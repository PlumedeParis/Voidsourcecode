package com.mytestclient.util;

import com.mytestclient.module.Modules;
import com.mytestclient.module.movement.ElytraFlyModule;
import com.mytestclient.module.world.SeedInfoModule;
import com.mytestclient.render.Overlay;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@code \elytra}: flies to the {@link Navigation} target with the elytra, using Elytra Fly's speed.
 * <p>
 * Before flying, a route is computed by {@link RoutePlanner} (from loaded chunks and, if known, the
 * seed), so the autopilot knows which way it goes around mountains, through which Nether tunnels,
 * etc. It is drawn in the world as a cyan line. While flying:
 * <ol>
 *     <li>It follows the route, aiming at a point a little ahead on it.</li>
 *     <li>Overworld/End: the height comes from the terrain ahead along the route (and the
 *     planned height); horizontal speed drops when the climb is too steep.
 *     Nether: the height is the planned gap height, snapped to the real gap once loaded.</li>
 *     <li>Raycasts are a safety net: when they hit, an avoidance direction is chosen and
 *     <b>kept</b> for a moment (no more back-and-forth).</li>
 *     <li>It re-plans when new chunks reveal unknown terrain, when it leaves the route, or when it
 *     gets stuck (that spot is then avoided).</li>
 * </ol>
 * It lands at the target, then stops. {@code \stop} cancels it.
 */
public final class ElytraAutopilot {
    private enum Phase {TAKEOFF, CRUISE, LANDING}

    private static final int NETHER_FLOOR = 34, NETHER_ROOF = 121;
    /** Blocks to keep above the terrain while cruising / minimum while climbing. */
    private static final double CRUISE_CLEARANCE = 14, MIN_CLEARANCE = 4;
    private static final double SAMPLE_STEP = 3;

    private static boolean active;
    private static Phase phase;
    private static int takeoffTicks, stuckTicks, climbOutTicks, replanTimer, avoidTicks;
    private static double bestProgress, bestY;
    private static Vec3d avoidDir;
    private static boolean progressOnRoute;

    private static RoutePlanner.Route route;
    private static CompletableFuture<RoutePlanner.Route> planning;
    private static boolean announced;
    private static double routeS;
    private static final List<BlockPos> blocked = new ArrayList<>();

    private static final double[] YAW_OFFSETS = {0, 25, -25, 50, -50, 80, -80, 120, -120};
    private static final double[] PITCH_OFFSETS = {0, -25, 25, -50, 50, -75, 75};

    private ElytraAutopilot() {
    }

    public static boolean isActive() {
        return active;
    }

