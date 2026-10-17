package com.mytestclient.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Fast Place: access to the right-click cooldown (4 ticks in vanilla). */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Accessor("itemUseCooldown")
    int mytestclient$getItemUseCooldown();

    @Accessor("itemUseCooldown")
    void mytestclient$setItemUseCooldown(int cooldown);
}
