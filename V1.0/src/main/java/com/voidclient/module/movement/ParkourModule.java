package com.voidclient.module.movement;

import com.voidclient.module.Category;
import com.voidclient.module.Module;

/** Parkour: jumps for you at the very edge of a block, for the longest possible jump. */
public class ParkourModule extends Module {
    public ParkourModule() {
        super("Parkour", "Jumps automatically when you reach the edge of a block.", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (!mc.player.isOnGround() || mc.player.isSneaking() || mc.options.jumpKey.isPressed()) return;
        if (mc.player.getVelocity().horizontalLengthSquared() < 0.003) return;
        var box = mc.player.getBoundingBox().offset(0, -0.5, 0).expand(-0.001, 0, -0.001);
        if (mc.world.isSpaceEmpty(mc.player, box)) mc.player.jump();
    }
}
