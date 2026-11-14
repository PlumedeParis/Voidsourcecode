package com.voidclient.module.misc;

import com.mojang.authlib.GameProfile;
import com.voidclient.module.Category;
import com.voidclient.module.Module;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;

import java.util.UUID;

/** Fake Player: puts a client-side copy of you where you stand, to test ESP, crystals or trap modules. */
public class FakePlayerModule extends Module {
    private OtherClientPlayerEntity fake;

    public FakePlayerModule() {
        super("Fake Player", "Spawns a client-side dummy player at your position for testing.", Category.MISC);
    }

    @Override
    public boolean persistsState() {
        return false;
    }

    @Override
    protected void onEnable() {
        if (mc.world == null || mc.player == null) return;
        fake = new OtherClientPlayerEntity(mc.world, new GameProfile(UUID.randomUUID(), "FakePlayer"));
        fake.refreshPositionAndAngles(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch());
        fake.headYaw = mc.player.headYaw;
        fake.setHealth(20);
        mc.world.addEntity(fake);
    }

    @Override
    protected void onDisable() {
        if (fake != null) fake.remove(Entity.RemovalReason.DISCARDED);
        fake = null;
    }

    @Override
    public void onWorldLeave() {
        fake = null;
        setEnabledQuietly(false);
    }
}
