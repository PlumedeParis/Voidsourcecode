package com.mytestclient.gui.component;

import com.mytestclient.gui.Theme;
import com.mytestclient.gui.Ui;
import com.mytestclient.gui.widget.TextBox;
import com.mytestclient.module.setting.StringSetting;

/** Text field bound to a string setting. */
public class StringComponent implements SettingComponent {
    private final StringSetting setting;
    private final TextBox box;

    public StringComponent(StringSetting setting) {
        this.setting = setting;
        this.box = new TextBox(setting.getDescription(), setting.getMaxLength()).onChange(setting::set);
        box.setText(setting.get());
    }

    @Override
    public int render(Ui ui, int x, int y, int w) {
        if (ui.focused != box) box.setText(setting.get());
        if (ui.hover(x, y, w, 12)) ui.tooltip(setting.getDescription());
        ui.text(setting.getName(), x + 6, y + 3, Theme.TEXT);
        box.render(ui, x + 6, y + 14, w - 12, 15);
        return 32;
    }
}
