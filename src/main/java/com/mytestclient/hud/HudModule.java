package com.mytestclient.hud;

import com.mytestclient.gui.Theme;
import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.render.Render2D;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * Base class of on-screen HUD elements. The position is stored as a percentage of the free
 * screen space so it survives resolution and GUI-scale changes; it can be edited with sliders or
 * by dragging in the HUD editor.
 */
public abstract class HudModule extends Module {
    public final NumberSetting posX;
    public final NumberSetting posY;
    public final BooleanSetting background;

    private int x, y;
    private int width = 40, height = 12;

    protected HudModule(String name, String description, Category category, double defaultX, double defaultY, boolean defaultBackground) {
        super(name, description, category);
        posX = add(new NumberSetting("X", "Horizontal position, % of the screen.", defaultX, 0, 100, 0.1));
        posY = add(new NumberSetting("Y", "Vertical position, % of the screen.", defaultY, 0, 100, 0.1));
        background = add(new BooleanSetting("Background", "Draw a dark panel behind the element.", defaultBackground));
    }

    protected HudModule(String name, String description, Category category, double defaultX, double defaultY) {
        this(name, description, category, defaultX, defaultY, true);
    }

    protected HudModule(String name, String description, double defaultX, double defaultY) {
        this(name, description, Category.HUD, defaultX, defaultY);
    }

    /** Called by {@link HudRenderer}. Computes the position and renders the element. */
    public final void renderHud(DrawContext ctx, float tickDelta) {
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        x = (int) Math.round(posX.get() / 100.0 * Math.max(0, sw - width));
        y = (int) Math.round(posY.get() / 100.0 * Math.max(0, sh - height));
        if (background.get()) Render2D.roundedRect(ctx, x, y, width, height, 4, Theme.HUD_BG);
        render(ctx, x, y, tickDelta);
    }

    /** Draw the element at (x, y) and call {@link #setSize} with its dimensions. */
    protected abstract void render(DrawContext ctx, int x, int y, float tickDelta);

    protected final void setSize(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
    }

    /** Used by the HUD editor when dragging. */
    public void setPixelPosition(int px, int py) {
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int freeW = Math.max(1, sw - width), freeH = Math.max(1, sh - height);
        posX.set(Math.max(0, Math.min(100, px * 100.0 / freeW)));
        posY.set(Math.max(0, Math.min(100, py * 100.0 / freeH)));
    }

    /** Elements on the right half align their text to the right. */
    protected boolean alignRight() {
        return posX.get() > 50;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    // ------------------------------------------------------------------ helpers

    /** "label value" line pair, the label drawn in the accent colour. */
    public record Line(String label, String value, int valueColor) {
        public Line(String label, String value) {
            this(label, value, Theme.TEXT);
        }
    }

    /** Draws a list of label/value lines, sizes the element and handles right alignment. */
    protected final void drawLines(DrawContext ctx, int x, int y, List<Line> lines) {
        TextRenderer tr = mc.textRenderer;
        int maxW = 0;
        for (Line line : lines) maxW = Math.max(maxW, lineWidth(tr, line));
        int pad = 4;
        int lineY = y + pad;
        for (Line line : lines) {
            int w = lineWidth(tr, line);
            int lx = alignRight() ? x + pad + maxW - w : x + pad;
            if (!line.label.isEmpty()) {
                ctx.drawText(tr, line.label, lx, lineY, Theme.accent(), true);
                lx += tr.getWidth(line.label) + 4;
            }
            ctx.drawText(tr, line.value, lx, lineY, line.valueColor, true);
            lineY += 10;
        }
        setSize(maxW + pad * 2, lines.size() * 10 + pad * 2 - 2);
    }

    private static int lineWidth(TextRenderer tr, Line line) {
        return (line.label.isEmpty() ? 0 : tr.getWidth(line.label) + 4) + tr.getWidth(line.value);
    }
}
