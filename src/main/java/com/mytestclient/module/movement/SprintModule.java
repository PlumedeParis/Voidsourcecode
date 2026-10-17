package com.mytestclient.module.movement;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.BooleanSetting;
import net.minecraft.client.option.KeyBinding;

/**
 * Sprint: keeps the sprint key held while you walk forward — the same as vanilla's
 * "Sprint: Toggle" accessibility option, so no warning icon.
 */
public class SprintModule extends Module {
    private final BooleanSetting onlyForward = add(new BooleanSetting("Only Forward", "Only sprint while holding the forward key.", true));

    public SprintModule() {
        super("Sprint", "Automatically sprint while walking (like vanilla toggle-sprint).", Category.MOVEMENT);
    }

    @Override
    public void onPreTick() {
        boolean moving = onlyForward.get() ? mc.options.forwardKey.isPressed()
                : mc.options.forwardKey.isPressed() || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
        mc.options.sprintKey.setPressed(moving);
    }

    @Override
    protected void onDisable() {
        KeyBinding.updatePressedStates();
    }
}
