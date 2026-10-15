package com.mytestclient.module.combat;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.NumberSetting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Auto Totem: keeps a Totem of Undying in your offhand by swapping one in from your inventory.
 * <p>
 * ⚠ DETECTABLE: performs inventory clicks automatically (without the inventory open), which
 * anti-cheats can detect and many PvP servers forbid.
 */
public class AutoTotemModule extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks to wait between swaps.", 2, 0, 20, 1));

    private int timer;

    public AutoTotemModule() {
        super("Auto Totem", "Keep a Totem of Undying in your offhand automatically.", Category.COMBAT);
        setRisk(Risk.DETECTABLE, "Automatic inventory clicks can be detected by anti-cheats and are banned on many PvP servers.");
    }

    @Override
    public void onTick() {
        if (timer > 0) {
            timer--;
            return;
        }
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return;

        int slot = -1;
        // main inventory first so the hotbar stays untouched when possible
        for (int i = 9; i < 36 && slot < 0; i++) if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) slot = i;
        for (int i = 0; i < 9 && slot < 0; i++) if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) slot = i;
        if (slot < 0) return;

        // player screen handler: hotbar 0-8 lives in slots 36-44, inventory 9-35 keeps its index.
        int screenSlot = slot < 9 ? 36 + slot : slot;
        // SWAP with button 40 swaps the clicked slot with the offhand
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, screenSlot, 40, SlotActionType.SWAP, mc.player);
        timer = delay.getInt();
    }
}
