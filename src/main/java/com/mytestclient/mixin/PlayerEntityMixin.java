package com.mytestclient.mixin;

import com.mytestclient.module.Modules;
import com.mytestclient.module.movement.SafeWalkModule;
import com.mytestclient.module.movement.ScaffoldModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Safe Walk / Scaffold: reuse vanilla's "sneaking keeps you on the edge" logic. */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "clipAtLedge", at = @At("HEAD"), cancellable = true)
    private void mytestclient$safeWalk(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this != MinecraftClient.getInstance().player) return;
        ScaffoldModule scaffold = Modules.get(ScaffoldModule.class);
        if (Modules.active(SafeWalkModule.class) || (scaffold != null && scaffold.wantsSafeWalk())) cir.setReturnValue(true);
    }
}
