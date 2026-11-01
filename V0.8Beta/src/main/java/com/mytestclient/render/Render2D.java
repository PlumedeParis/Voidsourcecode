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

    /** Horizontal gradient (left colour to right colour), drawn as thin vertical slices. */
    public static void hGradient(DrawContext ctx, int x, int y, int w, int h, int left, int right) {
        if (w <= 0 || h <= 0) return;
        int step = Math.max(1, w / 48);
        for (int i = 0; i < w; i += step) {
            int sw = Math.min(step, w - i);
            ctx.fill(x + i, y, x + i + sw, y + h, com.mytestclient.render.Colors.lerp(left, right, (i + sw / 2f) / w));
        }
    }

    /** Vertical gradient (top colour to bottom colour). */
    public static void vGradient(DrawContext ctx, int x, int y, int w, int h, int top, int bottom) {
        if (w > 0 && h > 0) ctx.fillGradient(x, y, x + w, y + h, top, bottom);
    }

    /** Soft drop shadow around a rounded rectangle. */
    public static void shadow(DrawContext ctx, int x, int y, int w, int h, int radius, int size, int alpha) {
        for (int i = size; i >= 1; i--) {
            int a = (int) (alpha * Math.pow(1 - i / (double) (size + 1), 2) * 0.55);
            if (a <= 0) continue;
            roundedRect(ctx, x - i, y - i + 2, w + i * 2, h + i * 2, radius + i, (a << 24));
        }
    }

    /** Coloured glow (like a shadow, but in a colour) around a rounded rectangle. */
    public static void glow(DrawContext ctx, int x, int y, int w, int h, int radius, int size, int color, float strength) {
        for (int i = size; i >= 1; i--) {
            int a = (int) (((color >>> 24) & 0xFF) * strength * Math.pow(1 - i / (double) (size + 1), 2) * 0.5);
            if (a <= 0) continue;
            roundedRect(ctx, x - i, y - i, w + i * 2, h + i * 2, radius + i, com.mytestclient.render.Colors.withAlpha(color, a));
        }
    }

    /** Rounded rectangle with a 1px border, drawn as an outline only (inside left untouched). */
    public static void roundedOutline(DrawContext ctx, int x, int y, int w, int h, int radius, int color) {
        int r = Math.min(radius, Math.min(w, h) / 2);
        ctx.fill(x + r, y, x + w - r, y + 1, color);
        ctx.fill(x + r, y + h - 1, x + w - r, y + h, color);
        ctx.fill(x, y + r, x + 1, y + h - r, color);
        ctx.fill(x + w - 1, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            int next = i + 1 < r ? (int) Math.round(r - Math.sqrt(r * r - (r - i - 1.5) * (r - i - 1.5))) : 0;
            int len = Math.max(1, inset - next + 1);
            ctx.fill(x + inset - len + 1, y + i, x + inset + 1, y + i + 1, color);
            ctx.fill(x + w - inset - 1, y + i, x + w - inset + len - 1, y + i + 1, color);
            ctx.fill(x + inset - len + 1, y + h - i - 1, x + inset + 1, y + h - i, color);
            ctx.fill(x + w - inset - 1, y + h - i - 1, x + w - inset + len - 1, y + h - i, color);
        }
    }

    /** Text with a left-to-right colour gradient (one colour per character). */
    public static void gradientText(DrawContext ctx, net.minecraft.client.font.TextRenderer tr, String text, int x, int y, int from, int to) {
        int total = Math.max(1, tr.getWidth(text));
        int cx = x;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            int w = tr.getWidth(ch);
            ctx.drawText(tr, ch, cx, y, com.mytestclient.render.Colors.lerp(from, to, (cx - x + w / 2f) / total), false);
            cx += w;
            i += Character.charCount(cp);
        }
    }

    public static void outline(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }
}
