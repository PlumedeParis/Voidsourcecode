package com.mytestclient.gui.component;

import com.mytestclient.gui.Theme;
import com.mytestclient.gui.Ui;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.render.Render2D;

/** Slider. Drag to change, right click to reset. */
public class NumberComponent implements SettingComponent {
    private final NumberSetting setting;

    public NumberComponent(NumberSetting setting) {
        this.setting = setting;
    }

    @Override
    public int render(Ui ui, int x, int y, int w) {
        int h = 28;
        if (ui.hover(x, y, w, h)) {
            Render2D.roundedRect(ui.ctx, x, y, w, h, 4, Theme.ROW_HOVER);
            ui.tooltip(setting.getDescription(), "Right click to reset (" + format(setting.getDefault()) + ")");
        }
        String value = setting.format();
        ui.text(ui.ellipsize(setting.getName(), w - ui.width(value) - 18), x + 6, y + 4, Theme.TEXT);
        ui.text(value, x + w - 6 - ui.width(value), y + 4, Theme.accent());

        int tx = x + 6, tw = w - 12, ty = y + 18;
        double range = setting.getMax() - setting.getMin();
        float pct = (float) (range == 0 ? 0 : (setting.get() - setting.getMin()) / range);
        float shown = ui.anim(setting, pct, 20f);
        int filled = Math.max(4, Math.round(tw * shown));
        boolean hot = ui.hover(x, y + 10, w, h - 10);
        Render2D.roundedRect(ui.ctx, tx, ty, tw, 4, 2, Theme.SLIDER_BG);
        Render2D.roundedRect(ui.ctx, tx, ty, filled, 4, 2, Theme.accent());
        if (filled > 4) Render2D.hGradient(ui.ctx, tx + 2, ty, filled - 4, 4, Theme.accent(), Theme.gradient(shown));
        int kx = tx + Math.round(tw * shown);
        if (hot) Render2D.glow(ui.ctx, kx - 4, ty - 2, 8, 8, 4, 3, Theme.gradient(shown), 0.8f);
        Render2D.circle(ui.ctx, kx, ty + 2, 4, 0xFFFFFFFF);
        Render2D.circle(ui.ctx, kx, ty + 2, 2, Theme.gradient(shown));

        ui.click(x, y + 10, w, h - 10, (b, mx, my) -> {
            if (b == 1) {
                setting.reset();
                return;
            }
            update(mx, tx, tw);
            ui.drag = (dx, dy) -> update(dx, tx, tw);
        });
        return h;
    }

    private void update(double mouseX, int tx, int tw) {
        double pct = Math.max(0, Math.min(1, (mouseX - tx) / tw));
        setting.set(setting.getMin() + pct * (setting.getMax() - setting.getMin()));
    }

    private String format(double v) {
        return setting.getStep() >= 1 ? Integer.toString((int) Math.round(v)) : Double.toString(v);
    }
}
