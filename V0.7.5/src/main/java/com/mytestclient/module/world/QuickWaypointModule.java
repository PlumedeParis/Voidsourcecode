package com.mytestclient.module.world;

import com.mytestclient.MyTestClient;
import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Modules;
import com.mytestclient.util.Notifications;
import com.mytestclient.waypoint.Waypoint;
import com.mytestclient.waypoint.WaypointManager;

/** Action: adds a waypoint at your position. Bind it to a key for quick marking. */
public class QuickWaypointModule extends Module {
    public QuickWaypointModule() {
        super("Quick Waypoint", "Action: drop a waypoint at your current position. Bind it to a key.", Category.WORLD);
    }

    @Override
    public boolean isAction() {
        return true;
    }

    @Override
    protected void onAction() {
        if (mc.player == null) return;
        addHere(null);
    }

    /** Adds a waypoint at the player's position. Shared with the GUI's Waypoints page. */
    public static Waypoint addHere(String name) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        WaypointManager manager = MyTestClient.get().waypoints();
        if (name == null || name.isBlank()) name = "Waypoint " + (manager.forCurrentWorld().size() + 1);
        WaypointsModule settings = Modules.get(WaypointsModule.class);
        int color = settings != null ? settings.defaultColor.get() : WaypointManager.PALETTE[0];
        Waypoint wp = new Waypoint(name, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(),
                WaypointManager.currentDimension(), WaypointManager.currentWorldKey(), color);
        manager.add(wp);
        Notifications.info("Added waypoint '" + name + "'");
        if (settings != null && !settings.isEnabled()) settings.setEnabled(true);
        return wp;
    }
}
