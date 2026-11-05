package com.mytestclient.module.world;

import com.mytestclient.MyTestClient;
import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.ColorSetting;
import com.mytestclient.module.setting.ModeSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.render.Overlay;
import com.mytestclient.util.Notifications;
import com.mytestclient.waypoint.Waypoint;
import com.mytestclient.waypoint.WaypointManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Waypoints: renders your saved waypoints (labels, distance, beams) and creates a waypoint
 * automatically when you die. Manage waypoints in the GUI's Waypoints page. No warning.
 */
public class WaypointsModule extends Module {
    private final ModeSetting style = add(new ModeSetting("Style",
            "Compact: small marker + distance, full name when you look at it. Full: always show the name.", "Compact", "Compact", "Full"));
    private final NumberSetting textScale = add(new NumberSetting("Text Scale", "Size of the small distance text (Compact).", 0.7, 0.4, 1, 0.05)
            .visibleWhen(() -> style.is("Compact")));
    private final BooleanSetting beams = add(new BooleanSetting("Show Beams", "Draw a thin vertical beam at each waypoint.", false));
    private final BooleanSetting distance = add(new BooleanSetting("Distance", "Show the distance.", true));
    private final NumberSetting maxDistance = add(new NumberSetting("Max Distance", "Hide waypoints further than this (0 = unlimited).", 0, 0, 10000, 50));
    private final BooleanSetting deathWaypoints = add(new BooleanSetting("Death Waypoints", "Create a waypoint where you die.", true));
    private final NumberSetting maxDeaths = add(new NumberSetting("Max Deaths", "How many death waypoints to keep per world.", 3, 1, 20, 1)
            .visibleWhen(deathWaypoints::get));
    public final ColorSetting defaultColor = add(new ColorSetting("Default Color", "Colour of new waypoints.", 0xFF6C8CFF));

    private boolean wasDead;

    public WaypointsModule() {
        super("Waypoints", "Show saved waypoints in the world and mark your death location.", Category.WORLD);
    }

    @Override
    protected void onEnable() {
        wasDead = false;
    }

    @Override
    public void onTick() {
        boolean dead = mc.player.isDead();
        if (dead && !wasDead && deathWaypoints.get()) addDeathWaypoint();
        wasDead = dead;
    }

    private void addDeathWaypoint() {
        WaypointManager manager = MyTestClient.get().waypoints();
        String world = WaypointManager.currentWorldKey();
        List<Waypoint> deaths = new ArrayList<>();
        for (Waypoint wp : manager.all()) if (wp.death && wp.world.equals(world)) deaths.add(wp);
        while (deaths.size() >= maxDeaths.getInt()) manager.remove(deaths.remove(0));

        Waypoint wp = new Waypoint("Death", mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(),
                WaypointManager.currentDimension(), world, 0xFFEF4444);
        wp.death = true;
        manager.add(wp);
        Notifications.warn("Death waypoint at " + wp.x + " " + wp.y + " " + wp.z);
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        String world = WaypointManager.currentWorldKey();
        String dim = WaypointManager.currentDimension();
        double max = maxDistance.get();
        for (Waypoint wp : MyTestClient.get().waypoints().all()) {
            if (!wp.visible || !wp.world.equals(world) || !wp.dimension.equals(dim)) continue;
            double cx = wp.x + 0.5, cy = wp.y + 1.0, cz = wp.z + 0.5;
            double dist = Math.sqrt(mc.player.squaredDistanceTo(cx, cy, cz));
            if (max > 0 && dist > max) continue;
            if (beams.get()) {
                double base = Math.max(mc.world.getBottomY(), mc.player.getY() - 128);
                o.line(cx, base, cz, cx, mc.player.getY() + 128, cz, (wp.color & 0xFFFFFF) | 0x60000000);
            }
            String dText = dist >= 1000 ? String.format("%.1fkm", dist / 1000) : String.format("%.0fm", dist);
            if (style.is("Compact")) {
                String detail = distance.get() ? wp.name + " (" + dText + ")" : wp.name;
                o.marker(cx, cy, cz, wp.color, distance.get() ? dText : "", detail, textScale.getFloat());
            } else {
                o.label(cx, cy, cz, distance.get() ? wp.name + " (" + dText + ")" : wp.name, wp.color);
            }
        }
    }
}
