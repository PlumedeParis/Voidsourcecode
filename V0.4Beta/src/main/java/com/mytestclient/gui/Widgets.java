package com.mytestclient.gui;

import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.render.Colors;
import com.mytestclient.render.Render2D;

/** Reusable widgets drawn with {@link Ui}. */
public final class Widgets {
    public static final int SWITCH_W = 22, SWITCH_H = 11;

    private Widgets() {
    }

    /** Animated pill toggle. {@code key} identifies the animation (use the setting/module). */
    public static void toggle(Ui ui, int x, int y, boolean on, Object key) {
        float t = ui.anim(key, on ? 1f : 0f, 14f);
        Render2D.roundedRect(ui.ctx, x, y, SWITCH_W, SWITCH_H, SWITCH_H / 2, Colors.lerp(Theme.SWITCH_OFF, Theme.accent(), t));
        int knob = x + 1 + Math.round(t * (SWITCH_W - SWITCH_H));
        Render2D.roundedRect(ui.ctx, knob, y + 1, SWITCH_H - 2, SWITCH_H - 2, (SWITCH_H - 2) / 2, 0xFFFFFFFF);
    }

    public static void button(Ui ui, int x, int y, int w, int h, String label, Runnable action) {
        button(ui, x, y, w, h, label, false, action);
    }

    /** Rounded button; {@code primary} uses the accent colour. */
    public static void button(Ui ui, int x, int y, int w, int h, String label, boolean primary, Runnable action) {
        boolean hover = ui.hover(x, y, w, h);
        int color = primary
                ? (hover ? Colors.lerp(Theme.accent(), 0xFFFFFFFF, 0.15f) : Theme.accent())
                : (hover ? Theme.BUTTON_HOVER : Theme.BUTTON);
        Render2D.roundedRect(ui.ctx, x, y, w, h, 4, color);
        String text = ui.ellipsize(label, w - 6);
        ui.text(text, x + (w - ui.width(text)) / 2, y + (h - 8) / 2, primary ? 0xFFFFFFFF : Theme.TEXT);
        ui.click(x, y, w, h, (b, mx, my) -> action.run());
    }

    /** ⚠ icon coloured by risk, with an explanatory tooltip. Returns the width drawn. */
    public static int warningIcon(Ui ui, Module module, int x, int y) {
        if (module.getRisk() == Risk.NONE) return 0;
        String icon = "⚠";
        ui.text(icon, x, y, module.getRisk().getColor());
        int w = ui.width(icon);
        if (ui.hover(x - 1, y - 2, w + 2, 12)) ui.tooltip("⚠ " + module.getRisk().getTitle(), module.getRiskReason());
        return w;
    }

    /** Thin scrollbar on the right edge of a scroll area. */
    public static void scrollbar(Ui ui, int x, int y, int h, int contentH, int offset) {
        if (contentH <= h) return;
        int barH = Math.max(16, h * h / contentH);
        int barY = y + (int) ((h - barH) * (offset / (double) (contentH - h)));
        Render2D.roundedRect(ui.ctx, x, barY, 3, barH, 1, 0x40FFFFFF);
    }
}
