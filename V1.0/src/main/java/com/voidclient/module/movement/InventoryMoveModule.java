package com.voidclient.module.movement;

import com.voidclient.event.KeybindManager;
import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.Risk;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/** Inventory Move: keep walking, jumping and sprinting while an inventory or the click GUI is open. */
public class InventoryMoveModule extends Module {
    public InventoryMoveModule() {
        super("Inventory Move", "Move, jump and sprint while inventories and menus are open.", Category.MOVEMENT);
        setRisk(Risk.DETECTABLE, "Moving with a container open is checked by some anti-cheats.");
    }

    @Override
    public void onPreTick() {
        var s = mc.currentScreen;
        if (s == null || s instanceof ChatScreen || s instanceof SignEditScreen || s instanceof AnvilScreen || s instanceof BookEditScreen) return;
        if (s instanceof com.voidclient.gui.BaseScreen && !(s instanceof com.voidclient.gui.ClickGuiScreen)) return; // editors use the keys
        for (KeyBinding k : new KeyBinding[]{mc.options.forwardKey, mc.options.backKey, mc.options.leftKey, mc.options.rightKey, mc.options.jumpKey, mc.options.sprintKey}) {
            int code = InputUtil.fromTranslationKey(k.getBoundKeyTranslationKey()).getCode();
            if (code > 0) k.setPressed(KeybindManager.isKeyDown(code));
        }
    }
}
