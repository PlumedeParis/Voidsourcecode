package com.mytestclient.module.world;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.module.setting.StringSetting;
import com.mytestclient.util.ChatUtil;
import com.mytestclient.util.SeedFinder;

/** Seed Finder (action): searches seeds matching the criteria, same as {@code \seedfinder}. Trigger again to stop. */
public class SeedFinderModule extends Module {
    private final StringSetting criteria = add(new StringSetting("Criteria",
            "Structures and max distance from 0 0, plus an optional spawn biome. Example: village 300 stronghold 2000 biome:cherry_grove",
            "village 300 stronghold 2000", 120));
    private final NumberSetting count = add(new NumberSetting("Seeds", "How many matching seeds to find.", 5, 1, 50, 1));

    public SeedFinderModule() {
        super("Seed Finder", "Action: find seeds with structures close to spawn and/or a chosen spawn biome.", Category.WORLD);
    }

    @Override
    public boolean isAction() {
        return true;
    }

    @Override
    protected void onAction() {
        if (SeedFinder.isRunning()) {
            SeedFinder.stop();
            return;
        }
        String error = SeedFinder.start(criteria.get(), count.getInt(), null);
        if (error != null) ChatUtil.error("Seed Finder: " + error);
        else ChatUtil.success("Seed Finder: searching for '" + criteria.get() + "'. Trigger again to stop.");
    }
}
