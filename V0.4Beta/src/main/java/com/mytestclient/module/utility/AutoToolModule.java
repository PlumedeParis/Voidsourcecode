package com.mytestclient.module.utility;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/** Auto Tool: while mining, switches to the fastest tool in your hotbar, then switches back. */
public class AutoToolModule extends Module {
    private final BooleanSetting switchBack = add(new BooleanSetting("Switch Back", "Return to the previous slot when you stop mining.", true));
    private final NumberSetting protect = add(new NumberSetting("Protect Below", "Don't use tools with fewer uses left than this (0 = off).", 5, 0, 50, 1));

    private int previousSlot = -1;

    public AutoToolModule() {
        super("Auto Tool", "Automatically picks the best tool from your hotbar when mining.", Category.UTILITY);
    }

    @Override
    public void onTick() {
        boolean mining = mc.interactionManager.isBreakingBlock() && mc.options.attackKey.isPressed();
        if (!mining || !(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            if (previousSlot >= 0 && switchBack.get()) mc.player.getInventory().setSelectedSlot(previousSlot);
            previousSlot = -1;
            return;
        }
        BlockState state = mc.world.getBlockState(hit.getBlockPos());
        int current = mc.player.getInventory().getSelectedSlot();
        int best = current;
        float bestSpeed = speed(mc.player.getInventory().getStack(current), state);
        for (int i = 0; i < 9; i++) {
            float s = speed(mc.player.getInventory().getStack(i), state);
            if (s > bestSpeed) {
                bestSpeed = s;
                best = i;
            }
        }
        if (best != current) {
            if (previousSlot < 0) previousSlot = current;
            mc.player.getInventory().setSelectedSlot(best);
        }
    }

    private float speed(ItemStack stack, BlockState state) {
        if (stack.isEmpty()) return 1f;
        if (protect.getInt() > 0 && stack.isDamageable() && stack.getMaxDamage() - stack.getDamage() <= protect.getInt()) return 0f;
        return stack.getMiningSpeedMultiplier(state);
    }
}
