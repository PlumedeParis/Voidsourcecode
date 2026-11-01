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

    /** Velocity at the end of the last tick: nothing changes it before the next tick except the server. */
    private Vec3d last;

    @Override
    public void onTick() {
        last = mc.player.getVelocity();
    }

    @Override
    public void onPreTick() {
        var p = mc.player;
        // only a hit with knockback: hurt this tick AND the server pushed us (fire/poison don't push)
        if (p == null || last == null || p.hurtTime != p.maxHurtTime || p.maxHurtTime <= 0) return;
        Vec3d v = p.getVelocity();
        Vec3d push = v.subtract(last);
        if (push.lengthSquared() < 1e-4) return;
        double h = horizontal.get() / 100.0, y = vertical.get() / 100.0;
        p.setVelocity(last.x + push.x * h, push.y > 0 ? last.y + push.y * y : v.y, last.z + push.z * h);
    }

    @Override
    public void onWorldLeave() {
        last = null;
    }
}
