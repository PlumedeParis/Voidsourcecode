package com.mytestclient.util;

import com.mytestclient.event.KeybindManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Walks the player along a list of points by steering the camera and holding forward/sprint
 * (and jumping over obstacles). Used by {@code \breadcrumbs back} and {@code \goto}.
 * There is no path-finding: it follows the points in a straight line between them.
 * Pressing back/left/right or {@code \stop} cancels it.
 */
public final class PathFollower {
    private static List<Vec3d> path = List.of();
    private static int index;
    private static String label = "";
    private static int stuckTicks;
    private static double lastDistance;

    private PathFollower() {
    }

    public static void start(List<Vec3d> points, String name) {
        if (points.isEmpty()) return;
        path = new ArrayList<>(points);
        index = 0;
        label = name;
        stuckTicks = 0;
        lastDistance = Double.MAX_VALUE;
        ChatUtil.success("Following " + name + " (" + points.size() + " points). \\stop or press S/A/D to cancel.");
    }

    public static boolean isActive() {
        return !path.isEmpty();
    }

    public static void stop(String reason) {
        if (!isActive()) return;
        path = List.of();
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.options.forwardKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        KeyBinding.updatePressedStates();
        ChatUtil.info(label + ": " + reason);
    }

    /** Called at the start of every client tick. */
    public static void tick() {
        if (!isActive()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            path = List.of();
            return;
        }
        if (KeybindManager.isBindingDown(mc.options.backKey) || KeybindManager.isBindingDown(mc.options.leftKey)
                || KeybindManager.isBindingDown(mc.options.rightKey)) {
            stop("cancelled");
            return;
        }

        Vec3d target = path.get(index);
        double dx = target.x - mc.player.getX(), dz = target.z - mc.player.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 0.8) {
            index++;
            stuckTicks = 0;
            lastDistance = Double.MAX_VALUE;
            if (index >= path.size()) {
                stop("arrived!");
                return;
            }
            return;
        }

        // stuck detection: no progress for 5 seconds
        if (horizontal < lastDistance - 0.05) {
            lastDistance = horizontal;
            stuckTicks = 0;
        } else if (++stuckTicks > 100) {
            stop("stuck, stopped");
            return;
        }

        mc.player.setYaw((float) Math.toDegrees(Math.atan2(dz, dx)) - 90f);
        mc.options.forwardKey.setPressed(true);
        mc.options.sprintKey.setPressed(true);
        boolean needJump = mc.player.horizontalCollision || target.y > mc.player.getY() + 0.6 || mc.player.isTouchingWater();
        mc.options.jumpKey.setPressed(needJump && (mc.player.isOnGround() || mc.player.isTouchingWater()));
    }

    public static String label() {
        return label;
    }

    public static int remaining() {
        return isActive() ? path.size() - index : 0;
    }
}
