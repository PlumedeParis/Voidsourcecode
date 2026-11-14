package com.voidclient.module.utility;

import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.Risk;
import com.voidclient.util.Notifications;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/** Chest Swap (action): one key to switch between your elytra and your chestplate. */
public class ChestSwapModule extends Module {
    public ChestSwapModule() {
        super("Chest Swap", "Action: swaps your elytra and chestplate. Bind it to a key.", Category.UTILITY);
        setRisk(Risk.DETECTABLE, "Moves items in your inventory without opening it.");
    }

    @Override
    public boolean isAction() {
        return true;
    }

    @Override
    protected void onAction() {
        if (mc.player == null) return;
        boolean wearingElytra = mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
        int best = -1, bestScore = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            int score = -1;
            if (wearingElytra) {
                String id = net.minecraft.registry.Registries.ITEM.getId(s.getItem()).getPath();
                if (id.endsWith("_chestplate")) score = id.startsWith("netherite") ? 5 : id.startsWith("diamond") ? 4 : id.startsWith("iron") ? 3 : 1;
            } else if (s.isOf(Items.ELYTRA) && s.getMaxDamage() - s.getDamage() > 1) score = s.getMaxDamage() - s.getDamage();
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        if (best < 0) {
            Notifications.warn(wearingElytra ? "No chestplate in your inventory" : "No usable elytra in your inventory");
            return;
        }
        int slot = best < 9 ? best + 36 : best;
        int sync = mc.player.playerScreenHandler.syncId;
        mc.interactionManager.clickSlot(sync, slot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(sync, 6, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(sync, slot, 0, SlotActionType.PICKUP, mc.player);
        Notifications.info(wearingElytra ? "Chestplate on" : "Elytra on");
    }
}
