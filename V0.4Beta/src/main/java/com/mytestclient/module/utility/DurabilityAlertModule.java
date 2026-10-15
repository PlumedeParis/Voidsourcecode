package com.mytestclient.module.utility;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.util.Notifications;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Durability Alert: warns you when your held tool or armour is about to break. */
public class DurabilityAlertModule extends Module {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private final NumberSetting threshold = add(new NumberSetting("Threshold", "Warn below this durability (%).", 10, 1, 50, 1));

    private final Set<ItemStack> warned = Collections.newSetFromMap(new IdentityHashMap<>());
    private int timer;

    public DurabilityAlertModule() {
        super("Durability Alert", "Warns you before your tools or armour break.", Category.UTILITY);
    }

    @Override
    public void onTick() {
        if (timer-- > 0) return;
        timer = 20;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = mc.player.getEquippedStack(slot);
            if (stack.isEmpty() || !stack.isDamageable()) continue;
            double left = 100.0 * (stack.getMaxDamage() - stack.getDamage()) / stack.getMaxDamage();
            if (left < threshold.get()) {
                if (warned.add(stack)) Notifications.warn(stack.getName().getString() + " is at " + Math.round(left) + "% durability!");
            } else {
                warned.remove(stack);
            }
        }
    }
}
