package com.voidclient.module.world;

import com.voidclient.gui.SeedMapScreen;
import com.voidclient.module.Category;
import com.voidclient.module.Module;

/**
 * Action: opens the Seed Map (biomes and structures of any seed, generated on your client).
 * Also available with {@code \seedmap [seed]}.
 */
public class SeedMapModule extends Module {
    public SeedMapModule() {
        super("Seed Map", "Action: open a map of any seed with biomes and structures (like Chunkbase, in game).", Category.WORLD);
    }

    @Override
    public boolean isAction() {
        return true;
    }

    @Override
    protected void onAction() {
        mc.setScreen(new SeedMapScreen(mc.currentScreen, null));
    }
}
