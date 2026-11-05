package com.mytestclient.mixin;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Writes an option value directly, bypassing the slider range validation. Used by Fullbright
 * (gamma above 100%), and to repair options left out of range by older versions.
 */
@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor {
    @Accessor("value")
    void mytestclient$setValueDirect(Object value);
}
