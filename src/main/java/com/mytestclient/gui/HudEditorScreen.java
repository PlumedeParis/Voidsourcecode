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

        // centre guides: faint, light up while an element snaps to them
        boolean snapX = false, snapY = false;
        if (dragging != null) {
            snapX = Math.abs(dragging.getX() + dragging.getWidth() / 2 - width / 2) <= 1;
            snapY = Math.abs(dragging.getY() + dragging.getHeight() / 2 - height / 2) <= 1;
        }
        int guide = Colors.withAlpha(Theme.accent(), 0xC0);
        ctx.fill(width / 2, 0, width / 2 + 1, height, snapX ? guide : 0x14FFFFFF);
        ctx.fill(0, height / 2, width, height / 2 + 1, snapY ? guide : 0x14FFFFFF);

        boolean any = false;
        HudModule hovered = null;
        for (Module m : Modules.all()) {
            if (!(m instanceof HudModule hud) || !hud.isEnabled()) continue;
            any = true;
            int x = hud.getX(), y = hud.getY(), w = hud.getWidth(), h = hud.getHeight();
            boolean hover = (dragging == null && ui.hover(x, y, w, h)) || dragging == hud;
            if (hover) hovered = hud;
            float t = ui.anim("hud:" + hud.getName(), hover ? 1f : 0f, 16f);
            Render2D.roundedRect(ctx, x - 1, y - 1, w + 2, h + 2, 3, Colors.lerp(0x0CFFFFFF, Colors.withAlpha(Theme.accent(), 0x2A), t));
            Render2D.roundedOutline(ctx, x - 2, y - 2, w + 4, h + 4, 4, Colors.lerp(0x50FFFFFF, Theme.accent(), t));
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

        // name tag (and position while dragging) of the hovered element, kept on screen
        if (hovered != null) {
            int x = hovered.getX(), y = hovered.getY(), h = hovered.getHeight();
            String label = dragging == hovered
                    ? hovered.getName() + "  " + String.format("%.0f%%, %.0f%%", hovered.posX.get(), hovered.posY.get())
                    : hovered.getName();
            int lw = ui.width(label) + 8;
            int ly = y > 15 ? y - 15 : y + h + 4;
            int lx = Math.max(2, Math.min(width - lw - 2, x - 2));
            Render2D.roundedRect(ctx, lx, ly, lw, 12, 3, Theme.accent());
            ui.text(label, lx + 4, ly + 2, 0xFFFFFFFF);
        }

        // hint pill at the top (moves to the bottom if an element sits under it)
        String hint = any ? "Drag to move  •  Right click: background  •  Esc: back"
                : "No HUD elements enabled: turn some on in the HUD category.";
        hint = ui.ellipsize(hint, width - 24);
        int hw = ui.width(hint) + 16;
        int hx = (width - hw) / 2;
        int hy = hintY(hx, hw, 16);
        Render2D.shadow(ctx, hx, hy, hw, 16, 5, 4, 0x80);
        Render2D.roundedRect(ctx, hx, hy, hw, 16, 5, Theme.BG);
        Render2D.roundedOutline(ctx, hx, hy, hw, 16, 5, Theme.BORDER);
        ui.text(hint, hx + 8, hy + 4, any ? Theme.TEXT_DIM : Theme.WARN);
        ui.endFrame();
    }

    /** First spot (top, a bit lower, middle, above the hotbar) where the hint covers no HUD element. */
    private int hintY(int hx, int hw, int hh) {
        int[] candidates = {6, 24, height / 2 - hh - 6, height - 60, height - hh - 4};
        for (int y : candidates) {
            boolean free = true;
            for (Module m : Modules.all()) {
                if (!(m instanceof HudModule hud) || !hud.isEnabled()) continue;
                if (hud.getX() < hx + hw && hud.getX() + hud.getWidth() > hx && hud.getY() < y + hh + 2 && hud.getY() + hud.getHeight() > y - 2) {
                    free = false;
                    break;
                }
            }
            if (free) return y;
        }
        return 6;
    }

    /** Moves an element, snapping it to the screen edges and centre lines. */
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
