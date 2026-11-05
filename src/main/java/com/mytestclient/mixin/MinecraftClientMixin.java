package com.mytestclient.mixin;

import com.mytestclient.module.Modules;
import com.mytestclient.module.visuals.EspModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ESP: forces the vanilla glowing outline (as with the Glowing effect) for ESP targets. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
    private void mytestclient$espOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        EspModule esp = Modules.get(EspModule.class);
        if (esp != null && esp.isEnabled() && esp.glowColor(entity) != null) cir.setReturnValue(true);
    }
}
