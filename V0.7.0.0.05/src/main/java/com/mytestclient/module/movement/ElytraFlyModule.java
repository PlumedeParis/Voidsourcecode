package com.mytestclient.module.movement;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.ModeSetting;
import com.mytestclient.module.setting.NumberSetting;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.Vec3d;

/**
 * Elytra Fly: fly fast with an elytra without fireworks.
 * <ul>
 *     <li>Control: fly in the direction you look at a constant speed; stop (hover) when no key
 *     is held. Jump/sneak move straight up/down (both modes).</li>
 *     <li>Boost: keeps vanilla gliding physics but accelerates you along your look direction
 *     while you hold forward, like an endless firework.</li>
 * </ul>
 * ⚠ DETECTABLE: speeds without fireworks are impossible in vanilla; servers rubber-band
 * ("moved too quickly") and anti-cheats flag it.
 */
public class ElytraFlyModule extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Control: direct flight with hover. Boost: accelerate vanilla gliding.", "Control", "Control", "Boost"));
    private final NumberSetting speed = add(new NumberSetting("Max Speed", "Maximum speed in blocks per second (vanilla firework flight is ~33).", 80, 5, 500, 1));
    private final NumberSetting verticalSpeed = add(new NumberSetting("Vertical Speed", "Up/down speed with jump/sneak; the \\elytra autopilot also climbs over terrain at least this fast.", 30, 1, 200, 1));
    private final NumberSetting acceleration = add(new NumberSetting("Acceleration", "Boost added per tick, in blocks/s (Boost mode).", 5, 0.2, 50, 0.1)
            .visibleWhen(() -> mode.is("Boost")));
    private final BooleanSetting hover = add(new BooleanSetting("Hover", "Stay in place when no movement key is held (Control mode).", true)
            .visibleWhen(() -> mode.is("Control")));
    private final BooleanSetting autoTakeoff = add(new BooleanSetting("Auto Takeoff", "Open the elytra automatically when you start falling.", true));
    private final ModeSetting camera = add(new ModeSetting("Autopilot Camera", "How \\elytra turns your view. Smooth: eases towards the flight direction. Instant: snaps. Free Look: you keep control of the camera.",
            "Smooth", "Smooth", "Instant", "Free Look"));
    private final NumberSetting smoothness = add(new NumberSetting("Smoothness", "How softly the autopilot camera turns (higher = softer).", 0.75, 0.1, 0.95, 0.05)
            .visibleWhen(() -> camera.is("Smooth")));
    private final BooleanSetting elytraSwap = add(new BooleanSetting("Elytra Swap", "Autopilot: put on a fresh elytra from your inventory before the worn one breaks (or land if there is none).", true));

    public ElytraFlyModule() {
        super("Elytra Fly", "Fly fast with an elytra, no fireworks needed. Look where you want to go.", Category.MOVEMENT);
        setRisk(Risk.DETECTABLE, "Elytra speeds without fireworks are impossible in vanilla: servers rubber-band you and anti-cheats flag it.");
    }

    /** Max speed in blocks per second, also used by the \elytra autopilot. */
    public double maxSpeed() {
        return speed.get();
    }

    public String cameraMode() {
        return camera.get();
    }

    public double smoothness() {
        return smoothness.get();
    }

    public boolean elytraSwap() {
        return elytraSwap.get();
    }

    /** Up/down speed in blocks per second, also used by the \elytra autopilot to climb over terrain. */
    public double verticalSpeed() {
        return verticalSpeed.get();
    }

    @Override
    public void onTick() {
        if (com.mytestclient.util.ElytraAutopilot.isActive()) return; // the autopilot drives
        var player = mc.player;
        if (!player.isGliding()) {
            boolean wearingElytra = player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
            if (autoTakeoff.get() && wearingElytra && !player.isOnGround() && player.getVelocity().y < -0.1
                    && player.fallDistance > 1.5 && player.checkGliding()) {
                player.networkHandler.sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
            return;
        }

        Vec3d look = player.getRotationVector();
        double max = speed.get() / 20.0;
        boolean forward = mc.options.forwardKey.isPressed();

        if (mode.is("Control")) {
            double up = 0;
            if (mc.options.jumpKey.isPressed()) up += verticalSpeed.get() / 20.0;
            if (mc.options.sneakKey.isPressed()) up -= verticalSpeed.get() / 20.0;
            if (forward) {
                player.setVelocity(look.x * max, look.y * max + up, look.z * max);
            } else if (mc.options.backKey.isPressed()) {
                player.setVelocity(-look.x * max * 0.5, up, -look.z * max * 0.5);
            } else if (hover.get()) {
                player.setVelocity(0, up, 0);
            }
        } else {
            Vec3d v = player.getVelocity();
            if (forward) {
                v = v.add(look.multiply(acceleration.get() / 20.0));
                double len = v.length();
                if (len > max) v = v.multiply(max / len);
            }
            // jump/sneak climb or dive without having to pitch up/down
            double vertical = verticalSpeed.get() / 20.0;
            if (mc.options.jumpKey.isPressed()) v = new Vec3d(v.x, Math.max(v.y, vertical), v.z);
            if (mc.options.sneakKey.isPressed()) v = new Vec3d(v.x, Math.min(v.y, -vertical), v.z);
            player.setVelocity(v);
        }
    }
}
