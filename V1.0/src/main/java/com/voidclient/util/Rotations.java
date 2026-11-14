package com.voidclient.util;

import com.voidclient.module.Modules;
import com.voidclient.module.misc.BypassModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Where automated modules "look". With Bypass &gt; Silent Rotations on, the rotation is only
 * sent to the server (your camera never moves): the look packet goes out right away, so the
 * click that follows is consistent with it, and it is held for a few ticks in the movement
 * packets ({@code ClientPlayerEntityMixin}). Otherwise the real view is turned, as before.
 */
public final class Rotations {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static float yaw, pitch;
    private static int ticksLeft;

    private Rotations() {
    }

    public static void face(Vec3d target) {
        double dx = target.x - mc.player.getX(), dy = target.y - mc.player.getEyeY(), dz = target.z - mc.player.getZ();
        float y = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float p = MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), -90f, 90f);
        set(y, p);
    }

    public static void set(float newYaw, float newPitch) {
        BypassModule bypass = Modules.get(BypassModule.class);
        if (bypass == null || !bypass.isEnabled() || !bypass.silentRotations.get()) {
            mc.player.setYaw(newYaw);
            mc.player.setPitch(newPitch);
            return;
        }
        // keep the yaw continuous (no 350° jumps) relative to what the server last saw
        float base = ticksLeft > 0 ? yaw : mc.player.getYaw();
        newYaw = base + MathHelper.wrapDegrees(newYaw - base);
        boolean changed = ticksLeft <= 0 || Math.abs(newYaw - yaw) > 0.01f || Math.abs(newPitch - pitch) > 0.01f;
        yaw = newYaw;
        pitch = newPitch;
        ticksLeft = bypass.holdTicks.getInt();
        if (changed && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
        }
    }

    /** True while the server-side rotation differs from the camera. */
    public static boolean active() {
        return ticksLeft > 0 && mc.player != null;
    }

    public static float yaw() {
        return yaw;
    }

    public static float pitch() {
        return pitch;
    }

    public static void tick() {
        if (ticksLeft > 0) ticksLeft--;
    }

    public static void reset() {
        ticksLeft = 0;
    }
}
