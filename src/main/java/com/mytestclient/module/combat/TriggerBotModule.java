package com.mytestclient.module.combat;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.util.CombatUtil;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;

/**
 * Trigger Bot: attacks the mob/player under your crosshair as soon as your attack is fully
 * charged. You aim, it clicks. ⚠ DETECTABLE: automated clicks.
 */
public class TriggerBotModule extends Module {
    private final BooleanSetting players = add(new BooleanSetting("Players", "Attack players (friends excluded).", true));
    private final BooleanSetting hostiles = add(new BooleanSetting("Monsters", "Attack hostile mobs.", true));
    private final BooleanSetting animals = add(new BooleanSetting("Animals", "Attack passive mobs.", false));
    private final NumberSetting delay = add(new NumberSetting("Extra Delay", "Ticks to wait after the cooldown is full (looks more human).", 0, 0, 10, 1));

    private int wait;

    public TriggerBotModule() {
        super("Trigger Bot", "Attacks what you aim at as soon as your hit is fully charged.", Category.COMBAT);
        setRisk(Risk.DETECTABLE, "Automated clicks; anti-cheats look at click timing.");
    }

    @Override
    public void onTick() {
        if (mc.currentScreen != null || mc.player.isUsingItem()) return;
        Entity target = mc.targetedEntity;
        if (target == null || !CombatUtil.isTarget(target, players.get(), hostiles.get(), animals.get())) {
            wait = delay.getInt();
            return;
        }
        if (mc.player.getAttackCooldownProgress(0.5f) < 1f) return;
        if (wait-- > 0) return;
        if (CombatUtil.holdForCritical()) return;
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        wait = delay.getInt();
    }
}
