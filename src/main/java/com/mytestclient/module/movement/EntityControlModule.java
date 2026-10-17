package com.mytestclient.module.movement;

import com.mytestclient.event.KeybindManager;
import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Modules;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Entity Control: steer the animal you ride even without a saddle (horses, donkeys, mules,
 * camels, pigs, striders) — and boats — with an adjustable speed and an optional fly mode.
 * {@code MountControlMixin} makes the rider the controlling passenger (what a saddle normally
 * does); this module then drives the vehicle's velocity directly.
 * <p>
 * Fly mode: jump = up, sprint key (Ctrl) = down (sneak would dismount).
 * <p>
 * ⚠ DETECTABLE: works fully in singleplayer and LAN worlds you host (the mixin also applies to
 * the built-in server). On other servers an unsaddled mount rubber-bands because the server
 * still requires a saddle, and speed/fly on saddled mounts and boats is visible to anti-cheats.
 */
public class EntityControlModule extends Module {
    private final NumberSetting speed = add(new NumberSetting("Ride Speed", "Speed in blocks per second (a fast horse is ~14).", 20, 2, 100, 1));
    private final BooleanSetting fly = add(new BooleanSetting("Fly", "Make the mount fly: jump = up, sprint key = down.", false));
    private final NumberSetting verticalSpeed = add(new NumberSetting("Vertical Speed", "Up/down speed in blocks per second.", 10, 1, 50, 1)
            .visibleWhen(fly::get));
    private final BooleanSetting noFallDamage = add(new BooleanSetting("No Fall Damage", "Reset the mount's fall distance.", true));

    private Entity flying;

    public EntityControlModule() {
        super("Entity Control", "Ride and steer horses, pigs, striders, camels and boats without a saddle, faster, and even fly.", Category.MOVEMENT);
        setRisk(Risk.DETECTABLE, "Works in singleplayer/LAN worlds you host. On other servers unsaddled mounts rubber-band, and speed/fly is visible to anti-cheats.");
    }

    /** The player who should control {@code vehicle}, or null to keep vanilla behaviour. */
    public static LivingEntity controller(Entity vehicle) {
        if (!Modules.active(EntityControlModule.class)) return null;
        return vehicle.getFirstPassenger() instanceof PlayerEntity player ? player : null;
    }

    @Override
    public void onPreTick() {
        Entity vehicle = mc.player.getVehicle();
        if (vehicle == null || vehicle.getFirstPassenger() != mc.player) {
            restoreGravity();
            return;
        }
        vehicle.setYaw(mc.player.getYaw());
        Vec3d dir = MovementInput.fromOptions(mc.player.getYaw()).multiply(speed.get() / 20.0);
        double vy = vehicle.getVelocity().y;
        if (fly.get()) {
            vy = 0;
            if (mc.options.jumpKey.isPressed()) vy += verticalSpeed.get() / 20.0;
            if (KeybindManager.isBindingDown(mc.options.sprintKey)) vy -= verticalSpeed.get() / 20.0;
            if (flying != vehicle) {
                restoreGravity();
                vehicle.setNoGravity(true);
                flying = vehicle;
            }
        } else {
            restoreGravity();
        }
        vehicle.setVelocity(dir.x, vy, dir.z);
        if (noFallDamage.get()) {
            vehicle.fallDistance = 0;
            mc.player.fallDistance = 0;
        }
    }

    private void restoreGravity() {
        if (flying != null) {
            flying.setNoGravity(false);
            flying = null;
        }
    }

    @Override
    protected void onDisable() {
        restoreGravity();
    }

    @Override
    public void onWorldLeave() {
        flying = null;
    }
}
