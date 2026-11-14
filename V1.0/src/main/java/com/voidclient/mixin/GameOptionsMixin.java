package com.voidclient.mixin;

import com.voidclient.module.Modules;
import com.voidclient.module.visuals.FullbrightModule;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fullbright raises the brightness option past its maximum. While options.txt is being written
 * the real value is put back, so the file never holds an out-of-range brightness (which made
 * Minecraft log "Error saving option Brightness" and skip it).
 */
@Mixin(GameOptions.class)
public abstract class GameOptionsMixin {
    @Inject(method = "write", at = @At("HEAD"), require = 0)
    private void voidclient$beforeWrite(CallbackInfo ci) {
        FullbrightModule fb = Modules.get(FullbrightModule.class);
        if (fb != null) fb.suspendForSave(true);
    }

    @Inject(method = "write", at = @At("RETURN"), require = 0)
    private void voidclient$afterWrite(CallbackInfo ci) {
        FullbrightModule fb = Modules.get(FullbrightModule.class);
        if (fb != null) fb.suspendForSave(false);
    }
}
