package com.mytestclient.module.utility;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Auto Eat: eats food from your hotbar when your hunger gets low. */
public class AutoEatModule extends Module {
    private final NumberSetting hunger = add(new NumberSetting("Hunger", "Eat when the hunger bar is at or below this (20 = full).", 14, 1, 19, 1));
    private final BooleanSetting avoidBad = add(new BooleanSetting("Avoid Bad Food", "Skip rotten flesh, spider eyes, poisonous potatoes, pufferfish and chorus fruit.", true));

    private int previousSlot = -1;
    private boolean eating;

    public AutoEatModule() {
        super("Auto Eat", "Automatically eats from your hotbar when you get hungry.", Category.UTILITY);
    }

    @Override
    public void onTick() {
        if (mc.currentScreen != null) {
            stop();
            return;
        }
        int food = mc.player.getHungerManager().getFoodLevel();
        if (eating) {
            boolean holdingFood = isFood(mc.player.getMainHandStack());
            if (food > hunger.getInt() || !holdingFood) stop();
            else mc.options.useKey.setPressed(true);
            return;
        }
        if (food > hunger.getInt()) return;
        int slot = findFood();
        if (slot < 0) return;
        previousSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        mc.options.useKey.setPressed(true);
        eating = true;
    }

    private void stop() {
        if (!eating) return;
        eating = false;
        mc.options.useKey.setPressed(false);
        KeyBinding.updatePressedStates();
        if (previousSlot >= 0) mc.player.getInventory().setSelectedSlot(previousSlot);
        previousSlot = -1;
    }

    private int findFood() {
        for (int i = 0; i < 9; i++) if (isFood(mc.player.getInventory().getStack(i))) return i;
        return -1;
    }

    private boolean isFood(ItemStack stack) {
        if (stack.isEmpty() || stack.get(DataComponentTypes.FOOD) == null) return false;
        if (!avoidBad.get()) return true;
        return !stack.isOf(Items.ROTTEN_FLESH) && !stack.isOf(Items.SPIDER_EYE) && !stack.isOf(Items.POISONOUS_POTATO)
                && !stack.isOf(Items.PUFFERFISH) && !stack.isOf(Items.CHORUS_FRUIT);
    }

    @Override
    protected void onDisable() {
        stop();
    }
}
