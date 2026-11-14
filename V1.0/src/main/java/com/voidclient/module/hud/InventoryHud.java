package com.voidclient.module.hud;

import com.voidclient.hud.HudModule;
import com.voidclient.module.Category;
import com.voidclient.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/** Inventory HUD: shows your main inventory (the 27 slots above the hotbar) on screen. */
public class InventoryHud extends HudModule {
    public InventoryHud() {
        super("Inventory", "Shows the contents of your inventory on screen.", Category.HUD, 100, 100);
    }

    @Override
    protected void render(DrawContext ctx, int x, int y, float tickDelta) {
        for (int i = 0; i < 27; i++) {
            int sx = x + 3 + (i % 9) * 18, sy = y + 3 + (i / 9) * 18;
            Render2D.roundedRect(ctx, sx, sy, 17, 17, 2, 0x30FFFFFF);
            ItemStack stack = mc.player.getInventory().getStack(9 + i);
            if (stack.isEmpty()) continue;
            ctx.drawItem(stack, sx, sy);
            ctx.drawStackOverlay(mc.textRenderer, stack, sx, sy);
        }
        setSize(9 * 18 + 5, 3 * 18 + 5);
    }
}
