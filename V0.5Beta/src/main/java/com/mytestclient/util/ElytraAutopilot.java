package com.mytestclient.util;

import com.mytestclient.module.Modules;
import com.mytestclient.module.movement.ElytraFlyModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * {@code \elytra}: flies to the {@link Navigation} target with the elytra, using Elytra Fly's speed.
 * <p>
 * Every tick it takes off if needed, then picks a flight direction: it starts from the straight
 * line to the target and, if raycasts show a wall (or lava) ahead, tries directions turned left/
 * right and up/down until it finds a free one. Speed drops near obstacles, near the target and
 * when the chunks ahead are not loaded yet (the client can't see terrain there).
 * <ul>
 *     <li>Overworld/End: climbs to a cruise height above the terrain, then descends at the end.</li>
 *     <li>Nether: stays between the lava sea and the bedrock roof and weaves around the terrain.</li>
 * </ul>
 * It lands at the target, then stops. Moving the mouse does not cancel it; {@code \stop} does.
 */
public final class ElytraAutopilot {
    private enum Phase {TAKEOFF, CRUISE, LANDING}

    private static boolean active;
    private static Phase phase;
    private static int takeoffTicks, stuckTicks;
    private static double lastDistance;

    private static final double[] YAW_OFFSETS = {0, 20, -20, 40, -40, 65, -65, 90, -90, 135, -135};
    private static final double[] PITCH_OFFSETS = {0, -20, 20, -40, 40, -65, 65};

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
        String blocked = com.mytestclient.module.SafeMode.blockReason(fly);
        if (blocked != null) return "Elytra Fly is " + blocked;
        PathFollower.stop("replaced by elytra flight");
        active = true;
        phase = mc.player.isGliding() ? Phase.CRUISE : Phase.TAKEOFF;
        takeoffTicks = 0;
        stuckTicks = 0;
        lastDistance = Double.MAX_VALUE;
        return null;
    }

    public static void stop(String reason) {
        if (!active) return;
        active = false;
        ChatUtil.info("Elytra autopilot: " + reason);
    }

    /** Called at the end of every client tick. */
    public static void tick() {
        if (!active) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        Vec3d target = Navigation.target();
        if (p == null || target == null) {
            stop("no destination");
            return;
        }
        if (!p.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
            stop("no elytra equipped");
            return;
        }
        ElytraFlyModule fly = Modules.get(ElytraFlyModule.class);
        double maxSpeed = (fly != null ? fly.maxSpeed() : 40) / 20.0;
        boolean nether = mc.world.getRegistryKey() == World.NETHER;
        Vec3d pos = new Vec3d(p.getX(), p.getY(), p.getZ());
        double horizontal = Math.hypot(target.x - pos.x, target.z - pos.z);

        // ---- take off
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
        if (phase == Phase.TAKEOFF) phase = Phase.CRUISE;

        // ---- choose the height we want
        double desiredY;
        if (horizontal < 24) {
            phase = Phase.LANDING;
            desiredY = target.y;
        } else if (nether) {
            desiredY = MathHelper.clamp(Math.max(target.y, 70), 40, 115);
        } else {
            int ground = mc.world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, (int) pos.x, (int) pos.z);
            desiredY = Math.max(ground + 30, Math.min(target.y + 60, 200));
        }

        Vec3d goal = new Vec3d(target.x, desiredY, target.z);
        Vec3d wanted = goal.subtract(pos);
        if (wanted.lengthSquared() < 1e-6) wanted = new Vec3d(0, -1, 0);
        wanted = wanted.normalize();

        // ---- speed: slower near the target and when the terrain ahead isn't loaded
        double speed = maxSpeed;
        if (phase == Phase.LANDING) speed = Math.min(speed, Math.max(0.15, horizontal / 20.0));
        else speed = Math.min(speed, Math.max(0.4, horizontal / 15.0));
        double lookAhead = Math.max(6, speed * 10);
        Vec3d ahead = pos.add(wanted.multiply(lookAhead));
        if (!mc.world.getChunkManager().isChunkLoaded((int) Math.floor(ahead.x) >> 4, (int) Math.floor(ahead.z) >> 4)) {
            speed = Math.min(speed, 0.5); // wait for chunks before flying into them blind
        }

        // ---- obstacle avoidance
        Vec3d dir = pickDirection(mc, p, pos, wanted, lookAhead, nether);
        if (dir == null) {
            // boxed in: hover and slowly climb (or descend near the nether roof)
            p.setVelocity(0, nether && pos.y > 110 ? -0.2 : 0.2, 0);
            if (++stuckTicks > 200) stop("stuck, could not find a way");
            return;
        }
        if (dir != wanted) speed = Math.min(speed, Math.max(0.5, maxSpeed * 0.5));

        // final landing: come straight down
        if (phase == Phase.LANDING && horizontal < 2) dir = new Vec3d(0, -1, 0);

        p.setVelocity(dir.multiply(speed));
        p.setYaw((float) Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90f);
        p.setPitch((float) MathHelper.clamp(-Math.toDegrees(Math.asin(MathHelper.clamp(dir.y, -1, 1))), -90, 90));
        p.fallDistance = 0;

        // progress check
        if (horizontal < lastDistance - 0.05) {
            lastDistance = horizontal;
            stuckTicks = 0;
        } else if (++stuckTicks > 400) {
            stop("no progress for 20 seconds, stopped");
        }
    }

    /** First free direction around {@code wanted}, or null. */
    private static Vec3d pickDirection(MinecraftClient mc, ClientPlayerEntity p, Vec3d pos, Vec3d wanted, double distance, boolean nether) {
        double baseYaw = Math.atan2(wanted.z, wanted.x);
        double basePitch = Math.asin(MathHelper.clamp(wanted.y, -1, 1));
        for (double dp : PITCH_OFFSETS) {
            for (double dy : YAW_OFFSETS) {
                if (dp == 0 && dy == 0 && isClear(mc, p, pos, wanted, distance, nether)) return wanted;
                double yaw = baseYaw + Math.toRadians(dy);
                double pitch = MathHelper.clamp(basePitch + Math.toRadians(-dp), -1.4, 1.4);
                Vec3d dir = new Vec3d(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
                if (isClear(mc, p, pos, dir, distance, nether)) return dir;
            }
        }
        return null;
    }

    /** Raycasts from the feet and the head of the player; lava counts as an obstacle. */
    private static boolean isClear(MinecraftClient mc, ClientPlayerEntity p, Vec3d pos, Vec3d dir, double distance, boolean nether) {
        Vec3d end = pos.add(dir.multiply(distance));
        if (nether && (end.y < 36 || end.y > 120)) return false; // lava sea / bedrock roof
        double[] heights = {0.2, 1.6};
        for (double h : heights) {
            Vec3d from = pos.add(0, h, 0);
            Vec3d to = end.add(0, h, 0);
            HitResult hit = mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, p));
            if (hit.getType() != HitResult.Type.MISS) return false;
        }
        return true;
    }
}
