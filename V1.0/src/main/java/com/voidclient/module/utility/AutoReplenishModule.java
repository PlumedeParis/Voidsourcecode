package com.voidclient.module.utility;

import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.Risk;
import com.voidclient.module.setting.NumberSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

/** Auto Replenish: when a stack in your hotbar runs low, swaps in a fuller stack of the same item. */
public class AutoReplenishModule extends Module {
    private final NumberSetting threshold = add(new NumberSetting("Refill Below", "Refill a hotbar stack when it has fewer items than this.", 8, 1, 32, 1));
    private int cooldown;

    public AutoReplenishModule() {
        super("Auto Replenish", "Refills hotbar stacks (blocks, food, rockets...) from your inventory.", Category.UTILITY);
        setRisk(Risk.DETECTABLE, "Moves items in your inventory without opening it.");
    }

    @Override
    public void onTick() {
        if (cooldown-- > 0 || mc.currentScreen != null) return;
        for (int h = 0; h < 9; h++) {
            ItemStack hot = mc.player.getInventory().getStack(h);
            if (hot.isEmpty() || !hot.isStackable() || hot.getCount() >= threshold.getInt()) continue;
            int best = -1, bestCount = hot.getCount();
            for (int i = 9; i < 36; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (ItemStack.areItemsAndComponentsEqual(s, hot) && s.getCount() > bestCount) {
                    bestCount = s.getCount();
                    best = i;
                }
            }
            if (best < 0) continue;
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, best, h, SlotActionType.SWAP, mc.player);
            cooldown = 5;
            return;
        }
    }
}
