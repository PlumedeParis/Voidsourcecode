package com.mytestclient.module.world;

import com.mytestclient.gui.SeedMapScreen;
import com.mytestclient.module.Category;
import com.mytestclient.module.Module;

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
        mc.setScreen(new SeedMapScreen(null));
    }
}
