package com.mytestclient.mixin;

import com.mytestclient.module.Modules;
import com.mytestclient.module.world.TimeChangerModule;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time Changer: overrides the client-side time of day used for sky and lighting. */
@Mixin(ClientWorld.Properties.class)
public abstract class ClientWorldPropertiesMixin {
    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
    private void mytestclient$timeOfDay(CallbackInfoReturnable<Long> cir) {
        TimeChangerModule time = Modules.get(TimeChangerModule.class);
        if (time != null && time.isEnabled()) cir.setReturnValue(time.getTime());
    }
}
