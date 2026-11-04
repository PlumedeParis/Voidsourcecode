package com.mytestclient.mixin;

import com.mytestclient.module.Modules;
import com.mytestclient.module.world.WeatherChangerModule;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Weather Changer: overrides rain/thunder strength on the client world only. */
@Mixin(World.class)
public abstract class WorldMixin {
    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
    private void mytestclient$rain(float delta, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ClientWorld)) return;
        WeatherChangerModule weather = Modules.get(WeatherChangerModule.class);
        if (weather != null && weather.isEnabled()) cir.setReturnValue(weather.getRain());
    }

    @Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true)
    private void mytestclient$thunder(float delta, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ClientWorld)) return;
        WeatherChangerModule weather = Modules.get(WeatherChangerModule.class);
        if (weather != null && weather.isEnabled()) cir.setReturnValue(weather.getThunder());
    }
}
