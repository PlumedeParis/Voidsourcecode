package com.voidclient.module.combat;

import com.voidclient.module.BindMode;
import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.Risk;
import com.voidclient.module.setting.BooleanSetting;
import com.voidclient.module.setting.NumberSetting;
import com.voidclient.util.Rotations;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/** Auto XP: throws experience bottles at your feet to repair Mending armour, then stops when it is repaired. */
public class AutoXpModule extends Module {
    private final NumberSetting perTick = add(new NumberSetting("Bottles Per Tick", "Bottles thrown each tick.", 1, 1, 4, 1));
    private final BooleanSetting stopWhenRepaired = add(new BooleanSetting("Stop When Repaired", "Turn off once all your armour is at full durability.", true));

    public AutoXpModule() {
        super("Auto XP", "Throws XP bottles at your feet to repair your armour (Mending).", Category.COMBAT);
        setRisk(Risk.DETECTABLE, "Throws items without looking and faster than normal.");
        setDefaultBindMode(BindMode.HOLD);
    }

    @Override
    public boolean persistsState() {
        return false;
    }

    @Override
    public void onTick() {
        if (stopWhenRepaired.get() && repaired()) {
            setEnabled(false);
            return;
        }
        int slot = -1;
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isOf(Items.EXPERIENCE_BOTTLE)) slot = i;
        if (slot < 0) return;
        int previous = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        Rotations.set(mc.player.getYaw(), 90);
        if (!Rotations.active()) mc.player.setPitch(90);
        for (int i = 0; i < perTick.getInt(); i++) mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.getInventory().setSelectedSlot(previous);
    }

    private boolean repaired() {
        boolean any = false;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack s = mc.player.getEquippedStack(slot);
            if (s.isEmpty() || !s.isDamageable()) continue;
            any = true;
            if (s.getDamage() > 0) return false;
        }
        return any;
    }
}
