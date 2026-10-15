package com.mytestclient.gui;

import com.mytestclient.MyTestClient;
import com.mytestclient.hud.HudModule;
import com.mytestclient.module.Module;
import com.mytestclient.module.Modules;
import com.mytestclient.render.Colors;
import com.mytestclient.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * HUD editor: drag enabled HUD elements to move them (they snap to screen edges and centre).
 * Right click an element to toggle its background. Esc returns to the previous screen.
 */
public class HudEditorScreen extends BaseScreen {
    private static final int SNAP = 4;
    private final Screen parent;
    private final Ui ui = new Ui();
    private HudModule dragging;

    public HudEditorScreen(Screen parent) {
        super(Text.literal("HUD Editor"));
        this.parent = parent;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x30000000);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ui.beginFrame(ctx, textRenderer, mouseX, mouseY, width, height);

        // centre guides
        ctx.fill(width / 2, 0, width / 2 + 1, height, 0x18FFFFFF);
        ctx.fill(0, height / 2, width, height / 2 + 1, 0x18FFFFFF);

        boolean any = false;
        for (Module m : Modules.all()) {
            if (!(m instanceof HudModule hud) || !hud.isEnabled()) continue;
            any = true;
            int x = hud.getX(), y = hud.getY(), w = hud.getWidth(), h = hud.getHeight();
            boolean hover = ui.hover(x, y, w, h) || dragging == hud;
            Render2D.rect(ctx, x, y, w, h, hover ? Colors.withAlpha(Theme.accent(), 0x30) : 0x10FFFFFF);
            Render2D.outline(ctx, x - 1, y - 1, w + 2, h + 2, hover ? Theme.accent() : 0x60FFFFFF);
            if (hover) {
                String label = hud.getName();
                int ly = y > 12 ? y - 11 : y + h + 2;
                Render2D.roundedRect(ctx, x - 1, ly - 1, ui.width(label) + 6, 11, 3, Theme.accent());
                ui.text(label, x + 2, ly + 1, 0xFFFFFFFF);
            }
            ui.click(x, y, w, h, (b, mx, my) -> {
                if (b == 1) {
                    hud.background.toggle();
                    return;
                }
                dragging = hud;
                int offX = (int) mx - x, offY = (int) my - y;
                ui.drag = (dx, dy) -> move(hud, (int) dx - offX, (int) dy - offY);
            });
        }

        String hint = any ? "Drag elements to move • Right click: toggle background • Esc: back"
                : "No HUD elements enabled. Enable some in the HUD category.";
        int hw = ui.width(hint) + 16;
        Render2D.roundedRect(ctx, (width - hw) / 2, 6, hw, 16, 5, Theme.BG);
        ui.text(hint, (width - hw) / 2 + 8, 10, Theme.TEXT);
        ui.endFrame();
    }

    private void move(HudModule hud, int x, int y) {
        int w = hud.getWidth(), h = hud.getHeight();
        if (Math.abs(x) < SNAP) x = 0;
        if (Math.abs(x + w - width) < SNAP) x = width - w;
        if (Math.abs(x + w / 2 - width / 2) < SNAP) x = width / 2 - w / 2;
        if (Math.abs(y) < SNAP) y = 0;
        if (Math.abs(y + h - height) < SNAP) y = height - h;
        if (Math.abs(y + h / 2 - height / 2) < SNAP) y = height / 2 - h / 2;
        hud.setPixelPosition(Math.max(0, Math.min(width - w, x)), Math.max(0, Math.min(height - h, y)));
    }

    @Override
    protected boolean onMouseClick(double mouseX, double mouseY, int button) {
        ui.dispatchClick(mouseX, mouseY, button);
        return true;
    }

    @Override
    protected void onMouseRelease(double mouseX, double mouseY, int button) {
        ui.drag = null;
        dragging = null;
    }

    @Override
    protected void onMouseDrag(double mouseX, double mouseY, int button) {
        if (ui.drag != null) ui.drag.onDrag(mouseX, mouseY);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void removed() {
        MyTestClient.get().config().save();
        super.removed();
    }
}
