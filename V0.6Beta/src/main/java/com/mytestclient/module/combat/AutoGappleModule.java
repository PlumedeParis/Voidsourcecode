package com.mytestclient.module.combat;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.Items;

/** Auto Gapple: eats a golden apple from your hotbar when your health gets low. */
public class AutoGappleModule extends Module {
    private final NumberSetting health = add(new NumberSetting("Health", "Eat when health + absorption is at or below this (20 = full).", 10, 1, 19, 1));
    private final BooleanSetting preferEnchanted = add(new BooleanSetting("Prefer Enchanted", "Use enchanted golden apples first.", true));

    private int previous = -1;
    private boolean eating;

    public AutoGappleModule() {
        super("Auto Gapple", "Eats a golden apple when your health is low.", Category.COMBAT);
    }

    @Override
    public void onTick() {
        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (eating) {
            boolean holding = mc.player.getMainHandStack().isOf(Items.GOLDEN_APPLE) || mc.player.getMainHandStack().isOf(Items.ENCHANTED_GOLDEN_APPLE);
            if (!holding || hp > health.get() + 4 || mc.currentScreen != null) stop();
            else mc.options.useKey.setPressed(true);
            return;
        }
        if (hp > health.get() || mc.currentScreen != null) return;
        int slot = find();
        if (slot < 0) return;
        previous = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        mc.options.useKey.setPressed(true);
        eating = true;
    }

    private int find() {
        int normal = -1;
        for (int i = 0; i < 9; i++) {
            var s = mc.player.getInventory().getStack(i);
            if (s.isOf(Items.ENCHANTED_GOLDEN_APPLE) && preferEnchanted.get()) return i;
            if ((s.isOf(Items.GOLDEN_APPLE) || s.isOf(Items.ENCHANTED_GOLDEN_APPLE)) && normal < 0) normal = i;
        }
        return normal;
    }

    private void stop() {
        eating = false;
        mc.options.useKey.setPressed(false);
        KeyBinding.updatePressedStates();
        if (previous >= 0) mc.player.getInventory().setSelectedSlot(previous);
        previous = -1;
    }

    @Override
    protected void onDisable() {
        if (eating) stop();
    }
}
