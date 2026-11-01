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
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The click GUI (Right Shift), "Void" design.
 * <ul>
 *     <li>Blurred, darkened game with drifting stars behind a floating window that pops in.</li>
 *     <li>Sidebar: logo, categories with icons and a sliding selection, tools (waypoints,
 *     profiles, seed map, HUD editor) and the Safe Mode switch.</li>
 *     <li>Module cards in a grid (name, description, ⚠, keybind, switch) that cascade in when
 *     you change category. Left click opens the settings drawer, right click toggles.</li>
 *     <li>Settings drawer sliding in from the right over the grid.</li>
 * </ul>
 * Everything is immediate-mode through {@link Ui}.
 */
public class ClickGuiScreen extends BaseScreen {
    private enum Page {MODULES, WAYPOINTS, PROFILES}

    private static final int SIDEBAR = 134;
    private static final int TOPBAR = 48;
    private static final int PAD = 14;
    private static final int CARD_H = 46, GAP = 8;

    // remembered between openings
    private static Page page = Page.MODULES;
    private static Category category = Category.MOVEMENT;
    private static int offsetX, offsetY;

    private final Ui ui = new Ui();
    private final TextBox search = new TextBox("Search modules...", 32);
    private final ScrollState moduleScroll = new ScrollState();
    private final ScrollState settingsScroll = new ScrollState();
    private final WaypointsPanel waypointsPanel = new WaypointsPanel();
    private final ProfilesPanel profilesPanel = new ProfilesPanel();

    private SettingsPanel settingsPanel;
    private Module drawerModule;
    private boolean drawerOpen;

    private float open;
    private boolean closing;
    private long switchTime = System.nanoTime();
    private String lastListKey = "";
    private final long startTime = System.nanoTime();

    private int winX, winY, winW, winH;
    private int cx, cw; // content column
    private int gridX, gridY, gridW, gridH;
    private int drawerX, drawerW;

    // starfield
    private static final int STARS = 110;
    private static final float[] SX = new float[STARS], SY = new float[STARS], SPEED = new float[STARS], PHASE = new float[STARS];
    private static final boolean[] TINTED = new boolean[STARS];

    static {
        Random r = new Random(0x701D);
        for (int i = 0; i < STARS; i++) {
            SX[i] = r.nextFloat();
            SY[i] = r.nextFloat();
            SPEED[i] = 0.004f + r.nextFloat() * 0.012f;
            PHASE[i] = r.nextFloat() * 6.28f;
            TINTED[i] = r.nextFloat() < 0.35f;
        }
    }

    public ClickGuiScreen() {
        super(Text.literal(MyTestClient.NAME));
        search.onChange(s -> moduleScroll.reset());
        if (!Theme.animations()) open = 1;
    }

    private ClickGuiModule gui() {
        return Modules.get(ClickGuiModule.class);
    }

    private void layout() {
        winW = Math.min(780, width - 24);
        winH = Math.min(460, height - 24);
        winX = MathHelper.clamp((width - winW) / 2 + offsetX, 0, Math.max(0, width - winW));
        winY = MathHelper.clamp((height - winH) / 2 + offsetY, 0, Math.max(0, height - winH));
        cx = winX + SIDEBAR;
        cw = winW - SIDEBAR;
        gridX = cx + PAD;
        gridY = winY + TOPBAR + 6;
        gridW = cw - PAD * 2;
        gridH = winH - TOPBAR - 6 - 22;
        drawerW = Math.min(320, (int) (cw * 0.62));
    }

    private static float outCubic(float t) {
        t = MathHelper.clamp(t, 0, 1);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    private static float outBack(float t) {
        t = MathHelper.clamp(t, 0, 1);
        float c1 = 1.4f, c3 = c1 + 1;
        return 1 + c3 * (t - 1) * (t - 1) * (t - 1) + c1 * (t - 1) * (t - 1);
    }

    private float seconds() {
        return (System.nanoTime() - startTime) / 1_000_000_000f;
    }

    // ------------------------------------------------------------------ background

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ClickGuiModule g = gui();
        float a = outCubic(open);
        if (g == null || g.blur.get()) {
            try {
                ctx.applyBlur();
            } catch (Throwable ignored) {
                // blur can only run once per frame; fine without it
            }
        }
        Render2D.vGradient(ctx, 0, 0, width, height, Colors.fade(0xA0050510, a), Colors.fade(0xD0040409, a));
        // soft accent light in the corners
        Render2D.vGradient(ctx, 0, 0, width, height / 3, Colors.fade(Colors.withAlpha(Theme.accent(), 0x22), a), 0);
        if (g == null || g.starfield.get()) drawStars(ctx, a);
    }

