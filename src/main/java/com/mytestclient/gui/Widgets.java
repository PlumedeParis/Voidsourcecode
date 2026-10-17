package com.mytestclient.gui;

import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.render.Colors;
import com.mytestclient.render.Render2D;

/** Reusable widgets drawn with {@link Ui}. */
public final class Widgets {
    public static final int SWITCH_W = 24, SWITCH_H = 12;

    private Widgets() {
    }

    /** Animated pill toggle with the accent gradient when on. {@code key} identifies the animation. */
    public static void toggle(Ui ui, int x, int y, boolean on, Object key) {
        float t = ui.anim(key, on ? 1f : 0f, 14f);
        int r = SWITCH_H / 2;
        Render2D.roundedRect(ui.ctx, x, y, SWITCH_W, SWITCH_H, r, Theme.SWITCH_OFF);
        if (t > 0.01f) {
            // gradient fill grows from the left with the knob
            int fillW = Math.max(SWITCH_H, Math.round(SWITCH_H + (SWITCH_W - SWITCH_H) * t));
            Render2D.roundedRect(ui.ctx, x, y, fillW, SWITCH_H, r, Colors.fade(Theme.accent(), t));
            if (fillW > SWITCH_H) Render2D.hGradient(ui.ctx, x + r, y + 1, fillW - SWITCH_H, SWITCH_H - 2,
                    Colors.fade(Theme.accent(), t), Colors.fade(Theme.gradient(t), t));
        }
        int knob = x + 1 + Math.round(t * (SWITCH_W - SWITCH_H));
        Render2D.roundedRect(ui.ctx, knob, y + 2, SWITCH_H - 2, SWITCH_H - 2, (SWITCH_H - 2) / 2, 0x40000000);
        Render2D.roundedRect(ui.ctx, knob, y + 1, SWITCH_H - 2, SWITCH_H - 2, (SWITCH_H - 2) / 2, 0xFFFFFFFF);
    }

    public static void button(Ui ui, int x, int y, int w, int h, String label, Runnable action) {
        button(ui, x, y, w, h, label, false, action);
    }

    /** Rounded button; {@code primary} uses the accent gradient. Lights up smoothly on hover. */
    public static void button(Ui ui, int x, int y, int w, int h, String label, boolean primary, Runnable action) {
        boolean hover = ui.hover(x, y, w, h);
        float t = ui.anim("btn:" + label + x + "," + y, hover ? 1f : 0f, 16f);
        if (primary) {
            Render2D.glow(ui.ctx, x, y, w, h, 4, 3, Theme.accent(), 0.4f + 0.6f * t);
            Render2D.roundedRect(ui.ctx, x, y, w, h, 4, Theme.accent());
            Render2D.hGradient(ui.ctx, x + 3, y, w - 6, h, Theme.accent(), Theme.accent2());
            Render2D.roundedRect(ui.ctx, x + w - 4, y, 4, h, 2, Theme.accent2());
            if (t > 0) Render2D.roundedRect(ui.ctx, x, y, w, h, 4, Colors.withAlpha(0xFFFFFF, (int) (0x22 * t)));
        } else {
            Render2D.roundedRect(ui.ctx, x, y, w, h, 4, Colors.lerp(Theme.BUTTON, Theme.BUTTON_HOVER, t));
            Render2D.roundedOutline(ui.ctx, x, y, w, h, 4, Colors.lerp(Theme.BORDER, Colors.fade(Theme.accent(), 0.6f), t));
        }
        String text = ui.ellipsize(label, w - 6);
        ui.text(text, x + (w - ui.width(text)) / 2, y + (h - 8) / 2, primary ? 0xFFFFFFFF : Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, Math.max(0.6f, t)));
        ui.click(x, y, w, h, (b, mx, my) -> action.run());
    }

    /** Small rounded label (key binds, counters...). */
    public static int chip(Ui ui, int x, int y, String text, int bg, int fg) {
        int w = ui.width(text) + 8;
        Render2D.roundedRect(ui.ctx, x, y, w, 11, 3, bg);
        ui.text(text, x + 4, y + 2, fg);
        return w;
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

    /** Thin rounded scrollbar on the right edge of a scroll area. */
    public static void scrollbar(Ui ui, int x, int y, int h, int contentH, int offset) {
        if (contentH <= h) return;
        int barH = Math.max(16, h * h / contentH);
        int barY = y + (int) ((h - barH) * (offset / (double) (contentH - h)));
        Render2D.roundedRect(ui.ctx, x, y, 3, h, 1, 0x10FFFFFF);
        Render2D.roundedRect(ui.ctx, x, barY, 3, barH, 1, Colors.fade(Theme.accent(), 0.7f));
    }
}
