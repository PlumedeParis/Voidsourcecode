package com.mytestclient.mixin;

import com.mytestclient.util.CombatUtil;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets Auto Weapon and Criticals act right before every attack (manual or automated). */
@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    // require = 0: if this ever fails to apply, only those two modules stop working, the game still starts
    @Inject(method = "attackEntity", at = @At("HEAD"), require = 0)
    private void mytestclient$beforeAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        try {
            CombatUtil.beforeAttack(target);
        } catch (Throwable t) {
            com.mytestclient.MyTestClient.LOGGER.warn("beforeAttack hook failed", t);
        }
    }
}
