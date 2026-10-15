package com.mytestclient.module.combat;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

/**
 * Velocity (anti-knockback): reduces the knockback you take when you are hit.
 * ⚠ DETECTABLE: servers expect you to move after a hit.
 */
public class VelocityModule extends Module {
    private final NumberSetting horizontal = add(new NumberSetting("Horizontal", "Horizontal knockback kept, in %.", 0, 0, 100, 5));
    private final NumberSetting vertical = add(new NumberSetting("Vertical", "Vertical knockback kept, in %.", 0, 0, 100, 5));

    public VelocityModule() {
        super("Velocity", "Take less (or no) knockback when you are hit.", Category.COMBAT);
        setRisk(Risk.DETECTABLE, "Servers expect you to be pushed after a hit; no knockback is a classic check.");
    }

    @Override
    public void onPreTick() {
        var p = mc.player;
        // hurtTime is set to its max on the tick the hit (and its knockback) arrives
        if (p == null || p.hurtTime != p.maxHurtTime || p.maxHurtTime <= 0) return;
        Vec3d v = p.getVelocity();
        double h = horizontal.get() / 100.0, y = vertical.get() / 100.0;
        p.setVelocity(v.x * h, v.y > 0 ? v.y * y : v.y, v.z * h);
    }
}
