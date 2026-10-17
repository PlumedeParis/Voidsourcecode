package com.mytestclient.module.movement;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.Vec3d;

/** Converts WASD state + a yaw into a horizontal direction vector. */
final class MovementInput {
    private MovementInput() {
    }

    /**
     * @return normalised horizontal direction (y = 0), or {@link Vec3d#ZERO} if no key is held
     */
    static Vec3d direction(boolean forwardKey, boolean backKey, boolean leftKey, boolean rightKey, float yawDegrees) {
        double forward = (forwardKey ? 1 : 0) - (backKey ? 1 : 0);
        double strafe = (leftKey ? 1 : 0) - (rightKey ? 1 : 0);
        if (forward == 0 && strafe == 0) return Vec3d.ZERO;
        double yaw = Math.toRadians(yawDegrees);
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        // forward = (-sin, cos), left = (cos, sin)
        double x = -sin * forward + cos * strafe;
        double z = cos * forward + sin * strafe;
        double len = Math.sqrt(x * x + z * z);
        return new Vec3d(x / len, 0, z / len);
    }

    static Vec3d fromOptions(float yawDegrees) {
        GameOptions o = MinecraftClient.getInstance().options;
        return direction(o.forwardKey.isPressed(), o.backKey.isPressed(), o.leftKey.isPressed(), o.rightKey.isPressed(), yawDegrees);
    }
}
