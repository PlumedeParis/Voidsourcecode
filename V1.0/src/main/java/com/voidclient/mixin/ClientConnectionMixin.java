package com.voidclient.mixin;

import com.voidclient.module.Modules;
import com.voidclient.module.movement.BlinkModule;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blink: holds outgoing movement packets while the module is on. */
@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {
    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void voidclient$blink(Packet<?> packet, CallbackInfo ci) {
        BlinkModule blink = Modules.get(BlinkModule.class);
        if (blink != null && blink.intercept(packet)) ci.cancel();
    }
}
