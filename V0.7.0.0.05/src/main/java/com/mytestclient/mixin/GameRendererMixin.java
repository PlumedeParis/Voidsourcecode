package com.mytestclient.mixin;

import com.mytestclient.module.Modules;
import com.mytestclient.module.visuals.NoHurtCamModule;
import com.mytestclient.module.visuals.ZoomModule;
import com.mytestclient.render.Projection;
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
    private void mytestclient$frameStart(net.minecraft.client.render.RenderTickCounter counter, CallbackInfo ci) {
        Projection.resetBob();
    }

    /** Overlays: record the view bobbing so ESP lines follow it exactly. */
    @Inject(method = "bobView", at = @At("TAIL"), require = 0)
    private void mytestclient$bob(net.minecraft.client.util.math.MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        Projection.setBob(matrices.peek().getPositionMatrix());
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("TAIL"), require = 0)
    private void mytestclient$tilt(net.minecraft.client.util.math.MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        Projection.setBob(matrices.peek().getPositionMatrix());
    }

    /** No Hurt Cam: skips the camera shake when you take damage. */
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void mytestclient$noHurtCam(CallbackInfo ci) {
        if (Modules.active(NoHurtCamModule.class)) ci.cancel();
    }

    /**
     * Zoom changes the FOV here (the FOV option is never touched, so it can't get stuck), and
     * the real world FOV (including sprint/speed effects) is recorded so overlays line up.
     */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void mytestclient$fov(Camera camera, float tickProgress, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (!changingFov) return; // the hand uses its own fixed FOV
        float fov = cir.getReturnValueF();
        ZoomModule zoom = Modules.get(ZoomModule.class);
        if (zoom != null) fov = zoom.apply(fov);
        Projection.setWorldFov(fov);
        cir.setReturnValue(fov);
    }
}
