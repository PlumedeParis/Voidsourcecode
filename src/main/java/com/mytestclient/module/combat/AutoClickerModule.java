package com.mytestclient.module.combat;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.ModeSetting;
import com.mytestclient.module.setting.NumberSetting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Auto Clicker: while you hold the attack (or use) button, clicks for you at a set rate, with a
 * little randomness. ⚠ DETECTABLE: steady fast clicking is easy to spot.
 */
public class AutoClickerModule extends Module {
    private final ModeSetting button = add(new ModeSetting("Button", "Which button to repeat while held.", "Left", "Left", "Right"));
    private final NumberSetting cps = add(new NumberSetting("CPS", "Clicks per second.", 10, 1, 20, 1));
    private final NumberSetting jitter = add(new NumberSetting("Randomness", "Random variation of the delay, in %.", 25, 0, 60, 5));

    private double timer;

    public AutoClickerModule() {
        super("Auto Clicker", "Clicks for you while you hold the button.", Category.COMBAT);
        setRisk(Risk.DETECTABLE, "Steady fast clicking is easy for anti-cheats to spot.");
    }

    @Override
    public void onTick() {
        if (mc.currentScreen != null) return;
        boolean left = button.is("Left");
        if (!(left ? mc.options.attackKey : mc.options.useKey).isPressed()) {
            timer = 0;
            return;
        }
        timer -= 1;
        // several clicks per tick at high CPS
        while (timer <= 0) {
            double base = 20.0 / cps.get();
            timer += base * (1 + (Math.random() * 2 - 1) * jitter.get() / 100.0);
            if (left) {
                HitResult hit = mc.crosshairTarget;
                if (hit instanceof EntityHitResult e) mc.interactionManager.attackEntity(mc.player, e.getEntity());
                mc.player.swingHand(Hand.MAIN_HAND);
            } else if (mc.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult b && b.getType() == HitResult.Type.BLOCK
                    && mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, b).isAccepted()) {
                mc.player.swingHand(Hand.MAIN_HAND);
            } else {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            }
        }
    }
}
