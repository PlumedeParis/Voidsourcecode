package com.mytestclient.module.combat;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.util.CombatUtil;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Auto Armor: puts on the best armour from your inventory (netherite > diamond > iron > ...),
 * keeping an elytra if you wear one. ⚠ DETECTABLE: automatic inventory clicks.
 */
public class AutoArmorModule extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks between two pieces.", 3, 0, 20, 1));

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private int timer;

    public AutoArmorModule() {
        super("Auto Armor", "Wears the best armour in your inventory automatically.", Category.COMBAT);
        setRisk(Risk.DETECTABLE, "Automatic inventory clicks can be detected and are banned on many PvP servers.");
    }

    @Override
    public void onTick() {
        if (timer-- > 0) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (!mc.player.playerScreenHandler.getCursorStack().isEmpty()) return;
        for (int s = 0; s < SLOTS.length; s++) {
            EquipmentSlot slot = SLOTS[s];
            ItemStack worn = mc.player.getEquippedStack(slot);
            if (worn.isOf(Items.ELYTRA)) continue; // don't take your elytra off
            if (net.minecraft.enchantment.EnchantmentHelper.hasAnyEnchantmentsWith(worn,
                    net.minecraft.component.EnchantmentEffectComponentTypes.PREVENT_ARMOR_CHANGE)) continue; // Curse of Binding
            int bestInv = -1, bestScore = score(worn, slot);
            for (int i = 0; i < 36; i++) {
                int sc = score(mc.player.getInventory().getStack(i), slot);
                if (sc > bestScore) {
                    bestScore = sc;
                    bestInv = i;
                }
            }
            if (bestInv < 0) continue;
            int sync = mc.player.playerScreenHandler.syncId;
            int invSlot = bestInv < 9 ? 36 + bestInv : bestInv; // screen slot index
            int armorSlot = 5 + s; // 5 head, 6 chest, 7 legs, 8 feet
            if (worn.isEmpty()) {
                mc.interactionManager.clickSlot(sync, invSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
            } else {
                // pick up new piece, swap it with the worn one, put the old one where the new one was
                mc.interactionManager.clickSlot(sync, invSlot, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(sync, armorSlot, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(sync, invSlot, 0, SlotActionType.PICKUP, mc.player);
            }
            timer = delay.getInt();
            return;
        }
    }

    /** 0 = can't go in this slot. */
    private static int score(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) return 0;
        EquippableComponent eq = stack.get(DataComponentTypes.EQUIPPABLE);
        if (eq == null || eq.slot() != slot || stack.isOf(Items.ELYTRA)) return 0;
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        if (!(path.endsWith("_helmet") || path.endsWith("_chestplate") || path.endsWith("_leggings") || path.endsWith("_boots"))) return 0;
        int damageLeft = stack.isDamageable() ? (stack.getMaxDamage() - stack.getDamage()) * 10 / Math.max(1, stack.getMaxDamage()) : 10;
        return CombatUtil.tier(path) * 100 + (stack.hasEnchantments() ? 20 : 0) + damageLeft;
    }
}
