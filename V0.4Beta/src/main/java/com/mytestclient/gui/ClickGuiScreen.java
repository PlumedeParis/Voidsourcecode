package com.mytestclient.gui;

import com.mytestclient.MyTestClient;
import com.mytestclient.gui.panel.ProfilesPanel;
import com.mytestclient.gui.panel.SettingsPanel;
import com.mytestclient.gui.panel.WaypointsPanel;
import com.mytestclient.gui.widget.TextBox;
import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Modules;
import com.mytestclient.module.SafeMode;
import com.mytestclient.module.misc.ClickGuiModule;
import com.mytestclient.render.Colors;
import com.mytestclient.render.Render2D;
import com.mytestclient.util.KeyNames;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The click GUI (Right Shift). Layout: header with search, category sidebar, scrollable module
 * list in the middle and the selected module's settings on the right. The Waypoints and Profiles
 * pages replace the two right-hand columns.
 * <p>
 * Everything is drawn immediate-mode through {@link Ui}; to add a page, add a {@link Page}
 * constant, a sidebar entry in {@link #renderSidebar()} and a case in {@link #render}.
 */
public class ClickGuiScreen extends BaseScreen {
    private enum Page {MODULES, WAYPOINTS, PROFILES}

    private static final int HEADER = 28;
    private static final int SIDEBAR = 104;
    private static final int PAD = 8;
    private static final int CARD_H = 24;

    // remembered between openings
    private static Page page = Page.MODULES;
    private static Category category = Category.MOVEMENT;
    private static Module selected;
    private static int offsetX, offsetY;

    private final Ui ui = new Ui();
    private final TextBox search = new TextBox("Search modules...", 32);
    private final ScrollState moduleScroll = new ScrollState();
    private final ScrollState settingsScroll = new ScrollState();
    private final WaypointsPanel waypointsPanel = new WaypointsPanel();
    private final ProfilesPanel profilesPanel = new ProfilesPanel();
    private SettingsPanel settingsPanel;

    private int winX, winY, winW, winH;
    private int listX, listY, listW, listH;
    private int setX, setY, setW, setH;

    public ClickGuiScreen() {
        super(Text.literal(MyTestClient.NAME));
        search.onChange(s -> moduleScroll.reset());
    }

    private void layout() {
        winW = Math.min(660, width - 16);
        winH = Math.min(390, height - 16);
        winX = MathHelper.clamp((width - winW) / 2 + offsetX, 0, Math.max(0, width - winW));
        winY = MathHelper.clamp((height - winH) / 2 + offsetY, 0, Math.max(0, height - winH));

        int contentX = winX + SIDEBAR + PAD;
        int contentW = winW - SIDEBAR - PAD * 2;
        listX = contentX;
        listY = winY + HEADER + PAD;
        listW = (int) (contentW * 0.42);
        listH = winH - HEADER - PAD * 2;
        setX = listX + listW + PAD;
        setY = listY;
        setW = contentW - listW - PAD;
        setH = listH;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ui.beginFrame(ctx, textRenderer, mouseX, mouseY, width, height);
        layout();

        // window shadow + body
        Render2D.roundedRect(ctx, winX - 4, winY - 4, winW + 8, winH + 8, 12, 0x30000000);
        Render2D.roundedRect(ctx, winX - 2, winY - 2, winW + 4, winH + 4, 10, 0x50000000);
        Render2D.roundedRect(ctx, winX, winY, winW, winH, 8, Theme.BG);

        renderSidebar();
        renderHeader();

        switch (page) {
            case MODULES -> renderModules();
            case WAYPOINTS -> {
                int x = winX + SIDEBAR + PAD, w = winW - SIDEBAR - PAD * 2;
                Render2D.roundedRect(ctx, x, listY, w, listH, 6, Theme.PANEL);
                waypointsPanel.render(ui, x + PAD, listY + PAD, w - PAD * 2, listH - PAD * 2);
            }
            case PROFILES -> {
                int x = winX + SIDEBAR + PAD, w = winW - SIDEBAR - PAD * 2;
                Render2D.roundedRect(ctx, x, listY, w, listH, 6, Theme.PANEL);
                profilesPanel.render(ui, x + PAD, listY + PAD, w - PAD * 2, listH - PAD * 2);
            }
        }

        ui.endFrame();
    }

    // ------------------------------------------------------------------ header

    private void renderHeader() {
        DrawContext ctx = ui.ctx;
        Render2D.roundedRect(ctx, winX, winY, winW, HEADER, 8, Theme.HEADER);
        ctx.fill(winX, winY + HEADER - 8, winX + winW, winY + HEADER, Theme.HEADER);
        ctx.fill(winX, winY + HEADER, winX + winW, winY + HEADER + 1, Theme.SEPARATOR);

        // drag the window by its header
        ui.click(winX, winY, winW, HEADER, (b, mx, my) -> {
            int startX = offsetX, startY = offsetY;
            ui.drag = (dx, dy) -> {
                offsetX = startX + (int) (dx - mx);
                offsetY = startY + (int) (dy - my);
            };
        });

        Render2D.circle(ctx, winX + 14, winY + HEADER / 2, 4, Theme.accent());
        ui.text(MyTestClient.NAME, winX + 24, winY + 10, Theme.TEXT);
        ui.text("v" + MyTestClient.VERSION, winX + 28 + ui.width(MyTestClient.NAME), winY + 10, Theme.TEXT_MUTED);

        int hudW = 64;
        int searchW = Math.min(170, winW - SIDEBAR - hudW - 140);
        search.render(ui, winX + winW - hudW - searchW - 14, winY + 6, searchW, 16);
        Widgets.button(ui, winX + winW - hudW - 8, winY + 6, hudW, 16, "HUD Editor", () ->
                client.setScreen(new HudEditorScreen(this)));
    }

    // ------------------------------------------------------------------ sidebar

    private void renderSidebar() {
        DrawContext ctx = ui.ctx;
        Render2D.roundedRect(ctx, winX, winY, SIDEBAR, winH, 8, Theme.SIDEBAR);
        ctx.fill(winX + SIDEBAR - 8, winY, winX + SIDEBAR, winY + winH, Theme.SIDEBAR);
        ctx.fill(winX + SIDEBAR, winY + HEADER, winX + SIDEBAR + 1, winY + winH, Theme.SEPARATOR);

        int y = winY + HEADER + PAD;
        for (Category c : Category.values()) {
            List<Module> mods = MyTestClient.get().modules().byCategory(c);
            int on = 0;
            for (Module m : mods) if (m.isEnabled()) on++;
            boolean active = page == Page.MODULES && category == c && search.getText().isEmpty();
            sidebarItem(c.getDisplayName(), on > 0 ? Integer.toString(on) : "", active, y, () -> {
                page = Page.MODULES;
                category = c;
                search.setText("");
                moduleScroll.reset();
            });
            y += 20;
        }

        y += 4;
        ctx.fill(winX + 10, y, winX + SIDEBAR - 10, y + 1, Theme.SEPARATOR);
        y += 7;
        sidebarItem("Waypoints", "", page == Page.WAYPOINTS, y, () -> page = Page.WAYPOINTS);
        y += 20;
        sidebarItem("Profiles", "", page == Page.PROFILES, y, () -> page = Page.PROFILES);

        // Safe Mode quick toggle at the bottom
        ClickGuiModule gui = Modules.get(ClickGuiModule.class);
        int by = winY + winH - 24;
        boolean safe = SafeMode.isOn();
        Widgets.toggle(ui, winX + 10, by + 4, safe, gui.safeMode);
        ui.text("Safe Mode", winX + 10 + Widgets.SWITCH_W + 5, by + 6, safe ? Theme.TEXT : Theme.TEXT_DIM);
        ui.click(winX + 6, by, SIDEBAR - 12, 18, (b, mx, my) -> gui.safeMode.toggle());
        if (ui.hover(winX + 6, by, SIDEBAR - 12, 18)) {
            ui.tooltip("Safe Mode", "Blocks modules with a red ⚠ (detectable by servers) while on multiplayer.");
        }
    }

    private void sidebarItem(String label, String badge, boolean active, int y, Runnable action) {
        int x = winX + 6, w = SIDEBAR - 12, h = 18;
        boolean hover = ui.hover(x, y, w, h);
        float t = ui.anim("side:" + label, active ? 1f : 0f, 12f);
        if (t > 0.01f) Render2D.roundedRect(ui.ctx, x, y, w, h, 4, Colors.withAlpha(Theme.accent(), (int) (0x38 * t)));
        else if (hover) Render2D.roundedRect(ui.ctx, x, y, w, h, 4, Theme.ROW_HOVER);
        if (t > 0.01f) Render2D.roundedRect(ui.ctx, x, y + 4, 2, h - 8, 1, Colors.withAlpha(Theme.accent(), (int) (255 * t)));
        ui.text(label, x + 9, y + 5, active ? 0xFFFFFFFF : (hover ? Theme.TEXT : Theme.TEXT_DIM));
        if (!badge.isEmpty()) {
            int bw = ui.width(badge) + 8;
            Render2D.roundedRect(ui.ctx, x + w - bw - 3, y + 3, bw, 12, 6, Colors.withAlpha(Theme.accent(), 0x60));
            ui.text(badge, x + w - bw + 1, y + 5, 0xFFFFFFFF);
        }
        ui.click(x, y, w, h, (b, mx, my) -> action.run());
    }

    // ------------------------------------------------------------------ modules page

    private List<Module> visibleModules() {
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return MyTestClient.get().modules().byCategory(category);
        List<Module> result = new ArrayList<>();
        for (Module m : MyTestClient.get().modules().all()) {
            if (m.getName().toLowerCase(Locale.ROOT).contains(q) || m.getDescription().toLowerCase(Locale.ROOT).contains(q)) {
                result.add(m);
            }
        }
        return result;
    }

    private void select(Module module) {
        if (selected == module && settingsPanel != null && settingsPanel.getModule() == module) return;
        selected = module;
        settingsPanel = module == null ? null : new SettingsPanel(module);
        settingsScroll.reset();
    }

    private void renderModules() {
        DrawContext ctx = ui.ctx;
        List<Module> modules = visibleModules();
        if ((selected == null || !modules.contains(selected)) && !modules.isEmpty()) select(modules.get(0));
        else if (settingsPanel == null || settingsPanel.getModule() != selected) select(selected);

        // ---- module list
        Render2D.roundedRect(ctx, listX, listY, listW, listH, 6, Theme.PANEL);
        String title = search.getText().isEmpty() ? category.getDisplayName() : "Search results";
        ui.text(title, listX + PAD, listY + PAD, Theme.TEXT);
        String count = modules.size() + (modules.size() == 1 ? " module" : " modules");
        ui.text(count, listX + listW - PAD - ui.width(count), listY + PAD, Theme.TEXT_MUTED);

        int areaY = listY + PAD + 14, areaH = listH - PAD * 2 - 14;
        moduleScroll.setBounds(modules.size() * (CARD_H + 3), areaH);
        int offset = moduleScroll.update(ui.delta());
        ui.clip(listX, areaY, listX + listW, areaY + areaH);
        int y = areaY - offset;
        for (Module m : modules) {
            if (y + CARD_H >= areaY && y <= areaY + areaH) renderCard(m, listX + PAD, y, listW - PAD * 2 - 4);
            y += CARD_H + 3;
        }
        if (modules.isEmpty()) ui.text("No modules found.", listX + PAD, areaY + 4, Theme.TEXT_MUTED);
        ui.unclip();
        Widgets.scrollbar(ui, listX + listW - 6, areaY, areaH, modules.size() * (CARD_H + 3), offset);

        // ---- settings
        Render2D.roundedRect(ctx, setX, setY, setW, setH, 6, Theme.PANEL);
        if (settingsPanel == null) return;
        int sx = setX + PAD + 2, sy = setY + PAD, sw = setW - PAD * 2 - 6, sh = setH - PAD * 2;
        int sOffset = settingsScroll.update(ui.delta());
        ui.clip(setX, sy, setX + setW, sy + sh);
        int contentH = settingsPanel.render(ui, sx, sy - sOffset, sw);
        ui.unclip();
        settingsScroll.setBounds(contentH, sh);
        Widgets.scrollbar(ui, setX + setW - 6, sy, sh, contentH, sOffset);
    }

    private void renderCard(Module m, int x, int y, int w) {
        boolean hover = ui.hover(x, y, w, CARD_H);
        boolean isSelected = m == selected;
        float on = ui.anim("card:" + m.getName(), m.isEnabled() ? 1f : 0f, 12f);
        int bg = isSelected ? Colors.lerp(Theme.CARD, Theme.accent(), 0.18f) : (hover ? Theme.CARD_HOVER : Theme.CARD);
        Render2D.roundedRect(ui.ctx, x, y, w, CARD_H, 5, bg);
        if (on > 0.01f) Render2D.roundedRect(ui.ctx, x, y + 5, 2, CARD_H - 10, 1, Colors.withAlpha(Theme.accent(), (int) (255 * on)));

        ui.click(x, y, w, CARD_H, (b, mx, my) -> {
            if (b == 1) m.toggle();
            else select(m);
        });
        if (hover) ui.tooltip(m.getDescription(), "Left click: settings • Right click: toggle");

        int textColor = Colors.lerp(Theme.TEXT_DIM, 0xFFFFFFFF, on);
        String name = ui.ellipsize(m.getName(), w - 80);
        ui.text(name, x + 9, y + 8, textColor);
        int iconX = x + 9 + ui.width(name) + 4;
        Widgets.warningIcon(ui, m, iconX, y + 8);

        int right = x + w - 8;
        if (m.isAction()) {
            Widgets.button(ui, right - 34, y + 5, 34, 14, "Run", true, m::toggle);
            right -= 40;
        } else {
            Widgets.toggle(ui, right - Widgets.SWITCH_W, y + 7, m.isEnabled(), m);
            ui.click(right - Widgets.SWITCH_W - 2, y + 4, Widgets.SWITCH_W + 4, 16, (b, mx, my) -> m.toggle());
            right -= Widgets.SWITCH_W + 6;
        }
        if (m.getKey() > 0) {
            String key = KeyNames.name(m.getKey());
            int kw = ui.width(key) + 8;
            Render2D.roundedRect(ui.ctx, right - kw, y + 6, kw, 12, 3, Theme.FIELD);
            ui.text(key, right - kw + 4, y + 8, Theme.TEXT_MUTED);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean onMouseClick(double mouseX, double mouseY, int button) {
        if (settingsPanel != null && settingsPanel.isListening()) {
            // clicking anywhere cancels keybind listening
            settingsPanel.handleBindKey(GLFW.GLFW_KEY_ESCAPE);
            return true;
        }
        ui.dispatchClick(mouseX, mouseY, button);
        return true;
    }

    @Override
    protected void onMouseRelease(double mouseX, double mouseY, int button) {
        ui.drag = null;
    }

    @Override
    protected void onMouseDrag(double mouseX, double mouseY, int button) {
        if (ui.drag != null) ui.drag.onDrag(mouseX, mouseY);
    }

    @Override
    protected boolean onScroll(double mouseX, double mouseY, double amount) {
        if (ui.dispatchScroll(mouseX, mouseY, amount)) return true;
        switch (page) {
            case MODULES -> {
                if (inside(mouseX, mouseY, listX, listY, listW, listH)) moduleScroll.scroll(amount);
                else if (inside(mouseX, mouseY, setX, setY, setW, setH)) settingsScroll.scroll(amount);
            }
            case WAYPOINTS -> waypointsPanel.scroll(mouseX, mouseY, amount);
            case PROFILES -> profilesPanel.scroll(mouseX, mouseY, amount);
        }
        return true;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    protected boolean onKey(int key, int scancode, int modifiers) {
        if (settingsPanel != null && settingsPanel.isListening()) {
            settingsPanel.handleBindKey(key);
            return true;
        }
        if (ui.focused != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                ui.focused = null;
                return true;
            }
            return ui.focused.keyPressed(key, modifiers);
        }
        // Ctrl+F focuses the search box
        if (key == GLFW.GLFW_KEY_F && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            page = Page.MODULES;
            ui.focused = search;
            return true;
        }
        ClickGuiModule gui = Modules.get(ClickGuiModule.class);
        if (gui != null && key == gui.getKey()) {
            close();
            return true;
        }
        return false;
    }

    @Override
    protected boolean onChar(int codepoint) {
        if (ui.focused != null) return ui.focused.charTyped(codepoint);
        return false;
    }

    @Override
    public void removed() {
        MyTestClient.get().config().save();
        super.removed();
    }
}
