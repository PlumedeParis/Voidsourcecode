package com.mytestclient.gui.panel;

import com.mytestclient.gui.Theme;
import com.mytestclient.gui.Ui;
import com.mytestclient.gui.Widgets;
import com.mytestclient.gui.component.SettingComponent;
import com.mytestclient.gui.component.SettingComponents;
import com.mytestclient.module.BindMode;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.misc.ClickGuiModule;
import com.mytestclient.module.setting.Setting;
import com.mytestclient.render.Colors;
import com.mytestclient.render.Render2D;
import com.mytestclient.util.KeyNames;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Right-hand panel of the click GUI for one module: description, warning box, enable switch,
 * keybind, bind mode and an automatically generated row for every setting.
 */
public class SettingsPanel {
    private final Module module;
    private final List<Setting<?>> settings = new ArrayList<>();
    private final List<SettingComponent> components = new ArrayList<>();
    private boolean listening;

    public SettingsPanel(Module module) {
        this.module = module;
        for (Setting<?> setting : module.getSettings()) {
            settings.add(setting);
            components.add(SettingComponents.create(setting));
        }
    }

    public Module getModule() {
        return module;
    }

    public boolean isListening() {
        return listening;
    }

    /** Receives the next key press while waiting for a keybind. */
    public void handleBindKey(int key) {
        listening = false;
        if (key == GLFW.GLFW_KEY_ESCAPE) return;
        if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
            // the GUI must always stay reachable
            module.setKey(module instanceof ClickGuiModule ? GLFW.GLFW_KEY_RIGHT_SHIFT : GLFW.GLFW_KEY_UNKNOWN);
            return;
        }
        module.setKey(key);
    }

    /** @return content height */
    public int render(Ui ui, int x, int y, int w) {
        int cy = y;

        ui.text(module.getName(), x, cy, Theme.accent());
        String cat = module.getCategory().getDisplayName();
        ui.text(cat, x + w - ui.width(cat), cy, Theme.TEXT_MUTED);
        cy += 13;

        for (OrderedText line : ui.tr.wrapLines(Text.literal(module.getDescription()), w)) {
            ui.ctx.drawText(ui.tr, line, x, cy, Theme.TEXT_DIM, false);
            cy += 10;
        }
        cy += 5;

        if (module.getRisk() != Risk.NONE) {
            int color = module.getRisk().getColor();
            List<OrderedText> lines = ui.tr.wrapLines(Text.literal(module.getRiskReason()), w - 22);
            int boxH = 14 + lines.size() * 10 + 4;
            Render2D.roundedRect(ui.ctx, x, cy, w, boxH, 5, Colors.withAlpha(color, 0x26));
            Render2D.rect(ui.ctx, x, cy + 3, 2, boxH - 6, color);
            ui.text("⚠ " + module.getRisk().getTitle(), x + 8, cy + 4, color);
            int ly = cy + 15;
            for (OrderedText line : lines) {
                ui.ctx.drawText(ui.tr, line, x + 8, ly, Theme.TEXT, false);
                ly += 10;
            }
            cy += boxH + 6;
        }

        // enable switch / run button
        if (module.isAction()) {
            Widgets.button(ui, x, cy, w, 18, "Run", true, module::toggle);
            cy += 22;
        } else {
            row(ui, x, cy, w, "Enabled", "Turn the module on or off.");
            Widgets.toggle(ui, x + w - Widgets.SWITCH_W - 6, cy + 4, module.isEnabled(), module);
            ui.click(x, cy, w, 18, (b, mx, my) -> module.toggle());
            cy += 20;
        }

        // keybind
        row(ui, x, cy, w, "Keybind", "Click, then press a key. Delete/Backspace clears, Esc cancels.");
        String keyText = listening ? "Press a key..." : KeyNames.name(module.getKey());
        int chipW = ui.width(keyText) + 12;
        int chipX = x + w - chipW - 4;
        Render2D.roundedRectBordered(ui.ctx, chipX, cy + 2, chipW, 14, 4, Theme.FIELD, listening ? Theme.accent() : Theme.BORDER);
        ui.text(keyText, chipX + 6, cy + 5, listening ? Theme.accent() : Theme.TEXT);
        ui.click(x, cy, w, 18, (b, mx, my) -> {
            if (b == 1) handleBindKey(GLFW.GLFW_KEY_DELETE);
            else listening = true;
        });
        cy += 20;

        // bind mode
        if (!module.isAction()) {
            row(ui, x, cy, w, "Bind Mode", "Toggle: press to switch on/off. Hold: active only while the key is held.");
            int segX = x + w - 4;
            BindMode[] modes = BindMode.values();
            for (int i = modes.length - 1; i >= 0; i--) {
                BindMode mode = modes[i];
                int sw = ui.width(mode.getDisplayName()) + 12;
                segX -= sw;
                boolean selected = module.getBindMode() == mode;
                Render2D.roundedRect(ui.ctx, segX, cy + 2, sw, 14, 4, selected ? Theme.accent() : Theme.FIELD);
                ui.text(mode.getDisplayName(), segX + 6, cy + 5, selected ? 0xFFFFFFFF : Theme.TEXT_DIM);
                ui.click(segX, cy + 2, sw, 14, (b, mx, my) -> module.setBindMode(mode));
                segX -= 2;
            }
            cy += 20;
        }

        // settings
        boolean header = false;
        for (int i = 0; i < components.size(); i++) {
            if (!settings.get(i).isVisible()) continue;
            if (!header) {
                cy += 4;
                ui.text("SETTINGS", x, cy, Theme.TEXT_MUTED);
                Render2D.rect(ui.ctx, x + ui.width("SETTINGS") + 6, cy + 4, w - ui.width("SETTINGS") - 6, 1, Theme.SEPARATOR);
                cy += 13;
                header = true;
            }
            cy += components.get(i).render(ui, x, cy, w) + 2;
        }

        if (!settings.isEmpty()) {
            cy += 6;
            Widgets.button(ui, x, cy, w, 16, "Reset settings to defaults", module::resetSettings);
            cy += 18;
        }
        return cy - y;
    }

    private static void row(Ui ui, int x, int y, int w, String label, String tooltip) {
        if (ui.hover(x, y, w, 18)) {
            Render2D.roundedRect(ui.ctx, x, y, w, 18, 4, Theme.ROW_HOVER);
            ui.tooltip(tooltip);
        }
        ui.text(label, x + 6, y + 5, Theme.TEXT);
    }
}
