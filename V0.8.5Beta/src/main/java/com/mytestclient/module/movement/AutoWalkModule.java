package com.mytestclient.module.movement;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import net.minecraft.client.option.KeyBinding;

/** Auto Walk: holds the forward key for you (long trips, AFK travel on ice roads...). */
public class AutoWalkModule extends Module {
    public AutoWalkModule() {
        super("Auto Walk", "Keeps walking forward for you.", Category.MOVEMENT);
    }

    @Override
    public boolean persistsState() {
        return false;
    }

    @Override
    public void onPreTick() {
        mc.options.forwardKey.setPressed(true);
    }

    @Override
    protected void onDisable() {
        KeyBinding.updatePressedStates();
    }
}
