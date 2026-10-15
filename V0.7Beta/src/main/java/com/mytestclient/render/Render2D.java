package com.mytestclient.render;

import net.minecraft.client.gui.DrawContext;

/** 2D drawing primitives built on {@link DrawContext#fill}: rounded rectangles, circles, outlines. */
public final class Render2D {
    private Render2D() {
    }

    public static void rect(DrawContext ctx, int x, int y, int w, int h, int color) {
        if (w > 0 && h > 0) ctx.fill(x, y, x + w, y + h, color);
    }

    /**
     * Filled rectangle with rounded corners. Each corner row is a single fill with an inset
     * computed from the circle equation, so translucent colours never overlap.
     */
    public static void roundedRect(DrawContext ctx, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(radius, Math.min(w, h) / 2);
        if (r <= 0) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }
        ctx.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            ctx.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
    }

    /** Rounded rectangle with a 1px border of another colour. */
    public static void roundedRectBordered(DrawContext ctx, int x, int y, int w, int h, int radius, int fill, int border) {
        roundedRect(ctx, x, y, w, h, radius, border);
        roundedRect(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), fill);
    }

    public static void circle(DrawContext ctx, int cx, int cy, int radius, int color) {
        roundedRect(ctx, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
    }

    public static void outline(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }
}
