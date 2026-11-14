package com.voidclient.module.world;

import com.voidclient.mixin.MinecraftClientAccessor;
import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.Risk;
import com.voidclient.module.setting.NumberSetting;

/**
 * Fast Place: shortens vanilla's 4-tick delay between right-click uses (placing blocks,
 * throwing items...).
 * <p>
 * ⚠ DETECTABLE: the server receives placements faster than a vanilla client can send them.
 */
public class FastPlaceModule extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks between uses (vanilla is 4).", 0, 0, 3, 1));

    public FastPlaceModule() {
        super("Fast Place", "Place blocks and use items faster when holding right click.", Category.WORLD);
        setRisk(Risk.DETECTABLE, "The server receives placements faster than vanilla allows; anti-cheats can flag it.");
    }

    @Override
    public void onTick() {
        MinecraftClientAccessor accessor = (MinecraftClientAccessor) mc;
        if (accessor.voidclient$getItemUseCooldown() > delay.getInt()) accessor.voidclient$setItemUseCooldown(delay.getInt());
    }
}
