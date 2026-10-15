package com.mytestclient.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Box;
import org.joml.Matrix3x2fStack;

/**
 * Drawing API for world-space overlays, passed to {@code Module#onRenderOverlay}.
 * <p>
 * 3D lines are clipped against the near plane, projected with {@link Projection}, clipped to the
 * screen and drawn as rotated rectangles in physical pixels (the GUI scale is undone) so lines stay
 * thin and crisp.
 */
public final class Overlay {
    private final DrawContext ctx;
    private final float tickDelta;
    private final int thickness;
    /** True while the "physical pixel" matrix is pushed; kept across lines to avoid push/pop per line. */
    private boolean fine;

    public Overlay(DrawContext ctx, float tickDelta) {
        this.ctx = ctx;
        this.tickDelta = tickDelta;
        this.thickness = Math.max(1, (int) Math.round(Projection.guiScale() * 0.5));
    }

    public DrawContext ctx() {
        return ctx;
    }

    public float tickDelta() {
        return tickDelta;
    }

    // ------------------------------------------------------------------ 3D

    public void line(double x1, double y1, double z1, double x2, double y2, double z2, int color) {
        double[] a = Projection.toCamera(x1, y1, z1);
        double[] b = Projection.toCamera(x2, y2, z2);
        if (a[2] < Projection.NEAR && b[2] < Projection.NEAR) return;
        if (a[2] < Projection.NEAR) a = clipNear(a, b);
        else if (b[2] < Projection.NEAR) b = clipNear(b, a);
        double[] pa = Projection.toScreen(a);
        double[] pb = Projection.toScreen(b);
        line2d(pa[0], pa[1], pb[0], pb[1], color);
    }

    private static double[] clipNear(double[] behind, double[] front) {
        double t = (Projection.NEAR - behind[2]) / (front[2] - behind[2]);
        return new double[]{
                behind[0] + (front[0] - behind[0]) * t,
                behind[1] + (front[1] - behind[1]) * t,
                Projection.NEAR
        };
    }