    private void drawStars(DrawContext ctx, float alpha) {
        float t = seconds();
        for (int i = 0; i < STARS; i++) {
            float x = ((SX[i] + t * SPEED[i]) % 1f) * width;
            float y = SY[i] * height;
            float tw = 0.5f + 0.5f * (float) Math.sin(t * 1.6f + PHASE[i]);
            int a = (int) ((40 + 170 * tw) * alpha);
            int c = TINTED[i] ? Theme.gradient(SY[i]) : 0xFFFFFFFF;
            int s = SPEED[i] > 0.013f ? 2 : 1;
            ctx.fill((int) x, (int) y, (int) x + s, (int) y + s, Colors.withAlpha(c, a));
        }
    }

    // ------------------------------------------------------------------ frame

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ui.beginFrame(ctx, textRenderer, mouseX, mouseY, width, height);
        layout();

        // open / close animation
        if (Theme.animations()) {
            open = MathHelper.clamp(open + (closing ? -ui.delta() * 6f : ui.delta() * 4f), 0, 1);
            if (closing && open <= 0) {
                super.close();
                return;
            }
        } else open = closing ? 0 : 1;
        float scale = closing ? 0.92f + 0.08f * outCubic(open) : 0.9f + 0.1f * outBack(open);
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        float pivotX = winX + winW / 2f, pivotY = winY + winH / 2f;
        m.translate(pivotX, pivotY + (1 - outCubic(open)) * 14);
        m.scale(scale, scale);
        m.translate(-pivotX, -pivotY);

        // window
        Render2D.shadow(ctx, winX, winY, winW, winH, 12, 14, 0xC0);
        Render2D.glow(ctx, winX, winY, winW, winH, 12, 6, Theme.accent(), 0.25f);
        Render2D.roundedRect(ctx, winX, winY, winW, winH, 12, Theme.BG);
        Render2D.roundedOutline(ctx, winX, winY, winW, winH, 12, Theme.BORDER);
        Render2D.hGradient(ctx, winX + 18, winY, winW - 36, 1, Colors.fade(Theme.accent(), 0.9f), Colors.fade(Theme.accent2(), 0.9f));

        renderSidebar();
        renderTopBar();
        switch (page) {
            case MODULES -> renderModules();
            case WAYPOINTS -> {
                Render2D.roundedRect(ctx, gridX, gridY, gridW, gridH, 8, Theme.PANEL);
                waypointsPanel.render(ui, gridX + 10, gridY + 10, gridW - 20, gridH - 20);
            }
            case PROFILES -> {
                Render2D.roundedRect(ctx, gridX, gridY, gridW, gridH, 8, Theme.PANEL);
                profilesPanel.render(ui, gridX + 10, gridY + 10, gridW - 20, gridH - 20);
            }
        }
        renderFooter();
        if (page == Page.MODULES) renderDrawer();

