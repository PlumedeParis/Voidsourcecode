package com.mytestclient.gui.component;

import com.mytestclient.gui.Theme;
import com.mytestclient.gui.Ui;
import com.mytestclient.module.setting.ModeSetting;
import com.mytestclient.render.Colors;
import com.mytestclient.render.Render2D;

/**
 * Dropdown. The option list expands in place (pushing rows below down) so it never overlaps
 * other widgets. Right click cycles without opening.
 */
public class ModeComponent implements SettingComponent {
    private static final int OPTION_H = 14;
    private final ModeSetting setting;
    private boolean expanded;

    public ModeComponent(ModeSetting setting) {
        this.setting = setting;
    }

    @Override
    public int render(Ui ui, int x, int y, int w) {
        int h = 18;
        if (ui.hover(x, y, w, h)) {
            Render2D.roundedRect(ui.ctx, x, y, w, h, 4, Theme.ROW_HOVER);
            ui.tooltip(setting.getDescription(), "Right click to cycle");
        }
        int boxW = 0;
        for (String mode : setting.getModes()) boxW = Math.max(boxW, ui.width(mode));
        boxW = Math.min(boxW + 20, w / 2);
        int bx = x + w - boxW - 4;
        ui.text(ui.ellipsize(setting.getName(), bx - x - 10), x + 6, y + 5, Theme.TEXT);
        Render2D.roundedRectBordered(ui.ctx, bx, y + 2, boxW, 14, 4, Theme.FIELD, expanded ? Theme.accent() : 0xFF2A2F3A);
        ui.text(ui.ellipsize(setting.get(), boxW - 16), bx + 5, y + 5, Theme.TEXT);
        ui.text(expanded ? "▴" : "▾", bx + boxW - 10, y + 5, Theme.TEXT_DIM);
        ui.click(x, y, w, h, (b, mx, my) -> {
            if (b == 1) setting.cycle();
            else expanded = !expanded;
        });

        if (!expanded) return h;

        int listY = y + h + 1;
        int listH = setting.getModes().size() * OPTION_H + 4;
        Render2D.roundedRect(ui.ctx, bx, listY, boxW, listH, 4, Theme.FIELD);
        int oy = listY + 2;
        for (String mode : setting.getModes()) {
            boolean selected = setting.is(mode);
            boolean hover = ui.hover(bx, oy, boxW, OPTION_H);
            if (hover || selected) {
                Render2D.roundedRect(ui.ctx, bx + 2, oy, boxW - 4, OPTION_H, 3,
                        selected ? Colors.withAlpha(Theme.accent(), 0x50) : Theme.ROW_HOVER);
            }
            ui.text(ui.ellipsize(mode, boxW - 10), bx + 6, oy + 3, selected ? 0xFFFFFFFF : Theme.TEXT_DIM);
            ui.click(bx, oy, boxW, OPTION_H, (b, mx, my) -> {
                setting.set(mode);
                expanded = false;
            });
            oy += OPTION_H;
        }
        return h + listH + 3;
    }
}
