package com.mytestclient.module.movement;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.NumberSetting;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * No Fall: tells the server you are on the ground while falling, so fall damage is never
 * applied.
 * <p>
 * ⚠ DETECTABLE: sends false "on ground" packets; anti-cheats compare them with your position.
 */
public class NoFallModule extends Module {
    private final NumberSetting minFall = add(new NumberSetting("Min Fall", "Start protecting after falling this many blocks.", 2.5, 1, 10, 0.5));

    public NoFallModule() {
        super("No Fall", "Prevents fall damage.", Category.MOVEMENT);
        setRisk(Risk.DETECTABLE, "Sends fake on-ground packets, which anti-cheats check against your real position.");
    }

    @Override
    public void onTick() {
        if (mc.player.isGliding() || mc.player.getAbilities().allowFlying) return;
        if (mc.player.fallDistance > minFall.get()) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
        }
    }
}