    /** @return null if started, otherwise the reason it can't start */
    public static String start() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return "join a world first";
        if (!Navigation.has()) return "no destination: use \\goto first";
        if (!mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) return "wear an elytra first";
        ElytraFlyModule fly = Modules.get(ElytraFlyModule.class);
        String blockReason = com.mytestclient.module.SafeMode.blockReason(fly);
        if (blockReason != null) return "Elytra Fly is " + blockReason;
        PathFollower.stop("replaced by elytra flight");
        active = true;
        phase = mc.player.isGliding() ? Phase.CRUISE : Phase.TAKEOFF;
        takeoffTicks = 0;
        stuckTicks = 0;
        climbOutTicks = 0;
        avoidTicks = 0;
        bestProgress = Double.NaN;
        bestY = mc.player.getY();
        route = null;
        planning = null;
        announced = false;
        blocked.clear();
        requestPlan(mc);
        return null;
    }

    public static void stop(String reason) {
        if (!active) return;
        active = false;
        route = null;
        planning = null;
        ChatUtil.info("Elytra autopilot: " + reason);
    }

    private static void requestPlan(MinecraftClient mc) {
        if (planning != null && !planning.isDone()) return;
        Vec3d target = Navigation.target();
        if (target == null || mc.player == null) return;
        SeedInfoModule seedInfo = Modules.get(SeedInfoModule.class);
        Long seed = seedInfo != null ? seedInfo.getSeed() : null;
        try {
            planning = RoutePlanner.plan(new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()), target, seed, List.copyOf(blocked));
        } catch (Throwable e) {
            planning = null;
            com.mytestclient.MyTestClient.LOGGER.warn("Route planning failed", e);
        }
        replanTimer = 200;
    }

    /** Takes a finished plan, if any. */
    private static void collectPlan(ClientPlayerEntity p) {
        if (planning == null || !planning.isDone()) return;
        try {
            RoutePlanner.Route r = planning.getNow(null);
            if (r != null) {
                route = r;
                routeS = r.project(new Vec3d(p.getX(), p.getY(), p.getZ()), 0);
                bestProgress = routeS;
                if (!announced) {
                    announced = true;
                    ChatUtil.info(String.format("Route planned: %.1f km, %d turns, %s%s (%d ms).",
                            r.length() / 1000, r.points.size() - 2,
                            r.usedSeed ? "terrain from the seed + loaded chunks" : "loaded chunks only (no seed: less precise, re-planned as you fly)",
                            r.partial ? ", no complete path found yet" : "", r.millis));
                }
            }
        } catch (Throwable e) {
            com.mytestclient.MyTestClient.LOGGER.warn("Route planning failed", e);
        }
        planning = null;
    }

    /** Called at the end of every client tick. */
    public static void tick() {
        if (!active) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        ClientWorld world = mc.world;
        Vec3d target = Navigation.target();
        if (p == null || world == null || target == null) {
            stop("no destination");
            return;
        }
        if (!p.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
            stop("no elytra equipped");
            return;
        }
        collectPlan(p);
        ElytraFlyModule fly = Modules.get(ElytraFlyModule.class);
        double maxH = (fly != null ? fly.maxSpeed() : 40) / 20.0;
        // climbing has to keep up with fast horizontal flight, so it is never much slower than it
        double maxV = Math.max(1.0, Math.max(fly != null ? fly.verticalSpeed() / 20.0 : 1.5, maxH * 0.6));
        boolean nether = world.getRegistryKey() == World.NETHER;
        Vec3d pos = new Vec3d(p.getX(), p.getY(), p.getZ());
        double horizontal = Math.hypot(target.x - pos.x, target.z - pos.z);

        // ---- take off (also after bumping into the ground mid-flight)
        if (!p.isGliding()) {
            if (phase == Phase.LANDING && p.isOnGround()) {
                stop("arrived at " + Navigation.label() + "!");
                return;
            }
            if (p.isOnGround()) {
                p.jump();
            } else if (p.getVelocity().y < 0 && p.checkGliding()) {
                p.networkHandler.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
            if (++takeoffTicks > 100) stop("could not take off (need open space above you)");
            return;
        }
        takeoffTicks = 0;
        if (phase == Phase.TAKEOFF) phase = Phase.CRUISE;
        if (horizontal < 24) phase = Phase.LANDING;

        // ---- where to go: a point ahead on the route, or straight at the target
        boolean onRoute = route != null && phase != Phase.LANDING;
        if (onRoute) {
            routeS = route.project(pos, routeS);
            Vec3d on = route.at(routeS);
            double off = Math.hypot(on.x - pos.x, on.z - pos.z);
            if (off > Math.max(40, route.cell * 3)) requestPlan(mc); // blown off course
            if (--replanTimer <= 0 && (route.unknownCells > 0 || route.partial)) requestPlan(mc); // new chunks, more detail
        }
        double lookAhead = nether ? MathHelper.clamp(maxH * 4, 6, 24) : MathHelper.clamp(maxH * 6, 8, 48);
        Vec3d aim = onRoute ? route.at(routeS + lookAhead) : target;
        double ax = aim.x - pos.x, az = aim.z - pos.z, aimDist = Math.hypot(ax, az);
        double hx = aimDist > 1e-4 ? ax / aimDist : 0, hz = aimDist > 1e-4 ? az / aimDist : 0;

        // ---- terrain ahead (along the route when we have one)
        double speedH = phase == Phase.LANDING ? Math.min(maxH, Math.max(0.15, horizontal / 20.0)) : maxH;
        double range = Math.min(onRoute ? route.length() - routeS : horizontal, MathHelper.clamp(maxH * 40, 48, 160));
        double desiredY;
        double neededRatio = 0; // climb (blocks up per block forward) needed to clear what's ahead
        double unloadedAt = Double.MAX_VALUE;

        if (nether) {
            double planned = onRoute ? aim.y : MathHelper.clamp(Math.max(target.y, 40), NETHER_FLOOR + 2, NETHER_ROOF - 4);
            if (phase == Phase.LANDING) planned = MathHelper.clamp(target.y, NETHER_FLOOR + 2, NETHER_ROOF - 4);
            // snap to the real gap at the aim point once its chunk is loaded
            if (loaded(world, aim.x, aim.z)) {
                Double free = nearestGap(world, MathHelper.floor(aim.x), MathHelper.floor(aim.z), planned, planned);
                if (free != null) planned = free;
            }
            desiredY = planned;
            for (double d = SAMPLE_STEP; d <= range; d += SAMPLE_STEP) {
                Vec3d s = onRoute ? route.at(routeS + d) : pos.add(hx * d, 0, hz * d);
                if (!loaded(world, s.x, s.z)) {
                    unloadedAt = d;
                    break;
                }
                double y = onRoute ? s.y : desiredY;
                neededRatio = Math.max(neededRatio, Math.abs(y - pos.y) / Math.max(1, d - 1));
            }
        } else {
            double maxGround = ground(world, pos.x, pos.z);
            double plannedMax = Double.NEGATIVE_INFINITY;
            for (double d = SAMPLE_STEP; d <= range; d += SAMPLE_STEP) {
                Vec3d s = onRoute ? route.at(routeS + d) : pos.add(hx * d, 0, hz * d);
                if (onRoute && d <= 64) plannedMax = Math.max(plannedMax, s.y);
                if (!loaded(world, s.x, s.z)) {
                    unloadedAt = d;
                    break;
                }
                // the flight line and both sides of it, so we don't clip a corner
                double h = Math.max(ground(world, s.x, s.z),
                        Math.max(ground(world, s.x - hz * 1.5, s.z + hx * 1.5), ground(world, s.x + hz * 1.5, s.z - hx * 1.5)));
                maxGround = Math.max(maxGround, h);
                double dy = h + MIN_CLEARANCE - pos.y;
                if (dy > 0) neededRatio = Math.max(neededRatio, dy / Math.max(1, d - 1.5));
            }
            if (phase == Phase.LANDING) {
                // descend towards the target but never below the terrain between us and it
                double landY = Math.max(target.y, ground(world, target.x, target.z));
                desiredY = horizontal < 6 ? landY : Math.max(landY, maxGround + MIN_CLEARANCE);
            } else {
                desiredY = Math.max(maxGround + CRUISE_CLEARANCE, plannedMax);
                desiredY = Math.min(desiredY, world.getTopYInclusive() + 30);
            }
        }

        // ---- horizontal speed: slow enough to make the climb, and to see the terrain
        if (neededRatio > 0) speedH = Math.min(speedH, maxV / neededRatio);
        if (unloadedAt != Double.MAX_VALUE) speedH = Math.min(speedH, Math.max(0.3, (unloadedAt - 8) / 12.0));
        if (onRoute) speedH = Math.min(speedH, Math.max(0.4, aimDist / 4)); // sharp turns: don't overshoot

        // ---- vertical speed: towards desiredY, at least as fast as the climb needs
        double vy = MathHelper.clamp((desiredY - pos.y) * 0.3, -maxV, maxV);
        if (neededRatio > 0 && !nether) vy = Math.max(vy, Math.min(maxV, speedH * neededRatio * 1.1));
        if (phase == Phase.LANDING && !nether) {
            double below = pos.y - ground(world, pos.x, pos.z);
            vy = Math.max(vy, -Math.max(0.25, below / 6.0)); // soft touchdown
        }
        if (horizontal < 1.5) speedH = 0;

        // just hit something: climb out before going on
        if (p.horizontalCollision) climbOutTicks = 8;
        if (climbOutTicks > 0) {
            climbOutTicks--;
            speedH = Math.min(speedH, 0.1);
            vy = nether && pos.y > NETHER_ROOF - 6 ? -maxV * 0.5 : maxV;
        }

        Vec3d move = new Vec3d(hx * speedH, vy, hz * speedH);

        // ---- safety net: raycast where we are actually going; keep an avoidance once chosen
        double len = move.length();
        boolean touchingDown = phase == Phase.LANDING && horizontal < 6 && move.y < 0;
        if (len > 1e-3 && !touchingDown) {
            double look = Math.max(4, len * 8);
            Vec3d dir = move.multiply(1 / len);
            double avoidSpeed = Math.min(len, Math.max(0.5, maxH * 0.5));
            if (avoidTicks > 0 && avoidDir != null && isClear(world, p, pos, avoidDir, look, nether)) {
                avoidTicks--;
                move = avoidDir.multiply(avoidSpeed);
            } else if (!isClear(world, p, pos, dir, look, nether)) {
                Vec3d alt = pickDirection(world, p, pos, dir, look, nether, avoidDir);
                if (alt != null) {
                    avoidDir = alt;
                    avoidTicks = 10;
                    move = alt.multiply(avoidSpeed);
                } else {
                    avoidTicks = 0;
                    // boxed in: straight up (or down under the nether roof), whichever is free
                    Vec3d up = new Vec3d(0, 1, 0), down = new Vec3d(0, -1, 0);
                    boolean preferDown = nether && pos.y > NETHER_ROOF - 10;
                    if (!preferDown && isClear(world, p, pos, up, 3, nether)) move = up.multiply(maxV);
                    else if (isClear(world, p, pos, down, 3, nether)) move = down.multiply(maxV * 0.5);
                    else move = Vec3d.ZERO;
                }
            } else {
                avoidTicks = 0;
                avoidDir = null;
            }
        }

        p.setVelocity(move);
        double mh = Math.hypot(move.x, move.z);
        if (mh > 1e-3) p.setYaw((float) Math.toDegrees(Math.atan2(move.z, move.x)) - 90f);
        p.setPitch((float) MathHelper.clamp(-Math.toDegrees(Math.atan2(move.y, Math.max(mh, 1e-3))), -90, 90));
        p.fallDistance = 0;

        // ---- progress: moving along the route (or closer), or climbing to the height we need
        double progressNow = onRoute ? routeS : -horizontal;
        if (onRoute != progressOnRoute) bestProgress = Double.NaN; // switched measure (route <-> landing)
        progressOnRoute = onRoute;
        boolean progress = false;
        if (Double.isNaN(bestProgress) || progressNow > bestProgress + 0.05) {
            bestProgress = progressNow;
            progress = true;
        }
        if (Math.abs(desiredY - pos.y) < Math.abs(desiredY - bestY) - 0.05) {
            bestY = pos.y;
            progress = true;
        }
        if (Math.abs(desiredY - bestY) < 1) bestY = pos.y; // height reached: track the new desired height
        if (progress) {
            stuckTicks = 0;
        } else {
            stuckTicks++;
            if (stuckTicks % 60 == 0 && phase != Phase.LANDING) {
                // stuck here: mark the spot ahead as blocked and plan around it
                blocked.add(BlockPos.ofFloored(pos.x + hx * 6, pos.y, pos.z + hz * 6));
                if (blocked.size() > 64) blocked.remove(0);
                bestProgress = Double.NaN;
                requestPlan(mc);
            }
            if (stuckTicks > 600) stop("no progress for 30 seconds, stopped");
        }
    }

    /** Draws the route ahead as a line in the world. */
    public static void render(Overlay o) {
        if (!active || route == null) return;
        double s = routeS;
        Vec3d prev = route.at(s);
        for (int i = 0; i < 80 && s < route.length(); i++) {
            s += Math.max(4, route.cell / 2.0);
            Vec3d next = route.at(s);
            o.line(prev.x, prev.y + 1, prev.z, next.x, next.y + 1, next.z, 0xCC22D3EE);
            prev = next;
        }
    }

    private static boolean loaded(ClientWorld world, double x, double z) {
        return world.getChunkManager().isChunkLoaded(MathHelper.floor(x) >> 4, MathHelper.floor(z) >> 4);
    }

    /** First free Y above the terrain (leaves and water count as terrain). */
    private static double ground(ClientWorld world, double x, double z) {
        return world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));
    }

    private static boolean passable(ClientWorld world, BlockPos.Mutable m) {
        BlockState s = world.getBlockState(m);
        return s.getCollisionShape(world, m).isEmpty() && s.getFluidState().isEmpty();
    }

    /**
     * Nether: the Y (feet) of the 3-block-high free gap in this column closest to {@code from},
     * slightly preferring gaps towards {@code goal}; null if the column is solid.
     */
    private static Double nearestGap(ClientWorld world, int x, int z, double from, double goal) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        Double best = null;
        double bestScore = Double.MAX_VALUE;
        int run = 0;
        for (int y = NETHER_FLOOR; y <= NETHER_ROOF; y++) {
            m.set(x, y, z);
            run = passable(world, m) ? run + 1 : 0;
            if (run >= 3) {
                double feet = y - 2 + 0.1;
                double score = Math.abs(feet - from) + Math.abs(feet - goal) * 0.25;
                if (score < bestScore) {
                    bestScore = score;
                    best = feet;
                }
            }
        }
        return best;
    }

    /** Free direction around {@code wanted}, preferring the side we avoided to last time; or null. */
    private static Vec3d pickDirection(ClientWorld world, ClientPlayerEntity p, Vec3d pos, Vec3d wanted, double distance, boolean nether, Vec3d previous) {
        double baseYaw = Math.atan2(wanted.z, wanted.x);
        double basePitch = Math.asin(MathHelper.clamp(wanted.y, -1, 1));
        // keep turning the same way as before instead of flipping left/right
        double side = 1;
        if (previous != null && wanted.x * previous.z - wanted.z * previous.x < 0) side = -1;
        for (double dp : PITCH_OFFSETS) {
            for (double dy : YAW_OFFSETS) {
                if (dp == 0 && dy == 0) continue;
                double yaw = baseYaw + Math.toRadians(dy * side);
                double pitch = MathHelper.clamp(basePitch + Math.toRadians(-dp), -1.45, 1.45);
                Vec3d dir = new Vec3d(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
                if (isClear(world, p, pos, dir, distance, nether)) return dir;
            }
        }
        return null;
    }

    /** Raycasts from the feet, the middle and the head of the player; lava counts as an obstacle. */
    private static boolean isClear(ClientWorld world, ClientPlayerEntity p, Vec3d pos, Vec3d dir, double distance, boolean nether) {
        Vec3d end = pos.add(dir.multiply(distance));
        if (nether && (end.y < NETHER_FLOOR || end.y > NETHER_ROOF)) return false; // lava sea / bedrock roof
        double[] heights = {0.2, 1.0, 1.7};
        for (double h : heights) {
            Vec3d from = pos.add(0, h, 0);
            Vec3d to = end.add(0, h, 0);
            HitResult hit = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, p));
            if (hit.getType() != HitResult.Type.MISS) return false;
        }
        return true;
    }
}
