package com.mytestclient.gui.component;

import com.mytestclient.gui.Theme;
import com.mytestclient.gui.Ui;
import com.mytestclient.gui.Widgets;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.render.Render2D;

public class BooleanComponent implements SettingComponent {
    private final BooleanSetting setting;

    public BooleanComponent(BooleanSetting setting) {
        this.setting = setting;
    }

    @Override
    public int render(Ui ui, int x, int y, int w) {
        int h = 18;
        if (ui.hover(x, y, w, h)) {
            Render2D.roundedRect(ui.ctx, x, y, w, h, 4, Theme.ROW_HOVER);
            ui.tooltip(setting.getDescription());
        }
        ui.text(ui.ellipsize(setting.getName(), w - 40), x + 6, y + 5, Theme.TEXT);
        Widgets.toggle(ui, x + w - Widgets.SWITCH_W - 6, y + 4, setting.get(), setting);
        ui.click(x, y, w, h, (b, mx, my) -> {
            if (b == 1) setting.reset();
            else setting.toggle();
        });
        return h;
    }
}
