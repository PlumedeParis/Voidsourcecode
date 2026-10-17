package com.mytestclient.mixin;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Writes an option value directly, bypassing the slider range validation. Used by Fullbright
 * (gamma above 100%) and Zoom (FOV below 30). Both restore the original value when disabled.
 */
@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor {
    @Accessor("value")
    void mytestclient$setValueDirect(Object value);
}
