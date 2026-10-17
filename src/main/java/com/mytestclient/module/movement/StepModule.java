package com.mytestclient.module.movement;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.NumberSetting;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;

/**
 * Step: walk up full blocks (or higher) without jumping by raising the step height attribute.
 * <p>
 * ⚠ DETECTABLE: the server sees you climbing more than vanilla's 0.6 blocks per step.
 */
public class StepModule extends Module {
    private static final double VANILLA = 0.6;
    private final NumberSetting height = add(new NumberSetting("Height", "Maximum step height in blocks.", 1, 0.6, 3, 0.1));

    public StepModule() {
        super("Step", "Walk up blocks without jumping.", Category.MOVEMENT);
        setRisk(Risk.DETECTABLE, "The server sees step-ups higher than vanilla allows; anti-cheats flag it.");
    }

    @Override
    public void onTick() {
        EntityAttributeInstance attribute = mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
        if (attribute != null && attribute.getBaseValue() != height.get()) attribute.setBaseValue(height.get());
    }

    @Override
    protected void onDisable() {
        if (mc.player == null) return;
        EntityAttributeInstance attribute = mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
        if (attribute != null) attribute.setBaseValue(VANILLA);
    }
}
