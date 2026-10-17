package com.mytestclient.module.hud;

import com.mytestclient.gui.Theme;
import com.mytestclient.hud.HudModule;
import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Modules;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.ModeSetting;
import com.mytestclient.render.Render2D;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Module List HUD: the list of enabled modules, with an optional client watermark. */
public class ModuleListHud extends HudModule {
    private final ModeSetting sort = add(new ModeSetting("Sort", "Order of the list.", "Length", "Length", "Alphabetical"));
    private final BooleanSetting watermark = add(new BooleanSetting("Watermark", "Show the client name on top.", true));
    private final BooleanSetting warnings = add(new BooleanSetting("Warning Icons", "Show ⚠ next to modules with a warning.", true));
    private final BooleanSetting hideHud = add(new BooleanSetting("Hide HUD Modules", "Do not list HUD elements.", true));

    public ModuleListHud() {
        super("Module List", "Lists your enabled modules on screen.", Category.HUD, 100, 0, false);
    }

    @Override
    protected void render(DrawContext ctx, int x, int y, float tickDelta) {
        TextRenderer tr = mc.textRenderer;
        List<String> names = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        List<Module> enabled = new ArrayList<>();
        for (Module m : Modules.all()) {
            if (!m.isEnabled() || m.isAction()) continue;
            if (hideHud.get() && m instanceof HudModule) continue;
            enabled.add(m);
        }
        if (sort.is("Length")) enabled.sort(Comparator.comparingInt((Module m) -> tr.getWidth(label(m))).reversed());
        else enabled.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
        for (Module m : enabled) {
            names.add(label(m));
            colors.add(m.getRisk() != Risk.NONE && warnings.get() ? m.getRisk().getColor() : Theme.TEXT);
        }

        int maxW = 0;
        for (String n : names) maxW = Math.max(maxW, tr.getWidth(n));
        String title = "VoidClient";
        if (watermark.get()) maxW = Math.max(maxW, tr.getWidth(title) + 8);
        int width = maxW + 8;
        int cy = y;
        boolean right = alignRight();

        if (watermark.get()) {
            Render2D.roundedRect(ctx, x, cy, width, 14, 3, Theme.HUD_BG);
            Render2D.rect(ctx, right ? x + width - 2 : x, cy, 2, 14, Theme.accent());
            int tx = right ? x + width - 6 - tr.getWidth(title) : x + 6;
            ctx.drawText(tr, title, tx, cy + 3, Theme.accent(), true);
            cy += 16;
        }
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i);
            int w = tr.getWidth(n) + 8;
            int lx = right ? x + width - w : x;
            Render2D.rect(ctx, lx, cy, w, 11, Theme.HUD_BG);
            Render2D.rect(ctx, right ? x + width - 1 : lx, cy, 1, 11, Theme.accent());
            ctx.drawText(tr, n, lx + 4, cy + 2, colors.get(i), true);
            cy += 11;
        }
        setSize(width, Math.max(12, cy - y));
    }

    private String label(Module m) {
        return warnings.get() && m.getRisk() != Risk.NONE ? m.getName() + " ⚠" : m.getName();
    }
}
