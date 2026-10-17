package com.mytestclient.module.movement;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.ModeSetting;
import com.mytestclient.module.setting.NumberSetting;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Fly: lets you fly like in creative mode.
 * <p>
 * ⚠ DETECTABLE: your position updates are sent to the server. Vanilla servers kick you for
 * flying ("Flying is not enabled on this server") unless {@code allow-flight=true}, and
 * anti-cheats flag it immediately. Intended for singleplayer, testing worlds and servers that
 * allow it.
 */
public class FlyModule extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode",
            "Abilities: vanilla creative flight. Velocity: direct movement control, no inertia.",
            "Abilities", "Abilities", "Velocity"));
    private final NumberSetting speed = add(new NumberSetting("Speed", "Horizontal speed in blocks per second.", 11, 1, 60, 0.5));
    private final NumberSetting verticalSpeed = add(new NumberSetting("Vertical Speed", "Up/down speed in blocks per second (Velocity mode).", 8, 1, 40, 0.5)
            .visibleWhen(() -> mode.is("Velocity")));

    private boolean hadAllowFlying;

    public FlyModule() {
        super("Fly", "Fly freely. Jump to go up, sneak to go down.", Category.MOVEMENT);
        setRisk(Risk.DETECTABLE, "Movement is sent to the server. Vanilla servers kick for flying unless allow-flight is on, and anti-cheats detect it. Use in singleplayer or where allowed.");
    }

    @Override
    protected void onEnable() {
        if (mc.player != null) hadAllowFlying = mc.player.getAbilities().allowFlying;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity player = mc.player;
        if (mode.is("Abilities")) {
            player.getAbilities().allowFlying = true;
            player.getAbilities().flying = true;
            // vanilla creative flight speed is 0.05 ~= 11 blocks/s
            player.getAbilities().setFlySpeed((float) (speed.get() / 11.0 * 0.05));
        } else {
            player.getAbilities().flying = false;
            Vec3d dir = MovementInput.fromOptions(player.getYaw()).multiply(speed.get() / 20.0);
            double vy = 0;
            if (mc.options.jumpKey.isPressed()) vy += verticalSpeed.get() / 20.0;
            if (mc.options.sneakKey.isPressed()) vy -= verticalSpeed.get() / 20.0;
            player.setVelocity(dir.x, vy, dir.z);
        }
    }

    @Override
    protected void onDisable() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        player.getAbilities().setFlySpeed(0.05f);
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().flying = false;
            player.getAbilities().allowFlying = hadAllowFlying;
        }
    }

    @Override
    public void onWorldLeave() {
        if (isEnabled()) setEnabledQuietly(false);
    }
}