    private static final int[][] EDGES = {{0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
    private final double[][] corners = new double[8][];

    /** The 12 edges of a box. Corners are projected once; boxes fully behind you or off screen are skipped. */
    public void box(Box b, int color) {
        boolean anyFront = false;
        double minSX = Double.MAX_VALUE, maxSX = -Double.MAX_VALUE, minSY = Double.MAX_VALUE, maxSY = -Double.MAX_VALUE;
        boolean allFront = true;
        for (int i = 0; i < 8; i++) {
            double[] c = Projection.toCamera((i & 1) == 0 ? b.minX : b.maxX, (i & 2) == 0 ? b.minY : b.maxY, (i & 4) == 0 ? b.minZ : b.maxZ);
            corners[i] = c;
            if (c[2] >= Projection.NEAR) {
                anyFront = true;
                double[] s = Projection.toScreen(c);
                minSX = Math.min(minSX, s[0]);
                maxSX = Math.max(maxSX, s[0]);
                minSY = Math.min(minSY, s[1]);
                maxSY = Math.max(maxSY, s[1]);
            } else allFront = false;
        }
        if (!anyFront) return;
        // entirely off screen (only decidable when every corner is in front of the camera)
        if (allFront && (maxSX < 0 || minSX > Projection.width() || maxSY < 0 || minSY > Projection.height())) return;
        for (int[] e : EDGES) {
            double[] a = corners[e[0]], c = corners[e[1]];
            if (a[2] < Projection.NEAR && c[2] < Projection.NEAR) continue;
            if (a[2] < Projection.NEAR) a = clipNear(a, c);
            else if (c[2] < Projection.NEAR) c = clipNear(c, a);
            double[] pa = Projection.toScreen(a), pc = Projection.toScreen(c);
            line2d(pa[0], pa[1], pc[0], pc[1], color);
        }
    }

    /** Flat horizontal rectangle at height {@code y}. */
    public void square(double x1, double z1, double x2, double z2, double y, int color) {
        line(x1, y, z1, x2, y, z1, color);
        line(x2, y, z1, x2, y, z2, color);
        line(x2, y, z2, x1, y, z2, color);
        line(x1, y, z2, x1, y, z1, color);
    }

    /** Line from the crosshair to a world point; points behind you are drawn towards the screen edge. */
    public void tracer(double x, double y, double z, int color) {
        double[] cam = Projection.toCamera(x, y, z);
        double cx = Projection.width() / 2.0, cy = Projection.height() / 2.0;
        if (cam[2] > Projection.NEAR) {
            double[] p = Projection.toScreen(cam);
            line2d(cx, cy, p[0], p[1], color);
        } else {
            double len = Math.hypot(cam[0], cam[1]);
            if (len < 1e-6) return;
            line2d(cx, cy, cx + cam[0] / len * 10000, cy - cam[1] / len * 10000, color);
        }
    }

    /** Text label centred above a world point, with a rounded background. */
    public void label(double x, double y, double z, String text, int color) {
        double[] p = Projection.project(x, y, z);
        if (p == null) return;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int w = tr.getWidth(text);
        int lx = (int) Math.round(p[0] - w / 2.0);
        int ly = (int) Math.round(p[1]) - 10;
        flush();
        Render2D.roundedRect(ctx, lx - 3, ly - 2, w + 6, 12, 3, 0xA0101014);
        ctx.drawText(tr, text, lx, ly, color | 0xFF000000, false);
    }

    /**
     * Compact marker: a small diamond with tiny text under it. The full {@code detail} text is
     * only shown when the marker is near the crosshair, so many markers don't clutter the screen.
     */
    public void marker(double x, double y, double z, int color, String shortText, String detail, float textScale) {
        double[] p = Projection.project(x, y, z);
        if (p == null) return;
        flush();
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int px = (int) Math.round(p[0]), py = (int) Math.round(p[1]);
        // diamond
        for (int i = 0; i < 4; i++) {
            ctx.fill(px - i, py - 3 + i, px + i + 1, py - 2 + i, color | 0xFF000000);
            ctx.fill(px - i, py + 3 - i, px + i + 1, py + 4 - i, color | 0xFF000000);
        }
        double cx = Projection.width() / 2.0, cy = Projection.height() / 2.0;
        boolean focused = Math.abs(p[0] - cx) < 40 && Math.abs(p[1] - cy) < 40;
        String text = focused && detail != null ? detail : shortText;
        if (text == null || text.isEmpty()) return;
        float scale = focused ? 1f : textScale;
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(px, py + 6);
        m.scale(scale, scale);
        int w = tr.getWidth(text);
        Render2D.roundedRect(ctx, -w / 2 - 2, -1, w + 4, 10, 2, 0x90101014);
        ctx.drawText(tr, text, -w / 2, 0, focused ? 0xFFFFFFFF : (color | 0xFF000000), false);
        m.popMatrix();
    }

    // ------------------------------------------------------------------ 2D

    /** Line in GUI coordinates, rasterised at physical pixel resolution. */
    public void line2d(double x1, double y1, double x2, double y2, int color) {
        double s = Projection.guiScale();
        double maxX = Projection.width() * s, maxY = Projection.height() * s;
        double[] c = clip(x1 * s, y1 * s, x2 * s, y2 * s, maxX, maxY);
        if (c == null) return;
        if (!fine) {
            Matrix3x2fStack matrices = ctx.getMatrices();
            matrices.pushMatrix();
            matrices.scale((float) (1.0 / s), (float) (1.0 / s));
            fine = true;
        }
        raster(c[0], c[1], c[2], c[3], color);
    }

    /** Restores the normal GUI scale. Called before text and after each module's overlay. */
    public void flush() {
        if (fine) {
            ctx.getMatrices().popMatrix();
            fine = false;
        }
    }

    /** Liang-Barsky clipping to the rectangle [0,maxX]x[0,maxY]. */
    private static double[] clip(double x0, double y0, double x1, double y1, double maxX, double maxY) {
        double t0 = 0, t1 = 1, dx = x1 - x0, dy = y1 - y0;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x0, maxX - x0, y0, maxY - y0};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0) {
                if (q[i] < 0) return null;
            } else {
                double r = q[i] / p[i];
                if (p[i] < 0) {
                    if (r > t1) return null;
                    if (r > t0) t0 = r;
                } else {
                    if (r < t0) return null;
                    if (r < t1) t1 = r;
                }
            }
        }
        return new double[]{x0 + t0 * dx, y0 + t0 * dy, x0 + t1 * dx, y0 + t1 * dy};
    }

    /**
     * Draws a line as one rotated thin rectangle (a single fill, whatever its length or angle):
     * much cheaper than pixel runs and without the staircase look.
     */
    private void raster(double x1, double y1, double x2, double y2, int color) {
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5) {
            ctx.fill((int) x1, (int) y1, (int) x1 + thickness, (int) y1 + thickness, color);
            return;
        }
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate((float) x1, (float) y1);
        m.rotate((float) Math.atan2(dy, dx));
        m.translate(0, -thickness / 2f);
        ctx.fill(0, 0, (int) Math.ceil(len), thickness, color);
        m.popMatrix();
    }
}
