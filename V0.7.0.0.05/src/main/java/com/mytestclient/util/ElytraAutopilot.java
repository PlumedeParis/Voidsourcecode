package com.mytestclient.util;

import com.mytestclient.gui.Theme;
import com.mytestclient.module.Modules;
import com.mytestclient.module.movement.ElytraFlyModule;
import com.mytestclient.module.world.SeedInfoModule;
import com.mytestclient.render.Colors;
import com.mytestclient.render.Overlay;
import com.mytestclient.render.Render2D;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
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
 * <ol>
 *     <li><b>Planning</b>: the route is computed first by {@link RoutePlanner} (loaded chunks + the
 *     seed if known); you only take off once it is ready (or after 15 s with a straight line).</li>
 *     <li><b>Following</b>: the player aims at a point a few blocks ahead on the route (in 3D in the
 *     Nether), slows down before sharp turns and steers back when pushed off. The elytra's own
 *     physics (drag, lift, turning towards where you look) is predicted and compensated, so the
 *     player really moves along the line.</li>
 *     <li><b>Camera</b>: eases towards the flight direction (or snaps, or stays free).</li>
 *     <li><b>Safety net</b>: short raycasts along the actual movement; when they hit, an avoidance
 *     direction is kept for a moment and the route is re-planned if it keeps happening.</li>
 * </ol>
 * Overworld/End also climb over terrain seen ahead. It lands at the target. {@code \stop} cancels.
 */
public final class ElytraAutopilot {
    private enum Phase {PLANNING, TAKEOFF, CRUISE, LANDING}

    private static final int NETHER_FLOOR = 34, NETHER_ROOF = 121;
    /** Blocks to keep above the terrain while cruising / minimum while climbing. */
    private static final double CRUISE_CLEARANCE = 14, MIN_CLEARANCE = 4;
    private static final double SAMPLE_STEP = 3;
    private static final int PLAN_TIMEOUT = 300;

    private static boolean active;
    private static Phase phase;
    private static int takeoffTicks, stuckTicks, climbOutTicks, replanTimer, avoidTicks, planningTicks, ticks;
    private static double bestProgress, bestY;
    private static Vec3d avoidDir;
    private static boolean progressOnRoute;
    private static boolean emergencyLanding;

