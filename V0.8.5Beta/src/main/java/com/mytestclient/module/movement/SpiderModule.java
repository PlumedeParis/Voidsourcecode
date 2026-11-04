package com.mytestclient.module.movement;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

/**
 * Spider: climb any wall like a spider — walk into a wall and you go up. Sneak against the wall
 * to hold your position.
 * <p>
 * ⚠ DETECTABLE: the server sees you rising along walls without ladders.
 */
public class SpiderModule extends Module {
    private final NumberSetting climbSpeed = add(new NumberSetting("Climb Speed", "Blocks per second upwards.", 4, 1, 20, 0.5));

    public SpiderModule() {
        super("Spider", "Climb walls by walking into them.", Category.MOVEMENT);
        setRisk(Risk.DETECTABLE, "The server sees you climbing walls without ladders; anti-cheats flag it.");
    }

    @Override
    public void onTick() {
        if (!mc.player.horizontalCollision || mc.player.getAbilities().flying) return;
        boolean moving = mc.options.forwardKey.isPressed() || mc.options.leftKey.isPressed()
                || mc.options.rightKey.isPressed() || mc.options.backKey.isPressed();
        if (!moving) return;
        Vec3d v = mc.player.getVelocity();
        double up = mc.player.isSneaking() ? 0 : climbSpeed.get() / 20.0;
        mc.player.setVelocity(v.x, Math.max(v.y, up), v.z);
        mc.player.fallDistance = 0;
    }
}
