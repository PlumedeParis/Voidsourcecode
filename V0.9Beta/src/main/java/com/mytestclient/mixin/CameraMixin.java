package com.mytestclient.mixin;

import com.mytestclient.module.Modules;
import com.mytestclient.module.movement.FreecamModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Freecam: moves the camera away from the player after vanilla positioned it. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void mytestclient$applyFreecam(CallbackInfo ci) {
        FreecamModule freecam = Modules.get(FreecamModule.class);
        if (freecam == null || !freecam.isEnabled()) return;
        float tickDelta = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(true);
        setRotation(freecam.getYaw(), freecam.getPitch());
        setPos(freecam.getX(tickDelta), freecam.getY(tickDelta), freecam.getZ(tickDelta));
    }

    /** Makes the player model render while the camera is detached. */
    @Inject(method = "isThirdPerson", at = @At("HEAD"), cancellable = true)
    private void mytestclient$freecamThirdPerson(CallbackInfoReturnable<Boolean> cir) {
        if (Modules.active(FreecamModule.class)) cir.setReturnValue(true);
    }
}