    private static RoutePlanner.Route route;
    private static CompletableFuture<RoutePlanner.Route> planning;
    private static boolean announced;
    private static double routeS;
    private static final List<BlockPos> blocked = new ArrayList<>();
    /** Patrol (Player Finder): never land, the destination keeps changing. */
    private static boolean patrol;
    private static Vec3d plannedTarget;
    /** Smoothed speed in blocks/s for the HUD. */
    private static double shownSpeed;
    private static Vec3d lastPos;

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
        if (!mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA) && !equipElytra(mc, false)) return "wear an elytra first (none found in your inventory)";
        ElytraFlyModule fly = Modules.get(ElytraFlyModule.class);
        String blockReason = com.mytestclient.module.SafeMode.blockReason(fly);
        if (blockReason != null) return "Elytra Fly is " + blockReason;
        PathFollower.stop("replaced by elytra flight");
        active = true;
        phase = Phase.PLANNING;
        planningTicks = 0;
        takeoffTicks = 0;
        stuckTicks = 0;
        climbOutTicks = 0;
        avoidTicks = 0;
        emergencyLanding = false;
        bestProgress = Double.NaN;
        bestY = mc.player.getY();
        route = null;
        planning = null;
        announced = false;
        blocked.clear();
        lastPos = null;
        requestPlan(mc);
        return null;
    }

    /** Patrol mode: no landing at the destination (it is moved further by the patrol). */
    public static void setPatrol(boolean value) {
        patrol = value;
    }

    public static void stop(String reason) {
        patrol = false;
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
        Long seed = seedInfo != null ? seedInfo.getCurrentSeed() : null;
        try {
            planning = RoutePlanner.plan(new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()), target, seed, List.copyOf(blocked));
        } catch (Throwable e) {
            planning = null;
            com.mytestclient.MyTestClient.LOGGER.warn("Route planning failed", e);
        }
        replanTimer = 200;
        plannedTarget = target;
    }

    /** Takes a finished plan, if any. */
    private static void collectPlan(ClientPlayerEntity p, boolean nether) {
        if (planning == null || !planning.isDone()) return;
        try {
            RoutePlanner.Route r = planning.getNow(null);
            if (r != null) {
                route = r;
                Vec3d pos = new Vec3d(p.getX(), p.getY(), p.getZ());
                routeS = nether ? r.project3(pos, 0) : r.project(pos, 0);
                bestProgress = routeS;
                if (!announced) {
                    announced = true;
                    ChatUtil.info(String.format("Route ready: %.1f km, %d turns, %s%s (%d ms).",
                            r.length() / 1000, Math.max(0, r.points.size() - 2),
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
        ticks++;
        boolean nether = world.getRegistryKey() == World.NETHER;
        Vec3d pos = new Vec3d(p.getX(), p.getY(), p.getZ());
        if (lastPos != null) shownSpeed += (pos.distanceTo(lastPos) * 20 - shownSpeed) * 0.15;
        lastPos = pos;

        collectPlan(p, nether);
        if (plannedTarget != null && target.squaredDistanceTo(plannedTarget) > 4) {
            // destination moved (\goto again, or the patrol's next leg): plan the new route
            route = null;
            bestProgress = Double.NaN;
            if (!patrol) announced = false;
            requestPlan(mc);
            if (phase == Phase.LANDING) phase = Phase.CRUISE;
        }
        ElytraFlyModule fly = Modules.get(ElytraFlyModule.class);
        if (ticks % 20 == 0) checkDurability(mc, p, fly);
        double maxH = (fly != null ? fly.maxSpeed() : 40) / 20.0;
        // climbing has to keep up with fast horizontal flight, so it is never much slower than it
        double maxV = Math.max(1.0, Math.max(fly != null ? fly.verticalSpeed() / 20.0 : 1.5, maxH * 0.6));
        double horizontal = Math.hypot(target.x - pos.x, target.z - pos.z);

        // ---- 1. compute the route before leaving
        if (phase == Phase.PLANNING) {
            planningTicks++;
            boolean ready = route != null || planningTicks > PLAN_TIMEOUT;
            if (!ready) {
                if (p.isGliding()) {
                    p.setVelocity(0, 0.02, 0); // hold position while thinking
                    p.fallDistance = 0;
                }
                return;
            }
            if (route == null) ChatUtil.info("Elytra autopilot: route not ready after 15 s, flying straight and planning on the way.");
            phase = p.isGliding() ? Phase.CRUISE : Phase.TAKEOFF;
            takeoffTicks = 0;
        }

        // ---- 2. take off (also after bumping into the ground mid-flight)
        if (!p.isGliding()) {
            if ((phase == Phase.LANDING || emergencyLanding) && p.isOnGround()) {
                stop(emergencyLanding ? "landed: your elytra is almost broken" : "arrived at " + Navigation.label() + "!");
                return;
            }
            if (phase != Phase.LANDING) phase = Phase.TAKEOFF;
            takeoffTicks++;
            if (p.isOnGround()) {
                p.jump();
            } else if (takeoffTicks >= 2 && p.checkGliding()) {
                // open the elytra right after the jump: works under low Nether ceilings too
                p.networkHandler.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
            if (takeoffTicks > 100) stop("could not take off (need 2 blocks of open space above you)");
            return;
        }
        takeoffTicks = 0;
        if (phase == Phase.TAKEOFF) phase = Phase.CRUISE;
        if (horizontal < 24 && !patrol) phase = Phase.LANDING;

        // ---- 3. steering, like Baritone: fly straight at the furthest point of the route that the
        //         player's hitbox can reach without touching anything (loaded terrain only)
        boolean onRoute = route != null && phase != Phase.LANDING;
        if (onRoute) {
            routeS = nether ? route.project3(pos, routeS) : route.project(pos, routeS);
            Vec3d on = route.at(routeS);
            double off = nether ? on.distanceTo(pos) : Math.hypot(on.x - pos.x, on.z - pos.z);
            if (off > Math.max(40, route.cell * 3)) requestPlan(mc); // blown off course
            if (--replanTimer <= 0 && (route.unknownCells > 0 || route.partial)) requestPlan(mc); // new chunks, more detail
        }
        Vec3d aim = null;
        double clearDist = Double.MAX_VALUE;
        if (onRoute) {
            double maxLook = Math.min(route.length() - routeS, Math.max(32, maxH * 14));
            for (double d = maxLook; d >= 2; d -= Math.max(1.5, d * 0.22)) {
                Vec3d c = route.at(routeS + d);
                if (!loaded(world, c.x, c.z)) continue;
                if (!nether) c = new Vec3d(c.x, Math.max(c.y, ground(world, c.x, c.z) + MIN_CLEARANCE), c.z);
                if (hitboxClear(world, p, pos, c, nether)) {
                    aim = c;
                    clearDist = c.distanceTo(pos);
                    break;
                }
            }
            if (aim == null) {
                aim = route.at(routeS + 3); // nothing visible: creep along the line, the safety net steers
                clearDist = 2;
            }
        } else if (nether) {
            double y = MathHelper.clamp(phase == Phase.LANDING ? target.y : Math.max(target.y, 40), NETHER_FLOOR + 2, NETHER_ROOF - 4);
            if (loaded(world, target.x, target.z)) {
                Double free = nearestGap(world, MathHelper.floor(target.x), MathHelper.floor(target.z), y, y);
                if (free != null) y = free;
            }
            aim = new Vec3d(target.x, y, target.z);
        } else {
            double y;
            if (phase == Phase.LANDING) {
                double landY = Math.max(target.y, ground(world, target.x, target.z));
                double midGround = ground(world, (pos.x + target.x) / 2, (pos.z + target.z) / 2);
                y = horizontal < 6 ? landY : Math.max(landY, Math.max(midGround, ground(world, pos.x, pos.z)) + MIN_CLEARANCE);
            } else {
                // no route (planning timed out): cruise above the terrain ahead
                double ahead = ground(world, pos.x, pos.z);
                double dx0 = target.x - pos.x, dz0 = target.z - pos.z, l0 = Math.max(1e-4, Math.hypot(dx0, dz0));
                for (int d = 8; d <= 64; d += 8) ahead = Math.max(ahead, ground(world, pos.x + dx0 / l0 * d, pos.z + dz0 / l0 * d));
                y = Math.min(ahead + CRUISE_CLEARANCE, world.getTopYInclusive() + 30);
            }
            aim = new Vec3d(target.x, y, target.z);
        }
        double ax = aim.x - pos.x, az = aim.z - pos.z, aimDist = Math.hypot(ax, az);
        double hx = aimDist > 1e-4 ? ax / aimDist : 0, hz = aimDist > 1e-4 ? az / aimDist : 0;
        double desiredY = aim.y;

        // ---- 4. speed: full speed; only slower when the free straight line ahead is short, or landing
        double speed = maxH;
        if (onRoute && clearDist < maxH * 3) speed = Math.max(0.6, clearDist / 3);
        if (phase == Phase.LANDING) speed = Math.min(speed, Math.max(0.2, horizontal / 12.0));
        Vec3d to = aim.subtract(pos);
        if (phase == Phase.LANDING && horizontal < 2) to = new Vec3d(target.x - pos.x, nether ? -2 : Math.min(-0.5, desiredY - pos.y), target.z - pos.z);
        double toLen = to.length();
        Vec3d move = toLen < 1e-4 ? Vec3d.ZERO : to.multiply(Math.min(speed, toLen) / toLen);
        if (phase == Phase.LANDING && !nether) {
            double below = pos.y - ground(world, pos.x, pos.z);
            if (move.y < -Math.max(0.25, below / 6.0)) move = new Vec3d(move.x, -Math.max(0.25, below / 6.0), move.z); // soft touchdown
        }
        if (emergencyLanding) move = new Vec3d(0, -0.6, 0);

        // just hit something: climb out before going on
        if (p.horizontalCollision) climbOutTicks = 6;
        if (climbOutTicks > 0) {
            climbOutTicks--;
            move = new Vec3d(move.x * 0.1, nether && pos.y > NETHER_ROOF - 6 ? -maxV * 0.5 : maxV, move.z * 0.1);
        }

        // ---- 5. safety net: only what we reach in the next few ticks
        double len = move.length();
        boolean touchingDown = (phase == Phase.LANDING && horizontal < 6 && move.y < 0) || emergencyLanding;
        if (len > 1e-3 && !touchingDown) {
            double look = len * 3 + 2;
            Vec3d dir = move.multiply(1 / len);
            double avoidSpeed = Math.min(len, Math.max(0.5, maxH * 0.5));
            if (avoidTicks > 0 && avoidDir != null && isClear(world, p, pos, avoidDir, look, nether)) {
                avoidTicks--;
                move = avoidDir.multiply(avoidSpeed);
            } else if (!isClear(world, p, pos, dir, look, nether)) {
                Vec3d alt = pickDirection(world, p, pos, dir, look, nether, avoidDir);
                if (alt != null) {
                    avoidDir = alt;
                    avoidTicks = 8;
                    move = alt.multiply(avoidSpeed);
                } else {
                    avoidTicks = 0;
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

        // ---- 6. camera, then velocity corrected for the elytra physics of the next tick
        aimCamera(p, move, fly);
        p.setVelocity(compensate(move, p.getYaw(), p.getPitch()));
        p.fallDistance = 0;

        // ---- 7. progress: moving along the route (or closer), or climbing to the height we need
        double progressNow = onRoute ? routeS : -horizontal;
        if (onRoute != progressOnRoute) bestProgress = Double.NaN;
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
        if (Math.abs(desiredY - bestY) < 1) bestY = pos.y;
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

    // ------------------------------------------------------------------ camera + physics

    private static void aimCamera(ClientPlayerEntity p, Vec3d move, ElytraFlyModule fly) {
        String mode = fly != null ? fly.cameraMode() : "Smooth";
        if (mode.equals("Free Look")) return;
        double mh = Math.hypot(move.x, move.z);
        float targetPitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(move.y, Math.max(mh, 1e-3))), -80, 80);
        float targetYaw = mh > 0.05 ? (float) Math.toDegrees(Math.atan2(move.z, move.x)) - 90f : p.getYaw();
        if (mode.equals("Instant")) {
            p.setYaw(targetYaw);
            p.setPitch(targetPitch);
            return;
        }
        float k = (float) (1 - (fly != null ? fly.smoothness() : 0.75));
        float dYaw = MathHelper.wrapDegrees(targetYaw - p.getYaw());
        float dPitch = targetPitch - p.getPitch();
        p.setYaw(p.getYaw() + MathHelper.clamp(dYaw * k, -20, 20));
        p.setPitch(MathHelper.clamp(p.getPitch() + MathHelper.clamp(dPitch * k, -12, 12), -90, 90));
    }

    /**
     * What vanilla elytra physics does to a velocity during the next tick (gravity, lift, turning
     * towards the look direction, drag), for a given view.
     */
    private static Vec3d glideModel(Vec3d v, float yawDeg, float pitchDeg) {
        double f = Math.toRadians(pitchDeg), yaw = Math.toRadians(yawDeg);
        double lx = -Math.sin(yaw) * Math.cos(f), lz = Math.cos(yaw) * Math.cos(f);
        double h = Math.sqrt(lx * lx + lz * lz);
        double i = Math.hypot(v.x, v.z);
        double k = Math.cos(f) * Math.cos(f);
        double x = v.x, y = v.y + 0.08 * (-1 + k * 0.75), z = v.z;
        if (y < 0 && h > 0) {
            double m = y * -0.1 * k;
            x += lx * m / h;
            y += m;
            z += lz * m / h;
        }
        if (f < 0 && h > 0) {
            double m = i * -Math.sin(f) * 0.04;
            x -= lx * m / h;
            y += m * 3.2;
            z -= lz * m / h;
        }
        if (h > 0) {
            x += (lx / h * i - x) * 0.1;
            z += (lz / h * i - z) * 0.1;
        }
        return new Vec3d(x * 0.99, y * 0.98, z * 0.99);
    }

    /** Velocity to set now so that, after the elytra physics, the player moves by {@code desired}. */
    private static Vec3d compensate(Vec3d desired, float yaw, float pitch) {
        Vec3d v = desired;
        for (int it = 0; it < 4; it++) v = v.add(desired.subtract(glideModel(v, yaw, pitch)));
        return v;
    }

    // ------------------------------------------------------------------ elytra durability

    private static int durabilityLeft(ItemStack s) {
        return s.isDamageable() ? s.getMaxDamage() - s.getDamage() : Integer.MAX_VALUE;
    }

    private static void checkDurability(MinecraftClient mc, ClientPlayerEntity p, ElytraFlyModule fly) {
        if (fly == null || !fly.elytraSwap() || emergencyLanding) return;
        if (durabilityLeft(p.getEquippedStack(EquipmentSlot.CHEST)) > 12) return;
        if (equipElytra(mc, true)) {
            ChatUtil.info("Elytra autopilot: swapped to a fresh elytra.");
            return;
        }
        ChatUtil.error("Elytra autopilot: your elytra is about to break and you have no spare, landing now.");
        emergencyLanding = true;
    }

    /** Puts the best elytra from the inventory on the chest slot. */
    private static boolean equipElytra(MinecraftClient mc, boolean onlyBetter) {
        ClientPlayerEntity p = mc.player;
        if (mc.currentScreen != null || !p.playerScreenHandler.getCursorStack().isEmpty()) return false;
        int best = -1, bestLeft = onlyBetter ? 12 : 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(Items.ELYTRA) && durabilityLeft(s) > bestLeft) {
                best = i;
                bestLeft = durabilityLeft(s);
            }
        }
        if (best < 0) return false;
        int sync = p.playerScreenHandler.syncId, slot = best < 9 ? 36 + best : best;
        mc.interactionManager.clickSlot(sync, slot, 0, SlotActionType.PICKUP, p);
        mc.interactionManager.clickSlot(sync, 6, 0, SlotActionType.PICKUP, p); // chest armour slot
        mc.interactionManager.clickSlot(sync, slot, 0, SlotActionType.PICKUP, p);
        return true;
    }

    // ------------------------------------------------------------------ drawing

    /** Draws the route ahead and the flight panel. */
    public static void render(Overlay o) {
        if (!active) return;
        if (route != null) {
            // one clean opaque line through the route's corners (no overlapping translucent pieces)
            List<Vec3d> pts = route.ahead(routeS, 400);
            for (int i = 1; i < pts.size(); i++) {
                Vec3d a = pts.get(i - 1), b = pts.get(i);
                o.line(a.x, a.y + 0.3, a.z, b.x, b.y + 0.3, b.z, 0xFF22D3EE, 2);
            }
        }
        o.flush();
        drawPanel(o.ctx());
    }

    private static void drawPanel(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || Navigation.target() == null) return;
        TextRenderer tr = mc.textRenderer;
        Vec3d t = Navigation.target();
        double remaining = route != null && phase != Phase.LANDING ? Math.max(0, route.length() - routeS)
                : Math.hypot(t.x - mc.player.getX(), t.z - mc.player.getZ());
        String state = switch (phase) {
            case PLANNING -> "Computing route" + ".".repeat(1 + (ticks / 5) % 3);
            case TAKEOFF -> "Taking off";
            case CRUISE -> emergencyLanding ? "Emergency landing" : "Cruising";
            case LANDING -> "Landing";
        };
        String dist = remaining >= 1000 ? String.format("%.2f km", remaining / 1000) : String.format("%.0f m", remaining);
        String eta = shownSpeed > 1 && phase != Phase.PLANNING ? fmtTime(remaining / shownSpeed) : "--:--";
        String line1 = "✈ " + Navigation.label();
        String line2 = state + "  •  " + dist + "  •  " + String.format("%.0f b/s", shownSpeed) + "  •  ETA " + eta;
        int w = Math.max(tr.getWidth(line1), tr.getWidth(line2)) + 20;
        int x = (ctx.getScaledWindowWidth() - w) / 2, y = 6, h = 32;
        Render2D.roundedRect(ctx, x - 1, y - 1, w + 2, h + 2, 7, 0x40000000);
        Render2D.roundedRect(ctx, x, y, w, h, 6, 0xD0101018);
        ctx.drawText(tr, line1, x + 10, y + 5, 0xFFFFFFFF, false);
        ctx.drawText(tr, line2, x + 10, y + 16, Theme.TEXT_DIM, false);
        // progress bar
        double total = route != null ? route.length() : 0;
        if (total > 0 && phase != Phase.PLANNING) {
            int bw = w - 12;
            float frac = (float) MathHelper.clamp(routeS / total, 0, 1);
            Render2D.rect(ctx, x + 6, y + h - 3, bw, 1, 0x30FFFFFF);
            Render2D.rect(ctx, x + 6, y + h - 3, Math.round(bw * frac), 1, Theme.accent());
        }
    }

    private static String fmtTime(double seconds) {
        int s = (int) Math.round(seconds);
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }

    // ------------------------------------------------------------------ world helpers

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

    /** Nether: feet Y of the 3-high free gap in this column closest to {@code from}; null if solid. */
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

    private static final double[][] HITBOX = {{0, 0.3, 0}, {0.35, 0.0, 0.35}, {-0.35, 0.0, -0.35}, {0.35, 0.65, -0.35}, {-0.35, 0.65, 0.35}};

    /** Can the gliding player's hitbox move in a straight line from {@code from} to {@code to}? */
    private static boolean hitboxClear(ClientWorld world, ClientPlayerEntity p, Vec3d from, Vec3d to, boolean nether) {
        if (nether && (to.y < NETHER_FLOOR || to.y > NETHER_ROOF)) return false;
        for (double[] o : HITBOX) {
            Vec3d a = from.add(o[0], o[1], o[2]), b = to.add(o[0], o[1], o[2]);
            if (world.raycast(new RaycastContext(a, b, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, p)).getType() != HitResult.Type.MISS) {
                return false;
            }
        }
        return true;
    }

    /** Is the straight move along {@code dir} free for the gliding hitbox? Lava counts as an obstacle. */
    private static boolean isClear(ClientWorld world, ClientPlayerEntity p, Vec3d pos, Vec3d dir, double distance, boolean nether) {
        // same rays as the steering: the gliding hitbox is only 0.6 high
        return hitboxClear(world, p, pos, pos.add(dir.multiply(distance)), nether);
    }
}
