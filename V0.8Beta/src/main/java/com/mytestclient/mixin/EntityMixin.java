package com.mytestclient.mixin;

import com.mytestclient.module.Modules;
import com.mytestclient.module.movement.FreecamModule;
import com.mytestclient.module.visuals.EspModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    /** Freecam: mouse movement rotates the free camera instead of the player. */
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void mytestclient$freecamLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if ((Object) this != MinecraftClient.getInstance().player) return;
        FreecamModule freecam = Modules.get(FreecamModule.class);
        if (freecam != null && freecam.isEnabled()) {
            freecam.rotate(cursorDeltaX, cursorDeltaY);
            ci.cancel();
        }
    }

    /** ESP: the glow outline uses the team colour, so we return the ESP colour instead. */
    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void mytestclient$espColor(CallbackInfoReturnable<Integer> cir) {
        EspModule esp = Modules.get(EspModule.class);
        if (esp == null || !esp.isEnabled()) return;
        Integer color = esp.glowColor((Entity) (Object) this);
        if (color != null) cir.setReturnValue(color & 0xFFFFFF);
    }
}
