package com.voidclient.module.movement;

import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.Risk;
import com.voidclient.module.setting.NumberSetting;
import com.voidclient.util.Notifications;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Elytra Replace: when the elytra you are wearing is about to break, swaps in the best spare
 * one from your inventory, even in mid-flight, so you never fall out of the sky.
 */
public class ElytraReplaceModule extends Module {
    private final NumberSetting threshold = add(new NumberSetting("Swap Below", "Swap when the worn elytra has this much durability left.", 8, 2, 100, 1));
    private int cooldown;

    public ElytraReplaceModule() {
        super("Elytra Replace", "Swaps a nearly broken elytra for a spare one, even while flying.", Category.MOVEMENT);
        setRisk(Risk.DETECTABLE, "Moves items in your inventory without opening it.");
    }

    @Override
    public void onTick() {
        if (cooldown-- > 0 || mc.currentScreen != null) return;
        ItemStack worn = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!worn.isOf(Items.ELYTRA) || worn.getMaxDamage() - worn.getDamage() > threshold.getInt()) return;
        int best = -1, bestLeft = threshold.getInt();
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            int left = s.getMaxDamage() - s.getDamage();
            if (s.isOf(Items.ELYTRA) && left > bestLeft) {
                bestLeft = left;
                best = i;
            }
        }
        if (best < 0) return;
        int slot = best < 9 ? best + 36 : best; // screen handler slot
        int sync = mc.player.playerScreenHandler.syncId;
        mc.interactionManager.clickSlot(sync, slot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(sync, 6, 0, SlotActionType.PICKUP, mc.player); // chest armour slot
        mc.interactionManager.clickSlot(sync, slot, 0, SlotActionType.PICKUP, mc.player);
        cooldown = 20;
        Notifications.info("Elytra swapped (" + bestLeft + " durability)");
    }
}
