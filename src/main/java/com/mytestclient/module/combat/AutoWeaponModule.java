package com.mytestclient.module.combat;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.util.CombatUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/** Auto Weapon: switches to your best sword/axe from the hotbar when you hit a mob or a player. */
public class AutoWeaponModule extends Module {
    private final BooleanSetting switchBack = add(new BooleanSetting("Switch Back", "Go back to the previous slot a second after the fight.", true));

    private int previous = -1, idle;

    public AutoWeaponModule() {
        super("Auto Weapon", "Switches to your best weapon when you attack.", Category.COMBAT);
    }

    public void beforeAttack(Entity target) {
        if (!(target instanceof LivingEntity)) return;
        int slot = CombatUtil.bestWeaponSlot();
        int current = mc.player.getInventory().getSelectedSlot();
        idle = 0;
        if (slot < 0 || slot == current) return;
        if (previous < 0) previous = current;
        mc.player.getInventory().setSelectedSlot(slot);
    }

    @Override
    public void onTick() {
        if (previous < 0 || !switchBack.get()) return;
        if (++idle > 20 && !mc.options.attackKey.isPressed()) {
            mc.player.getInventory().setSelectedSlot(previous);
            previous = -1;
        }
    }

    @Override
    protected void onDisable() {
        previous = -1;
    }

    @Override
    public void onWorldLeave() {
        previous = -1;
    }
}
