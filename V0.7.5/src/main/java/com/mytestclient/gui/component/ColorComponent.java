package com.mytestclient.gui.component;

import com.mytestclient.gui.Theme;
import com.mytestclient.gui.Ui;
import com.mytestclient.module.setting.ColorSetting;
import com.mytestclient.render.Render2D;

/** Colour swatch that expands into R/G/B/A sliders. */
public class ColorComponent implements SettingComponent {
    private static final String[] CHANNELS = {"R", "G", "B", "A"};
    private static final int[] CHANNEL_COLORS = {0xFFEF4444, 0xFF4ADE80, 0xFF60A5FA, 0xFFD4D4D8};
    private final ColorSetting setting;
    private boolean expanded;

    public ColorComponent(ColorSetting setting) {
        this.setting = setting;
    }

    @Override
    public int render(Ui ui, int x, int y, int w) {
        int h = 18;
        if (ui.hover(x, y, w, h)) {
            Render2D.roundedRect(ui.ctx, x, y, w, h, 4, Theme.ROW_HOVER);
            ui.tooltip(setting.getDescription(), "Click to edit, right click to reset");
        }
        String hex = setting.toHex();
        ui.text(ui.ellipsize(setting.getName(), w - ui.width(hex) - 40), x + 6, y + 5, Theme.TEXT);
        int swX = x + w - 22;
        Render2D.roundedRectBordered(ui.ctx, swX, y + 3, 16, 12, 3, setting.get(), 0xFF3A4050);
        ui.text(hex, swX - 4 - ui.width(hex), y + 5, Theme.TEXT_DIM);
        ui.click(x, y, w, h, (b, mx, my) -> {
            if (b == 1) setting.reset();
            else expanded = !expanded;
        });
        if (!expanded) return h;

        int cy = y + h;
        for (int c = 0; c < 4; c++) {
            final int channel = c;
            int tx = x + 20, tw = w - 52, ty = cy + 5;
            ui.text(CHANNELS[c], x + 8, cy + 2, CHANNEL_COLORS[c]);
            Render2D.roundedRect(ui.ctx, tx, ty, tw, 4, 2, Theme.SLIDER_BG);
            float pct = setting.getChannel(c) / 255f;
            Render2D.roundedRect(ui.ctx, tx, ty, Math.max(4, Math.round(tw * pct)), 4, 2, CHANNEL_COLORS[c]);
            Render2D.circle(ui.ctx, tx + Math.round(tw * pct), ty + 2, 3, 0xFFFFFFFF);
            String v = Integer.toString(setting.getChannel(c));
            ui.text(v, x + w - 6 - ui.width(v), cy + 2, Theme.TEXT_DIM);
            ui.click(tx - 4, cy, tw + 8, 13, (b, mx, my) -> {
                set(channel, mx, tx, tw);
                ui.drag = (dx, dy) -> set(channel, dx, tx, tw);
            });
            cy += 13;
        }
        return cy - y + 3;
    }

    private void set(int channel, double mouseX, int tx, int tw) {
        double pct = Math.max(0, Math.min(1, (mouseX - tx) / tw));
        setting.setChannel(channel, (int) Math.round(pct * 255));
    }
}
