package com.voidclient.module.utility;

import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.Risk;
import com.voidclient.module.setting.BooleanSetting;
import com.voidclient.module.setting.NumberSetting;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/** Chest Stealer: takes everything out of the chest, barrel or shulker box you open. */
public class ChestStealerModule extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks between items (0 = all at once).", 1, 0, 10, 1));
    private final BooleanSetting autoClose = add(new BooleanSetting("Auto Close", "Close the container when it is empty or your inventory is full.", true));
    private int timer;

    public ChestStealerModule() {
        super("Chest Stealer", "Empties chests, barrels and shulker boxes into your inventory when you open them.", Category.UTILITY);
        setRisk(Risk.DETECTABLE, "Takes items faster than a human can click.");
    }

    @Override
    public void onTick() {
        var handler = mc.player.currentScreenHandler;
        int size;
        if (handler instanceof GenericContainerScreenHandler g) size = g.getRows() * 9;
        else if (handler instanceof ShulkerBoxScreenHandler) size = 27;
        else return;
        if (timer-- > 0) return;
        boolean full = mc.player.getInventory().getEmptySlot() < 0;
        int moved = 0;
        for (int i = 0; i < size && !full; i++) {
            if (handler.getSlot(i).getStack().isEmpty()) continue;
            mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            moved++;
            if (delay.getInt() > 0) {
                timer = delay.getInt();
                return;
            }
            full = mc.player.getInventory().getEmptySlot() < 0;
        }
        if (moved == 0 && autoClose.get()) mc.player.closeHandledScreen();
    }
}
