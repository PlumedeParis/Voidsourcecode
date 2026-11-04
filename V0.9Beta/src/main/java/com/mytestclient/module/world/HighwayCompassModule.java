package com.mytestclient.module.world;

import com.mytestclient.gui.Theme;
import com.mytestclient.hud.HudModule;
import com.mytestclient.module.Category;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.render.Overlay;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Highway Compass: anarchy servers (2b2t and co.) are crossed by eight highways starting at 0 0:
 * the four axes (+X, -X, +Z, -Z) and the four diagonals, in the Overworld and the Nether.
 * <p>
 * This HUD shows the nearest one, how far and in which direction it is (relative to where you
 * look), and once you are on it, the distance marker (with the Overworld equivalent in the
 * Nether). It draws the highway's centre line around you, and Snap Heading locks your yaw onto
 * the highway while you travel along it. Turn your mouse and the lock lets go for two seconds.
 */
public class HighwayCompassModule extends HudModule {
    private final NumberSetting onWidth = add(new NumberSetting("On Highway", "Within this many blocks of the centre line you count as on the highway.", 3, 1, 16, 1));
    private final BooleanSetting diagonals = add(new BooleanSetting("Diagonals", "Include the four diagonal highways.", true));
    private final BooleanSetting line = add(new BooleanSetting("Show Line", "Draw the nearest highway's centre line around you.", true));
    private final BooleanSetting snap = add(new BooleanSetting("Snap Heading", "On a highway, lock your view straight along it when you look roughly that way.", false));

    /** One highway half: direction (unit vector) and display name. */
    private record Highway(String name, double dx, double dz) {
    }

    private static final double D = Math.sqrt(0.5);
    private static final Highway[] ALL = {
            new Highway("+X", 1, 0), new Highway("-X", -1, 0), new Highway("+Z", 0, 1), new Highway("-Z", 0, -1),
            new Highway("+X +Z", D, D), new Highway("-X -Z", -D, -D), new Highway("+X -Z", D, -D), new Highway("-X +Z", -D, D)
    };

    /** Nearest highway result. */
    private record Nearest(Highway hw, double distance, double along, double px, double pz) {
    }

    private float lastYaw;
    private int snapCooldown;

    public HighwayCompassModule() {
        super("Highway Compass", "Nearest anarchy highway, how far and which way, and your km marker on it.", Category.WORLD, 50, 0, true);
    }

    private boolean supportedDimension() {
        return mc.world.getRegistryKey() == World.OVERWORLD || mc.world.getRegistryKey() == World.NETHER;
    }

    /** Highways run along block rows (the row z = 0 is the +X highway), so distances are measured from block centres. */
    private Nearest nearest(double worldX, double worldZ) {
        double x = worldX - 0.5, z = worldZ - 0.5;
        Nearest best = null;
        for (Highway h : ALL) {
            if (!diagonals.get() && h.dx != 0 && h.dz != 0) continue;
            double along = x * h.dx + z * h.dz; // projection on the ray from 0 0
            if (along < 0) continue; // that half points away from you
            double px = h.dx * along, pz = h.dz * along;
            double d = Math.hypot(x - px, z - pz);
            if (best == null || d < best.distance) best = new Nearest(h, d, along, px + 0.5, pz + 0.5);
        }
        return best;
    }

    @Override
    public void onTick() {
        if (!snap.get() || !supportedDimension()) return;
        float yaw = mc.player.getYaw();
        if (Math.abs(wrap(yaw - lastYaw)) > 1.5f) snapCooldown = 40; // you moved the mouse: let go for a moment
        if (snapCooldown > 0) {
            snapCooldown--;
            lastYaw = yaw;
            return;
        }
        Nearest n = nearest(mc.player.getX(), mc.player.getZ());
        if (n == null || n.distance > onWidth.get()) {
            lastYaw = yaw;
            return;
        }
        float out = yawOf(n.hw.dx, n.hw.dz), back = yawOf(-n.hw.dx, -n.hw.dz);
        float target = Math.abs(wrap(yaw - out)) < Math.abs(wrap(yaw - back)) ? out : back;
        float diff = wrap(target - yaw);
        if (Math.abs(diff) < 15 && Math.abs(diff) > 0.01f) {
            mc.player.setYaw(yaw + diff);
        }
        lastYaw = mc.player.getYaw();
    }

    /** Minecraft yaw of a horizontal direction (0 = +Z, 90 = -X). */
    private static float yawOf(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static float wrap(float a) {
        a %= 360;
        if (a >= 180) a -= 360;
        if (a < -180) a += 360;
        return a;
    }

    private static String compass(double dx, double dz) {
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        int i = (int) Math.round(((yawOf(dx, dz) % 360) + 360) % 360 / 45.0) % 8;
        return names[i];
    }

    private String relative(double dx, double dz) {
        float diff = wrap(yawOf(dx, dz) - mc.player.getYaw());
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        return arrows[(int) Math.round(((diff % 360) + 360) % 360 / 45.0) % 8];
    }

    private static String km(double blocks) {
        return blocks >= 1000 ? String.format(Locale.ROOT, "%.1fk", blocks / 1000) : String.valueOf(Math.round(blocks));
    }

    @Override
    protected void render(DrawContext ctx, int x, int y, float tickDelta) {
        List<Line> lines = new ArrayList<>();
        if (!supportedDimension()) {
            lines.add(new Line("Highway", "none in this dimension", Theme.TEXT_MUTED));
            drawLines(ctx, x, y, lines);
            return;
        }
        boolean nether = mc.world.getRegistryKey() == World.NETHER;
        Nearest n = nearest(mc.player.getX(), mc.player.getZ());
        if (n == null) {
            drawLines(ctx, x, y, lines);
            return;
        }
        // marker: axis coordinate for axes, X (= Z) for diagonals, as players name them
        double marker = n.hw.dx != 0 && n.hw.dz != 0 ? Math.abs(n.px - 0.5) : n.along;
        String where = nether ? km(marker) + "  (OW " + km(marker * 8) + ")" : km(marker);
        if (n.distance <= onWidth.get()) {
            lines.add(new Line("On", n.hw.name + " highway", Theme.GOOD));
            lines.add(new Line("At", where));
            String off = n.distance < 0.5 ? "centred" : String.format(Locale.ROOT, "%.1f off centre", n.distance);
            lines.add(new Line("", off, Theme.TEXT_DIM));
        } else {
            double dx = n.px - mc.player.getX(), dz = n.pz - mc.player.getZ();
            lines.add(new Line("Highway", n.hw.name + (nether ? " (Nether)" : "")));
            lines.add(new Line("Go", relative(dx, dz) + " " + km(n.distance) + " " + compass(dx, dz)));
            lines.add(new Line("Joins at", where, Theme.TEXT_DIM));
        }
        drawLines(ctx, x, y, lines);
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        if (!line.get() || !supportedDimension()) return;
        Nearest n = nearest(mc.player.getX(), mc.player.getZ());
        if (n == null || n.distance > 96) return;
        double y = Math.floor(mc.player.getY()) + 0.05;
        double a0 = Math.max(0, n.along - 48), a1 = n.along + 48;
        int color = n.distance <= onWidth.get() ? Theme.GOOD : Theme.accent();
        o.line(n.hw.dx * a0 + 0.5, y, n.hw.dz * a0 + 0.5, n.hw.dx * a1 + 0.5, y, n.hw.dz * a1 + 0.5, color, 2);
    }
}
