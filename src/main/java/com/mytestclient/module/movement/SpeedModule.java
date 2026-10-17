package com.mytestclient.module.movement;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Speed: adjustable ground movement speed.
 * <p>
 * ⚠ DETECTABLE: the server sees you moving faster than possible and will rubber-band you
 * (vanilla "moved too quickly") or flag you (anti-cheat).
 */
public class SpeedModule extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed", "Ground speed in blocks per second (sprinting is ~5.6).", 8, 2, 30, 0.1));
    private final BooleanSetting onlyOnGround = add(new BooleanSetting("Only On Ground", "Do not change speed while airborne.", true));
    private final BooleanSetting inLiquids = add(new BooleanSetting("In Liquids", "Also apply in water and lava.", false));

    public SpeedModule() {
        super("Speed", "Move faster on the ground with an adjustable speed.", Category.MOVEMENT);
        setRisk(Risk.DETECTABLE, "The server sees impossible movement speed: vanilla servers rubber-band you and anti-cheats flag it.");
    }

    @Override
    public void onTick() {
        ClientPlayerEntity player = mc.player;
        if (player.isSneaking() || player.getAbilities().flying || player.hasVehicle()) return;
        if (onlyOnGround.get() && !player.isOnGround()) return;
        if (!inLiquids.get() && (player.isTouchingWater() || player.isInLava())) return;
        Vec3d dir = MovementInput.fromOptions(player.getYaw());
        if (dir == Vec3d.ZERO) return;
        double perTick = speed.get() / 20.0;
        player.setVelocity(dir.x * perTick, player.getVelocity().y, dir.z * perTick);
    }
}
