package com.mytestclient.module.hud;

import com.mytestclient.MyTestClient;
import com.mytestclient.gui.Theme;
import com.mytestclient.hud.HudModule;
import com.mytestclient.module.Category;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.render.Render2D;
import com.mytestclient.waypoint.Waypoint;
import com.mytestclient.waypoint.WaypointManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/** Compass HUD: a heading strip with cardinal directions and your waypoints as markers. */
public class CompassHud extends HudModule {
    private static final String[] LABELS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

    private final NumberSetting width = add(new NumberSetting("Width", "Strip width in pixels.", 180, 80, 400, 2));
    private final BooleanSetting waypoints = add(new BooleanSetting("Waypoints", "Show waypoints of this world on the strip.", true));

    public CompassHud() {
        super("Compass", "Heading strip with directions and waypoint markers.", Category.HUD, 50, 0, false);
    }

    @Override
    protected void render(DrawContext ctx, int x, int y, float tickDelta) {
        int w = width.getInt(), h = 18, cx = x + w / 2;
        float yaw = mc.player.getYaw(tickDelta);
        double pxPerDegree = w / 180.0; // 180° visible

        Render2D.roundedRect(ctx, x, y, w, h, 5, Theme.HUD_BG);
        ctx.enableScissor(x, y, x + w, y + h);
        // ticks every 15°, labels every 45°. Minecraft yaw: 0 = south, 90 = west, 180 = north, 270 = east
        for (int deg = 0; deg < 360; deg += 15) {
            float diff = MathHelper.wrapDegrees(deg - yaw);
            if (Math.abs(diff) > 95) continue;
            int px = cx + (int) Math.round(diff * pxPerDegree);
            if (deg % 45 == 0) {
                String label = LABELS[deg / 45];
                boolean main = label.length() == 1;
                int color = label.equals("N") ? Theme.BAD : (main ? Theme.TEXT : Theme.TEXT_DIM);
                ctx.drawText(mc.textRenderer, label, px - mc.textRenderer.getWidth(label) / 2, y + 5, color, true);
            } else {
                ctx.fill(px, y + h - 5, px + 1, y + h - 2, Theme.TEXT_MUTED);
            }
        }
        if (waypoints.get()) {
            String world = WaypointManager.currentWorldKey(), dim = WaypointManager.currentDimension();
            for (Waypoint wp : MyTestClient.get().waypoints().all()) {
                if (!wp.visible || !wp.world.equals(world) || !wp.dimension.equals(dim)) continue;
                double dx = wp.x + 0.5 - mc.player.getX(), dz = wp.z + 0.5 - mc.player.getZ();
                float wpYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
                float diff = MathHelper.wrapDegrees(wpYaw - yaw);
                if (Math.abs(diff) > 90) continue;
                int px = cx + (int) Math.round(diff * pxPerDegree);
                Render2D.roundedRect(ctx, px - 2, y + 1, 5, 4, 1, wp.color);
            }
        }
        ctx.disableScissor();
        // centre marker
        ctx.fill(cx, y, cx + 1, y + h, Theme.accent());
        setSize(w, h);
    }
}
