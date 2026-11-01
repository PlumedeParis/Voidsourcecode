package com.mytestclient.module.movement;

import com.mytestclient.event.KeybindManager;
import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.NumberSetting;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Freecam: detaches the camera so you can fly it around while your player stands still.
 * <p>
 * How it works: {@code CameraMixin} overrides the camera position/rotation, {@code EntityMixin}
 * routes mouse movement to the free camera, and this module reads the movement keys directly
 * from the keyboard while clearing the vanilla key state so the real player does not move.
 * <p>
 * ⚠ CLIENT_SIDE: nothing is sent to the server, but the server only loads chunks around your
 * real position, so far-away terrain will be missing in multiplayer.
 */
public class FreecamModule extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed", "Camera speed in blocks per second.", 15, 1, 80, 0.5));
    private final NumberSetting sensitivity = add(new NumberSetting("Sensitivity", "Mouse look multiplier.", 1, 0.1, 3, 0.05));

    private double x, y, z, prevX, prevY, prevZ;
    private float yaw, pitch;

    public FreecamModule() {
        super("Freecam", "Detach the camera and fly it around. Your player stays where it is.", Category.MOVEMENT);
        setRisk(Risk.CLIENT_SIDE, "Purely client-side, but the server only sends chunks near your real position, so distant areas may not be loaded in multiplayer.");
    }

    @Override
    public boolean persistsState() {
        return false;
    }

    @Override
    protected void onEnable() {
        if (mc.player == null) {
            setEnabledQuietly(false);
            return;
        }
        x = prevX = mc.player.getX();
        y = prevY = mc.player.getEyeY();
        z = prevZ = mc.player.getZ();
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
    }

    @Override
    protected void onDisable() {
        KeyBinding.updatePressedStates();
    }

    @Override
    public void onPreTick() {
        GameOptions o = mc.options;
        boolean fwd = KeybindManager.isBindingDown(o.forwardKey);
        boolean back = KeybindManager.isBindingDown(o.backKey);
        boolean left = KeybindManager.isBindingDown(o.leftKey);
        boolean right = KeybindManager.isBindingDown(o.rightKey);
        boolean up = KeybindManager.isBindingDown(o.jumpKey);
        boolean down = KeybindManager.isBindingDown(o.sneakKey);
        boolean fast = KeybindManager.isBindingDown(o.sprintKey);

        // keep the real player still
        for (KeyBinding binding : new KeyBinding[]{o.forwardKey, o.backKey, o.leftKey, o.rightKey, o.jumpKey, o.sneakKey, o.sprintKey}) {
            binding.setPressed(false);
        }

        prevX = x;
        prevY = y;
        prevZ = z;
        double perTick = speed.get() / 20.0 * (fast ? 2 : 1);
        Vec3d dir = MovementInput.direction(fwd, back, left, right, yaw).multiply(perTick);
        x += dir.x;
        z += dir.z;
        if (up) y += perTick;
        if (down) y -= perTick;
    }

    /** Called from {@code EntityMixin} with raw mouse deltas. */
    public void rotate(double dx, double dy) {
        yaw += (float) (dx * 0.15 * sensitivity.get());
        pitch = MathHelper.clamp(pitch + (float) (dy * 0.15 * sensitivity.get()), -90f, 90f);
    }

    @Override
    public void onWorldLeave() {
        if (isEnabled()) setEnabledQuietly(false);
    }

    public double getX(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevX, x);
    }

    public double getY(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevY, y);
    }

    public double getZ(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevZ, z);
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }
}
