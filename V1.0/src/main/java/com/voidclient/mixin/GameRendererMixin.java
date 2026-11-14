package com.voidclient.mixin;

import com.voidclient.module.Modules;
import com.voidclient.module.visuals.NoHurtCamModule;
import com.voidclient.render.Projection;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** Overlays: the bobbing/tilt matrix is rebuilt every frame, start from identity. */
    @Inject(method = "renderWorld", at = @At("HEAD"), require = 0)
    private void voidclient$frameStart(net.minecraft.client.render.RenderTickCounter counter, CallbackInfo ci) {
        Projection.setInHand(false);
        Projection.resetBob();
    }

    /** Overlays: record the view bobbing so ESP lines follow it exactly. */
    @Inject(method = "bobView", at = @At("TAIL"), require = 0)
    private void voidclient$bob(net.minecraft.client.util.math.MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        Projection.setBob(matrices.peek().getPositionMatrix());
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("TAIL"), require = 0)
    private void voidclient$tilt(net.minecraft.client.util.math.MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        Projection.setBob(matrices.peek().getPositionMatrix());
    }

    @Inject(method = "renderHand", at = @At("HEAD"), require = 0)
    private void voidclient$handStart(float tickProgress, boolean sleeping, org.joml.Matrix4f matrix, CallbackInfo ci) {
        Projection.setInHand(true);
    }

    @Inject(method = "renderHand", at = @At("RETURN"), require = 0)
    private void voidclient$handEnd(float tickProgress, boolean sleeping, org.joml.Matrix4f matrix, CallbackInfo ci) {
        Projection.setInHand(false);
    }

    /** No Hurt Cam: skips the camera shake when you take damage. */
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void voidclient$noHurtCam(CallbackInfo ci) {
        if (Modules.active(NoHurtCamModule.class)) ci.cancel();
    }

    /** Records the real world FOV (including sprint/speed effects) so overlays line up. */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void voidclient$fov(Camera camera, float tickProgress, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (!changingFov) return; // the hand uses its own fixed FOV
        float fov = cir.getReturnValueF();
        com.voidclient.module.visuals.ZoomModule zoom = Modules.get(com.voidclient.module.visuals.ZoomModule.class);
        if (zoom != null) fov = zoom.apply(fov);
        Projection.setWorldFov(fov);
        cir.setReturnValue(fov);
    }
}
