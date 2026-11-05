package com.mytestclient.module.hud;

import com.mytestclient.gui.Theme;
import com.mytestclient.hud.HudModule;
import com.mytestclient.module.Category;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.render.Colors;
import com.mytestclient.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;

/** Keystrokes HUD: shows WASD, mouse buttons and space, lighting up when pressed. */
public class KeystrokesHud extends HudModule {
    private final BooleanSetting mouse = add(new BooleanSetting("Mouse", "Show left/right mouse buttons.", true));
    private final BooleanSetting space = add(new BooleanSetting("Space", "Show the jump key.", true));

    private static final int KEY = 20, GAP = 2;

    public KeystrokesHud() {
        super("Keystrokes", "Displays your movement keys and mouse buttons.", Category.HUD, 0, 40, false);
    }

    @Override
    protected void render(DrawContext ctx, int x, int y, float tickDelta) {
        int full = KEY * 3 + GAP * 2;
        key(ctx, x + KEY + GAP, y, KEY, "W", mc.options.forwardKey);
        key(ctx, x, y + KEY + GAP, KEY, "A", mc.options.leftKey);
        key(ctx, x + KEY + GAP, y + KEY + GAP, KEY, "S", mc.options.backKey);
        key(ctx, x + (KEY + GAP) * 2, y + KEY + GAP, KEY, "D", mc.options.rightKey);
        int cy = y + (KEY + GAP) * 2;
        if (mouse.get()) {
            int w = (full - GAP) / 2;
            key(ctx, x, cy, w, "LMB", mc.options.attackKey);
            key(ctx, x + w + GAP, cy, w, "RMB", mc.options.useKey);
            cy += KEY + GAP;
        }
        if (space.get()) {
            keyBox(ctx, x, cy, full, 12, "——", mc.options.jumpKey.isPressed());
            cy += 12 + GAP;
        }
        setSize(full, cy - y - GAP);
    }

    private void key(DrawContext ctx, int x, int y, int w, String label, KeyBinding binding) {
        keyBox(ctx, x, y, w, KEY, label, binding.isPressed());
    }

    private void keyBox(DrawContext ctx, int x, int y, int w, int h, String label, boolean pressed) {
        Render2D.roundedRect(ctx, x, y, w, h, 3, pressed ? Colors.withAlpha(Theme.accent(), 0xD0) : Theme.HUD_BG);
        int tw = mc.textRenderer.getWidth(label);
        ctx.drawText(mc.textRenderer, label, x + (w - tw) / 2, y + (h - 8) / 2, pressed ? 0xFFFFFFFF : Theme.TEXT_DIM, true);
    }
}
