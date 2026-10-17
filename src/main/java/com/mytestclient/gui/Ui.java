package com.mytestclient.gui;

import com.mytestclient.gui.widget.TextBox;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immediate-mode GUI context. Each frame, widgets draw themselves and register click regions
 * here; mouse clicks are then dispatched to the top-most region under the cursor. This keeps
 * widgets tiny (a render method) and makes the GUI easy to extend: no layout trees to maintain.
 */
public final class Ui {
    @FunctionalInterface
    public interface ClickHandler {
        void onClick(int button, double mouseX, double mouseY);
    }

    @FunctionalInterface
    public interface DragHandler {
        void onDrag(double mouseX, double mouseY);
    }

    @FunctionalInterface
    public interface ScrollHandler {
        void onScroll(double amount);
    }

    private record Hit(int x1, int y1, int x2, int y2, ClickHandler handler) {
    }

    private record ScrollHit(int x1, int y1, int x2, int y2, ScrollHandler handler) {
    }

    public DrawContext ctx;
    public TextRenderer tr;
    public int mouseX, mouseY;

    /** Focused text box receives keyboard input. */
    public TextBox focused;
    /** Active drag (slider, window...) receives mouse movement until release. */
    public DragHandler drag;

    private final List<Hit> hits = new ArrayList<>();
    private final List<ScrollHit> scrollHits = new ArrayList<>();
    private final Map<Object, float[]> anims = new HashMap<>();
    private int screenW, screenH;
    private int clipX1, clipY1, clipX2, clipY2;
    private List<Text> tooltip;
    private long lastFrame = System.nanoTime();
    private float delta;

    public void beginFrame(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY, int screenW, int screenH) {
        this.ctx = ctx;
        this.tr = tr;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.screenW = screenW;
        this.screenH = screenH;
        hits.clear();
        scrollHits.clear();
        tooltip = null;
        resetClip();
        long now = System.nanoTime();
        delta = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
    }

    /** Draws the pending tooltip on top of everything. */
    public void endFrame() {
        if (tooltip == null || !Theme.tooltips()) return;
        List<OrderedText> lines = new ArrayList<>();
        for (Text line : tooltip) lines.addAll(tr.wrapLines(line, 220));
        ctx.drawOrderedTooltip(tr, lines, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ clipping

    public void clip(int x1, int y1, int x2, int y2) {
        ctx.enableScissor(x1, y1, x2, y2);
        clipX1 = x1;
        clipY1 = y1;
        clipX2 = x2;
        clipY2 = y2;
    }

    public void unclip() {
        ctx.disableScissor();
        resetClip();
    }

    private void resetClip() {
        clipX1 = 0;
        clipY1 = 0;
        clipX2 = screenW;
        clipY2 = screenH;
    }

    // ------------------------------------------------------------------ input

    public boolean hover(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h
                && mouseX >= clipX1 && mouseX < clipX2 && mouseY >= clipY1 && mouseY < clipY2;
    }

    /** Registers a clickable region (clipped to the current scissor area). */
    public void click(int x, int y, int w, int h, ClickHandler handler) {
        int x1 = Math.max(x, clipX1), y1 = Math.max(y, clipY1);
        int x2 = Math.min(x + w, clipX2), y2 = Math.min(y + h, clipY2);
        if (x2 > x1 && y2 > y1) hits.add(new Hit(x1, y1, x2, y2, handler));
    }

    /** Registers a nested scrollable area (e.g. a list inside a setting) that takes the mouse wheel first. */
    public void scrollArea(int x, int y, int w, int h, ScrollHandler handler) {
        int x1 = Math.max(x, clipX1), y1 = Math.max(y, clipY1);
        int x2 = Math.min(x + w, clipX2), y2 = Math.min(y + h, clipY2);
        if (x2 > x1 && y2 > y1) scrollHits.add(new ScrollHit(x1, y1, x2, y2, handler));
    }

    public boolean dispatchScroll(double mx, double my, double amount) {
        for (int i = scrollHits.size() - 1; i >= 0; i--) {
            ScrollHit hit = scrollHits.get(i);
            if (mx >= hit.x1 && mx < hit.x2 && my >= hit.y1 && my < hit.y2) {
                hit.handler.onScroll(amount);
                return true;
            }
        }
        return false;
    }

    public boolean dispatchClick(double mx, double my, int button) {
        focused = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (mx >= hit.x1 && mx < hit.x2 && my >= hit.y1 && my < hit.y2) {
                hit.handler.onClick(button, mx, my);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ helpers

    /** Smoothly animates a value towards {@code target}, keyed by any stable object. */
    public float anim(Object key, float target, float speed) {
        float[] v = anims.computeIfAbsent(key, k -> new float[]{target});
        v[0] += (target - v[0]) * Math.min(1f, delta * speed);
        if (Math.abs(target - v[0]) < 0.001f) v[0] = target;
        return v[0];
    }

    public float delta() {
        return delta;
    }

    public void tooltip(String... lines) {
        List<Text> list = new ArrayList<>();
        for (String line : lines) if (line != null && !line.isEmpty()) list.add(Text.literal(line));
        if (!list.isEmpty()) tooltip = list;
    }

    public void tooltip(List<Text> lines) {
        tooltip = lines;
    }

    public void text(String s, int x, int y, int color) {
        ctx.drawText(tr, s, x, y, color, false);
    }

    public int width(String s) {
        return tr.getWidth(s);
    }

    /** Trims a string with "..." to fit {@code maxWidth}. */
    public String ellipsize(String s, int maxWidth) {
        if (tr.getWidth(s) <= maxWidth) return s;
        String dots = "...";
        int dw = tr.getWidth(dots);
        String out = s;
        while (!out.isEmpty() && tr.getWidth(out) + dw > maxWidth) out = out.substring(0, out.length() - 1);
        return out + dots;
    }
}
