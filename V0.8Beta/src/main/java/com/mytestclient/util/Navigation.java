package com.mytestclient.util;

import com.mytestclient.render.Overlay;
import com.mytestclient.waypoint.WaypointManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/**
 * The current destination set with {@code \goto}, used by {@code \walk} and {@code \elytra}.
 * Shown on screen as a marker with the distance.
 */
public final class Navigation {
    private static Vec3d target;
    private static String label = "";
    private static String dimension = "";
    /** A structure query waiting for a seed ({@code \goto stronghold} before {@code \setseed}). */
    private static String pendingStructure;

    private Navigation() {
    }

    public static void set(Vec3d pos, String name) {
        target = pos;
        label = name;
        dimension = WaypointManager.currentDimension();
        pendingStructure = null;
    }

    public static void clear() {
        target = null;
        pendingStructure = null;
    }

    public static Vec3d target() {
        // follow the player across dimensions: overworld <-> nether coordinates are converted
        if (target == null) return null;
        String now = WaypointManager.currentDimension();
        if (now.equals(dimension)) return target;
        if (dimension.equals("minecraft:overworld") && now.equals("minecraft:the_nether")) return new Vec3d(target.x / 8, 70, target.z / 8);
        if (dimension.equals("minecraft:the_nether") && now.equals("minecraft:overworld")) return new Vec3d(target.x * 8, 100, target.z * 8);
        return null;
    }

    public static String label() {
        return label;
    }

    public static boolean has() {
        return target() != null;
    }

    public static void setPendingStructure(String query) {
        pendingStructure = query;
    }

    public static String pendingStructure() {
        return pendingStructure;
    }

    public static void render(Overlay o) {
        Vec3d t = target();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (t == null || mc.player == null) return;
        double dist = Math.sqrt(mc.player.squaredDistanceTo(t.x, mc.player.getY(), t.z));
        String d = dist >= 1000 ? String.format("%.1fkm", dist / 1000) : String.format("%.0fm", dist);
        o.marker(t.x, t.y, t.z, 0xFF22D3EE, d, label + " (" + d + ")", 0.8f);
    }
}