        m.popMatrix();
        ui.endFrame();
    }

    // ------------------------------------------------------------------ sidebar

    private static String icon(Category c) {
        return switch (c) {
            case MOVEMENT -> "»";
            case VISUALS -> "◉";
            case WORLD -> "◆";
            case COMBAT -> "⚔";
            case UTILITY -> "⚙";
            case HUD -> "▣";
            case MISC -> "✦";
        };
    }

    private void renderSidebar() {
        DrawContext ctx = ui.ctx;
        Render2D.roundedRect(ctx, winX, winY, SIDEBAR, winH, 12, Theme.SIDEBAR);
        ctx.fill(winX + SIDEBAR - 12, winY, winX + SIDEBAR, winY + winH, Theme.SIDEBAR);
        ctx.fill(winX + SIDEBAR, winY + 10, winX + SIDEBAR + 1, winY + winH - 10, Theme.SEPARATOR);

        // logo (drag the window by it)
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(winX + 16, winY + 14);
        m.scale(2f, 2f);
        Render2D.gradientText(ctx, ui.tr, "VOID", 0, 0, Theme.accent(), Theme.accent2());
        m.popMatrix();
        ui.text("client", winX + 20 + ui.width("VOID") * 2, winY + 22, Theme.TEXT_MUTED);
        Widgets.chip(ui, winX + 16, winY + 34, MyTestClient.VERSION, Colors.withAlpha(Theme.accent(), 0x30), Theme.TEXT_DIM);
        ui.click(winX, winY, SIDEBAR, 44, (b, mx, my) -> {
            int startX = offsetX, startY = offsetY;
            ui.drag = (dx, dy) -> {
                offsetX = startX + (int) (dx - mx);
                offsetY = startY + (int) (dy - my);
            };
        });

        // categories with a sliding selection pill
        int y0 = winY + 54;
        int index = 0, selIndex = -1;
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            if (page == Page.MODULES && category == cats[i] && search.getText().isEmpty()) selIndex = i;
        }
        if (selIndex >= 0) {
            float sy = ui.anim("sel-y", y0 + selIndex * 22, 16f);
            int py = Math.round(sy);
            Render2D.glow(ctx, winX + 8, py, SIDEBAR - 16, 20, 6, 3, Theme.accent(), 0.35f);
            Render2D.roundedRect(ctx, winX + 8, py, SIDEBAR - 16, 20, 6, Colors.withAlpha(Theme.accent(), 0x2E));
            Render2D.vGradient(ctx, winX + 8, py + 4, 2, 12, Theme.accent(), Theme.accent2());
        }
        for (Category c : cats) {
            List<Module> mods = MyTestClient.get().modules().byCategory(c);
            int on = 0;
            for (Module mod : mods) if (mod.isEnabled()) on++;
            boolean active = index == selIndex;
            sidebarItem(icon(c), c.getDisplayName(), on > 0 ? Integer.toString(on) : "", active, y0 + index * 22, () -> {
                page = Page.MODULES;
                category = c;
                search.setText("");
                moduleScroll.reset();
                drawerOpen = false;
            });
            index++;
        }

        int y = y0 + cats.length * 22 + 6;
        ctx.fill(winX + 14, y, winX + SIDEBAR - 14, y + 1, Theme.SEPARATOR);
        ui.text("TOOLS", winX + 16, y + 6, Theme.TEXT_MUTED);
        y += 18;
        sidebarItem("⚑", "Waypoints", "", page == Page.WAYPOINTS, y, () -> page = Page.WAYPOINTS);
        y += 22;
        sidebarItem("☰", "Profiles", "", page == Page.PROFILES, y, () -> page = Page.PROFILES);
        y += 22;
        sidebarItem("▦", "Seed Map", "", false, y, () -> client.setScreen(new SeedMapScreen(null)));
        y += 22;
        sidebarItem("✎", "HUD Editor", "", false, y, () -> client.setScreen(new HudEditorScreen(this)));

        // Safe Mode card at the bottom
        ClickGuiModule g = gui();
        int by = winY + winH - 36, bx = winX + 8, bw = SIDEBAR - 16;
        boolean safe = SafeMode.isOn();
        float st = ui.anim("safe-card", safe ? 1 : 0, 10f);
        Render2D.roundedRect(ctx, bx, by, bw, 28, 7, Colors.lerp(Theme.CARD, Colors.shade(Theme.GOOD, -0.75f), st));
        Render2D.roundedOutline(ctx, bx, by, bw, 28, 7, Colors.lerp(Theme.BORDER, Colors.withAlpha(Theme.GOOD, 0x60), st));
        ui.text(safe ? "Safe Mode" : "Safe Mode off", bx + 8, by + 6, safe ? Theme.TEXT : Theme.WARN);
        ui.text(safe ? "red ⚠ blocked online" : "everything allowed", bx + 8, by + 16, Theme.TEXT_MUTED);
        if (g != null) {
            Widgets.toggle(ui, bx + bw - Widgets.SWITCH_W - 6, by + 8, safe, g.safeMode);
            ui.click(bx, by, bw, 28, (b, mx, my) -> g.safeMode.toggle());
        }
        if (ui.hover(bx, by, bw, 28)) {
            ui.tooltip("Safe Mode", "Blocks modules with a red ⚠ (detectable by servers) while on multiplayer.");
        }
    }

    private void sidebarItem(String icon, String label, String badge, boolean active, int y, Runnable action) {
        int x = winX + 8, w = SIDEBAR - 16, h = 20;
        boolean hover = ui.hover(x, y, w, h);
        float ht = ui.anim("side-h:" + label, hover && !active ? 1f : 0f, 14f);
        if (ht > 0.01f) Render2D.roundedRect(ui.ctx, x, y, w, h, 6, Colors.withAlpha(0xFFFFFF, (int) (0x10 * ht)));
        float at = ui.anim("side-a:" + label, active ? 1f : 0f, 12f);
        int shift = Math.round(at * 3 + ht * 2);
        ui.text(icon, x + 9 + shift, y + 6, active ? Theme.gradient(0.3f) : Colors.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, ht));
        ui.text(label, x + 23 + shift, y + 6, active ? 0xFFFFFFFF : Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, ht));
        if (!badge.isEmpty()) {
            int bw = ui.width(badge) + 8;
            Render2D.roundedRect(ui.ctx, x + w - bw - 4, y + 4, bw, 12, 6, Colors.withAlpha(Theme.accent(), active ? 0x90 : 0x45));
            ui.text(badge, x + w - bw, y + 6, 0xFFFFFFFF);
        }
        ui.click(x, y, w, h, (b, mx, my) -> action.run());
    }

    // ------------------------------------------------------------------ top bar + footer

    private void renderTopBar() {
        DrawContext ctx = ui.ctx;
        String title = switch (page) {
            case WAYPOINTS -> "Waypoints";
            case PROFILES -> "Profiles";
            case MODULES -> search.getText().isEmpty() ? category.getDisplayName() : "Search";
        };
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx + PAD, winY + 12);
        m.scale(1.5f, 1.5f);
        ctx.drawText(ui.tr, title, 0, 0, 0xFFFFFFFF, false);
        m.popMatrix();
        if (page == Page.MODULES) {
            List<Module> mods = visibleModules();
            int on = 0;
            for (Module mod : mods) if (mod.isEnabled()) on++;
            ui.text(mods.size() + " modules  •  " + on + " enabled", cx + PAD, winY + 30, Theme.TEXT_MUTED);
        }
        // search
        int sw = Math.min(200, cw / 3);
        int sx = cx + cw - PAD - sw, sy = winY + 14;
        boolean focused = ui.focused == search;
        float ft = ui.anim("search-focus", focused ? 1 : 0, 14f);
        if (ft > 0.01f) Render2D.glow(ctx, sx, sy, sw, 18, 6, 3, Theme.accent(), 0.5f * ft);
        search.render(ui, sx, sy, sw, 18);
        int lineW = Math.round(sw * ft);
        if (lineW > 0) Render2D.hGradient(ctx, sx + (sw - lineW) / 2, sy + 17, lineW, 1, Theme.accent(), Theme.accent2());
    }

    private void renderFooter() {
        String hint = switch (page) {
            case MODULES -> "Left click: settings   •   Right click: toggle   •   Ctrl+F: search   •   Esc: close";
            case WAYPOINTS -> "Waypoints of this world   •   \\wp add <name> in chat";
            case PROFILES -> "Presets of all module settings";
        };
        ui.text(ui.ellipsize(hint, cw - PAD * 2), cx + PAD, winY + winH - 15, Theme.TEXT_MUTED);
    }

    // ------------------------------------------------------------------ modules page

    private List<Module> visibleModules() {
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return MyTestClient.get().modules().byCategory(category);
        List<Module> result = new ArrayList<>();
        for (Module mod : MyTestClient.get().modules().all()) {
            if (mod.getName().toLowerCase(Locale.ROOT).contains(q) || mod.getDescription().toLowerCase(Locale.ROOT).contains(q)) {
                result.add(mod);
            }
        }
        return result;
    }

    private void openDrawer(Module module) {
        if (drawerModule != module || settingsPanel == null) {
            drawerModule = module;
            settingsPanel = new SettingsPanel(module);
            settingsScroll.reset();
        }
        drawerOpen = true;
    }

    private void renderModules() {
        List<Module> modules = visibleModules();
        String key = category + "|" + search.getText();
        if (!key.equals(lastListKey)) {
            lastListKey = key;
            switchTime = System.nanoTime();
        }

        int cols = Math.max(1, Math.min(3, (gridW + GAP) / (190 + GAP)));
        int cardW = (gridW - GAP * (cols - 1) - 6) / cols;
        int rows = (modules.size() + cols - 1) / cols;
        int contentH = rows * (CARD_H + GAP);
        moduleScroll.setBounds(contentH, gridH);
        int offset = moduleScroll.update(ui.delta());

        ui.clip(gridX - 6, gridY, gridX + gridW + 6, gridY + gridH);
        for (int i = 0; i < modules.size(); i++) {
            int col = i % cols, row = i / cols;
            int x = gridX + col * (cardW + GAP);
            int y = gridY + 4 + row * (CARD_H + GAP) - offset;
            if (y + CARD_H < gridY || y > gridY + gridH) continue;
            renderCard(modules.get(i), x, y, cardW, i);
        }
        if (modules.isEmpty()) {
            String msg = "Nothing matches \"" + search.getText() + "\"";
            ui.text(msg, gridX + (gridW - ui.width(msg)) / 2, gridY + 40, Theme.TEXT_MUTED);
        }
        ui.unclip();
        Widgets.scrollbar(ui, gridX + gridW + 2, gridY + 4, gridH - 8, contentH, offset);
    }

    private float entrance(int index) {
        if (!Theme.animations()) return 1;
        float t = (System.nanoTime() - switchTime) / 1_000_000_000f - index * 0.022f;
        return outCubic(t / 0.28f);
    }

    private void renderCard(Module mod, int x, int y0, int w, int index) {
        float appear = entrance(index);
        if (appear <= 0.01f) return;
        int y = y0 + Math.round((1 - appear) * 12);
        boolean hover = ui.hover(x, y, w, CARD_H) && !drawerOpen;
        float ht = ui.anim("card-h:" + mod.getName(), hover ? 1f : 0f, 14f);
        float on = ui.anim("card-on:" + mod.getName(), mod.isEnabled() ? 1f : 0f, 10f);
        boolean selected = drawerOpen && drawerModule == mod;
        y -= Math.round(ht);

        if (on > 0.01f) Render2D.glow(ui.ctx, x, y, w, CARD_H, 9, 4, Theme.accent(), 0.30f * on * appear);
        int bg = Colors.lerp(Colors.lerp(Theme.CARD, Theme.CARD_HOVER, ht), Colors.shade(Theme.accent(), -0.78f), on * 0.55f);
        Render2D.roundedRect(ui.ctx, x, y, w, CARD_H, 9, Colors.fade(bg, appear));
        int border = selected ? Theme.accent() : Colors.lerp(Theme.BORDER, Colors.withAlpha(Theme.accent(), 0x80), Math.max(ht, on * 0.4f));
        Render2D.roundedOutline(ui.ctx, x, y, w, CARD_H, 9, border);
        if (on > 0.01f) {
            int barH = Math.round((CARD_H - 18) * on);
            Render2D.vGradient(ui.ctx, x + 1, y + (CARD_H - barH) / 2, 2, barH, Theme.accent(), Theme.accent2());
        }

        ui.click(x, y, w, CARD_H, (b, mx, my) -> {
            if (b == 1) mod.toggle();
            else openDrawer(mod);
        });
        if (hover) ui.tooltip(mod.getDescription(), "Left click: settings  •  Right click: toggle");

        int textX = x + 12;
        String name = ui.ellipsize(mod.getName(), w - 60);
        ui.text(name, textX, y + 9, Colors.lerp(Theme.TEXT_DIM, 0xFFFFFFFF, Math.max(on, ht * 0.6f)));
        Widgets.warningIcon(ui, mod, textX + ui.width(name) + 4, y + 9);
        ui.text(ui.ellipsize(mod.getDescription(), w - 24), textX, y + 21, Theme.TEXT_MUTED);

        // bottom row: keybind / action chip
        int chipX = textX;
        if (mod.isAction()) chipX += Widgets.chip(ui, chipX, y + 32, "▶ action", Colors.withAlpha(Theme.accent2(), 0x30), Theme.TEXT_DIM) + 4;
        if (mod.getKey() > 0) Widgets.chip(ui, chipX, y + 32, "⌨ " + KeyNames.name(mod.getKey()), Theme.FIELD, Theme.TEXT_DIM);

        int right = x + w - 10;
        if (mod.isAction()) {
            Widgets.button(ui, right - 34, y + 7, 34, 14, "Run", true, mod::toggle);
        } else {
            Widgets.toggle(ui, right - Widgets.SWITCH_W, y + 8, mod.isEnabled(), mod);
            ui.click(right - Widgets.SWITCH_W - 3, y + 4, Widgets.SWITCH_W + 6, 20, (b, mx, my) -> mod.toggle());
        }
    }

    // ------------------------------------------------------------------ settings drawer

    private void renderDrawer() {
        float t = ui.anim("drawer", drawerOpen ? 1f : 0f, 14f);
        if (!Theme.animations()) t = drawerOpen ? 1 : 0;
        if (t <= 0.01f || settingsPanel == null) return;
        DrawContext ctx = ui.ctx;
        float e = outCubic(t);
        int contentTop = winY + 1, contentBottom = winY + winH - 1;

        // dim the grid; clicking it closes the drawer
        ui.clip(cx, contentTop, cx + cw - 1, contentBottom);
        ctx.fill(cx, contentTop, cx + cw, contentBottom, Colors.withAlpha(0x05050A, (int) (0x90 * e)));
        if (drawerOpen) ui.click(cx, contentTop, cw, contentBottom - contentTop, (b, mx, my) -> drawerOpen = false);

        drawerX = winX + winW - Math.round(drawerW * e) - 6;
        int dy = winY + 8, dh = winH - 16;
        Render2D.shadow(ctx, drawerX, dy, drawerW, dh, 10, 10, (int) (0xB0 * e));
        Render2D.roundedRect(ctx, drawerX, dy, drawerW, dh, 10, Theme.PANEL);
        Render2D.roundedOutline(ctx, drawerX, dy, drawerW, dh, 10, Theme.BORDER);
        Render2D.hGradient(ctx, drawerX + 12, dy, drawerW - 24, 1, Theme.accent(), Theme.accent2());
        ui.click(drawerX, dy, drawerW, dh, (b, mx, my) -> {
        }); // swallow clicks on empty drawer space

        // header
        ui.text("⚙  SETTINGS", drawerX + 14, dy + 10, Theme.TEXT_MUTED);
        int closeX = drawerX + drawerW - 24, closeY = dy + 6;
        boolean ch = ui.hover(closeX, closeY, 16, 16);
        Render2D.roundedRect(ctx, closeX, closeY, 16, 16, 5, ch ? Colors.withAlpha(Theme.BAD, 0x60) : Theme.BUTTON);
        ui.text("✕", closeX + 5, closeY + 4, ch ? 0xFFFFFFFF : Theme.TEXT_DIM);
        ui.click(closeX, closeY, 16, 16, (b, mx, my) -> drawerOpen = false);

        int sx = drawerX + 12, sy = dy + 28, sw = drawerW - 28, sh = dh - 36;
        int sOffset = settingsScroll.update(ui.delta());
        ui.clip(drawerX, sy, drawerX + drawerW, sy + sh);
        int contentH = settingsPanel.render(ui, sx, sy - sOffset, sw);
        ui.unclip();
        settingsScroll.setBounds(contentH, sh);
        Widgets.scrollbar(ui, drawerX + drawerW - 8, sy, sh, contentH, sOffset);
    }

    // ------------------------------------------------------------------ input

    private boolean interactive() {
        return !closing && open >= 0.98f;
    }

    @Override
    protected boolean onMouseClick(double mouseX, double mouseY, int button) {
        if (!interactive()) return true;
        if (settingsPanel != null && settingsPanel.isListening()) {
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
                if (drawerOpen && mouseX >= drawerX) settingsScroll.scroll(amount);
                else if (!drawerOpen) moduleScroll.scroll(amount);
            }
            case WAYPOINTS -> waypointsPanel.scroll(mouseX, mouseY, amount);
            case PROFILES -> profilesPanel.scroll(mouseX, mouseY, amount);
        }
        return true;
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
        if (key == GLFW.GLFW_KEY_ESCAPE && drawerOpen) {
            drawerOpen = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_F && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            page = Page.MODULES;
            drawerOpen = false;
            ui.focused = search;
            return true;
        }
        ClickGuiModule g = gui();
        if (g != null && key == g.getKey()) {
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

    /** Plays the closing animation first. */
    @Override
    public void close() {
        if (Theme.animations() && !closing && open > 0) {
            closing = true;
            return;
        }
        super.close();
    }

    @Override
    public void removed() {
        MyTestClient.get().config().save();
        super.removed();
    }
}
