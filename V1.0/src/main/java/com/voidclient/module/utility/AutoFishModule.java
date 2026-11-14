package com.voidclient.module.utility;

import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.setting.NumberSetting;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * Auto Fish: reels in when a fish bites (the splash sound at your bobber) and casts again.
 * Hold a fishing rod and cast once.
 */
public class AutoFishModule extends Module {
    private final NumberSetting recast = add(new NumberSetting("Recast Delay", "Ticks to wait before casting again.", 12, 4, 60, 1));
    private int reelIn = -1, castIn = -1;

    public AutoFishModule() {
        super("Auto Fish", "Reels in when a fish bites and casts again.", Category.UTILITY);
    }

    /** From the sound hook. */
    public void onSound(String id, double x, double y, double z) {
        if (!id.endsWith("entity.fishing_bobber.splash") || mc.player == null || mc.player.fishHook == null) return;
        if (mc.player.fishHook.squaredDistanceTo(x, y, z) < 4) reelIn = 3;
    }

    private boolean holdingRod() {
        return mc.player.getMainHandStack().isOf(Items.FISHING_ROD);
    }

    @Override
    public void onTick() {
        if (!holdingRod()) {
            reelIn = castIn = -1;
            return;
        }
        if (reelIn >= 0 && reelIn-- == 0) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            castIn = recast.getInt();
        } else if (castIn >= 0 && castIn-- == 0 && mc.player.fishHook == null) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }
}
