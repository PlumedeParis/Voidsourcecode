package com.mytestclient.module.world;

import com.mytestclient.mixin.MinecraftClientAccessor;
import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.NumberSetting;

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
        if (accessor.mytestclient$getItemUseCooldown() > delay.getInt()) accessor.mytestclient$setItemUseCooldown(delay.getInt());
    }
}
