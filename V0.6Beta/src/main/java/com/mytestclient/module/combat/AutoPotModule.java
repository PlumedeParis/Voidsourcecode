package com.mytestclient.module.combat;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.NumberSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * Auto Pot: throws a splash potion of healing (or regeneration) at your feet when your health
 * is low. ⚠ DETECTABLE: snaps your view down and back.
 */
public class AutoPotModule extends Module {
    private final NumberSetting health = add(new NumberSetting("Health", "Throw when health is at or below this.", 8, 1, 19, 1));
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks between two potions.", 10, 2, 40, 1));

    private int timer;

    public AutoPotModule() {
        super("Auto Pot", "Throws a healing splash potion at your feet when you are low.", Category.COMBAT);
        setRisk(Risk.DETECTABLE, "Automated item use with a forced look-down.");
    }

    @Override
    public void onTick() {
        if (timer-- > 0 || mc.currentScreen != null) return;
        if (mc.player.getHealth() > health.get()) return;
        int slot = -1;
        for (int i = 0; i < 9 && slot < 0; i++) if (isHealing(mc.player.getInventory().getStack(i))) slot = i;
        if (slot < 0) return;
        int previous = mc.player.getInventory().getSelectedSlot();
        float pitch = mc.player.getPitch();
        mc.player.getInventory().setSelectedSlot(slot);
        mc.player.setPitch(90);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.setPitch(pitch);
        mc.player.getInventory().setSelectedSlot(previous);
        timer = delay.getInt();
    }

    private static boolean isHealing(ItemStack stack) {
        if (!stack.isOf(Items.SPLASH_POTION)) return false;
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) return false;
        for (StatusEffectInstance e : contents.getEffects()) {
            if (e.getEffectType() == StatusEffects.INSTANT_HEALTH || e.getEffectType() == StatusEffects.REGENERATION) return true;
        }
        return false;
    }
}
