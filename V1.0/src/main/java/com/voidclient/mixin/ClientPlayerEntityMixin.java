package com.voidclient.mixin;

import com.voidclient.module.Modules;
import com.voidclient.module.movement.NoSlowModule;
import com.voidclient.util.Rotations;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Silent rotations: while a module holds a server-side rotation, the movement packets carry it
 * instead of the camera's (swapped in for the duration of sendMovementPackets only).
 * No Slow: eating/blocking does not slow you down.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Unique
    private float voidclient$realYaw, voidclient$realPitch;
    @Unique
    private boolean voidclient$swapped;

    @Inject(method = "sendMovementPackets", at = @At("HEAD"), require = 0)
    private void voidclient$beforeMove(CallbackInfo ci) {
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        if (!Rotations.active()) return;
        voidclient$realYaw = self.getYaw();
        voidclient$realPitch = self.getPitch();
        self.setYaw(Rotations.yaw());
        self.setPitch(Rotations.pitch());
        voidclient$swapped = true;
    }

    @Inject(method = "sendMovementPackets", at = @At("RETURN"), require = 0)
    private void voidclient$afterMove(CallbackInfo ci) {
        if (!voidclient$swapped) return;
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        self.setYaw(voidclient$realYaw);
        self.setPitch(voidclient$realPitch);
        voidclient$swapped = false;
    }

    @Redirect(method = "applyMovementSpeedFactors", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"), require = 0)
    private boolean voidclient$noSlow(ClientPlayerEntity self) {
        return self.isUsingItem() && !Modules.active(NoSlowModule.class);
    }
}
