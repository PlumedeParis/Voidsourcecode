package com.mytestclient.module.visuals;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.ColorSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.render.Overlay;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;

/** Breadcrumbs: draws the path you walked so you can find your way back. Visual only. */
public class BreadcrumbsModule extends Module {
    private final NumberSetting spacing = add(new NumberSetting("Spacing", "Distance between recorded points (blocks).", 1, 0.25, 5, 0.25));
    private final NumberSetting maxPoints = add(new NumberSetting("Max Points", "Length of the trail.", 1000, 50, 5000, 50));
    private final ColorSetting color = add(new ColorSetting("Color", "Trail colour.", 0xFF22D3EE));

    private final Deque<Vec3d> points = new ArrayDeque<>();
    private String dimension = "";

    public BreadcrumbsModule() {
        super("Breadcrumbs", "Draws a trail of where you walked.", Category.VISUALS);
    }

    @Override
    public void onTick() {
        String dim = mc.world.getRegistryKey().getValue().toString();
        if (!dim.equals(dimension)) {
            points.clear();
            dimension = dim;
        }
        Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getY() + 0.1, mc.player.getZ());
        Vec3d last = points.peekLast();
        if (last == null || last.squaredDistanceTo(pos) >= spacing.get() * spacing.get()) {
            if (last != null && last.squaredDistanceTo(pos) > 100 * 100) points.clear(); // teleported
            points.addLast(pos);
            while (points.size() > maxPoints.getInt()) points.removeFirst();
        }
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        Vec3d prev = null;
        int c = color.get();
        for (Vec3d p : points) {
            if (prev != null) o.line(prev.x, prev.y, prev.z, p.x, p.y, p.z, c);
            prev = p;
        }
    }

    /** Trail from newest to oldest point (to walk back). */
    public java.util.List<Vec3d> pathBack() {
        java.util.List<Vec3d> list = new java.util.ArrayList<>(points);
        java.util.Collections.reverse(list);
        return list;
    }

    public void clear() {
        points.clear();
    }

    public int size() {
        return points.size();
    }

    @Override
    public void onWorldLeave() {
        points.clear();
    }
}
