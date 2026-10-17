package com.mytestclient.gui.component;

import com.mytestclient.gui.Theme;
import com.mytestclient.gui.Ui;
import com.mytestclient.gui.Widgets;
import com.mytestclient.gui.widget.TextBox;
import com.mytestclient.module.setting.ListSetting;
import com.mytestclient.render.Colors;
import com.mytestclient.render.Render2D;
import com.mytestclient.util.RegistryEntries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Picker menu for a {@link ListSetting}: a searchable, scrollable list of every block or entity
 * with its icon and a checkbox. Selected entries are shown first. Only visible rows are drawn.
 */
public class ListComponent implements SettingComponent {
    private static final int ROW = 18, VISIBLE_ROWS = 8;
    private final ListSetting setting;
    private final TextBox search = new TextBox("Search...", 40);
    private boolean expanded;
    private double scroll;
    private String lastQuery;
    private int lastSize = -1;
    private List<RegistryEntries.Entry> filtered = List.of();

    public ListComponent(ListSetting setting) {
        this.setting = setting;
    }

    private void refilter() {
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        if (q.equals(lastQuery) && setting.size() == lastSize) return;
        lastQuery = q;
        lastSize = setting.size();
        List<RegistryEntries.Entry> selected = new ArrayList<>(), others = new ArrayList<>();
        for (RegistryEntries.Entry e : RegistryEntries.get(setting.getKind())) {
            if (!q.isEmpty() && !e.search().contains(q)) continue;
            (setting.contains(e.id()) ? selected : others).add(e);
        }
        selected.addAll(others);
        filtered = selected;
    }

    @Override
    public int render(Ui ui, int x, int y, int w) {
        int h = 18;
        if (ui.hover(x, y, w, h)) {
            Render2D.roundedRect(ui.ctx, x, y, w, h, 4, Theme.ROW_HOVER);
            ui.tooltip(setting.getDescription(), "Click to open the picker");
        }
        String summary = setting.size() + " selected " + (expanded ? "▴" : "▾");
        ui.text(ui.ellipsize(setting.getName(), w - ui.width(summary) - 20), x + 6, y + 5, Theme.TEXT);
        ui.text(summary, x + w - 6 - ui.width(summary), y + 5, Theme.accent());
        ui.click(x, y, w, h, (b, mx, my) -> expanded = !expanded);
        if (!expanded) return h;

        int cy = y + h + 2;
        search.render(ui, x + 4, cy, w - 66, 15);
        Widgets.button(ui, x + w - 60, cy, 56, 15, "Clear", () -> setting.set(new ArrayList<>()));
        cy += 19;

        refilter();
        int listH = VISIBLE_ROWS * ROW;
        int contentH = filtered.size() * ROW;
        scroll = Math.max(0, Math.min(Math.max(0, contentH - listH), scroll));
        Render2D.roundedRect(ui.ctx, x + 4, cy, w - 8, listH, 4, Theme.FIELD);
        final int listTop = cy;
        ui.scrollArea(x + 4, cy, w - 8, listH, amount -> scroll -= amount * ROW * 2);

        ui.ctx.enableScissor(x + 4, listTop, x + w - 4, listTop + listH);
        int first = (int) (scroll / ROW);
        for (int i = first; i < Math.min(filtered.size(), first + VISIBLE_ROWS + 1); i++) {
            RegistryEntries.Entry e = filtered.get(i);
            int ry = listTop + i * ROW - (int) scroll;
            boolean on = setting.contains(e.id());
            boolean hover = ui.hover(x + 4, ry, w - 8, ROW) && ry >= listTop && ry + ROW <= listTop + listH + ROW;
            if (on) Render2D.rect(ui.ctx, x + 4, ry, w - 8, ROW, Colors.withAlpha(Theme.accent(), 0x40));
            else if (hover) Render2D.rect(ui.ctx, x + 4, ry, w - 8, ROW, Theme.ROW_HOVER);
            if (!e.icon().isEmpty()) ui.ctx.drawItem(e.icon(), x + 7, ry + 1);
            ui.text(ui.ellipsize(e.name(), w - 50), x + 27, ry + 5, on ? 0xFFFFFFFF : Theme.TEXT_DIM);
            Render2D.roundedRectBordered(ui.ctx, x + w - 18, ry + 4, 10, 10, 2, on ? Theme.accent() : Theme.FIELD, on ? Theme.accent() : 0xFF3A4050);
            if (ry + ROW > listTop && ry < listTop + listH) {
                int clickTop = Math.max(ry, listTop), clickBottom = Math.min(ry + ROW, listTop + listH);
                ui.click(x + 4, clickTop, w - 8, clickBottom - clickTop, (b, mx, my) -> setting.toggle(e.id()));
            }
            if (hover) ui.tooltip(e.name(), e.id());
        }
        ui.ctx.disableScissor();
        if (filtered.isEmpty()) ui.text("Nothing found", x + 10, listTop + 6, Theme.TEXT_MUTED);
        Widgets.scrollbar(ui, x + w - 7, listTop, listH, contentH, (int) scroll);
        return cy + listH + 4 - y;
    }
}
