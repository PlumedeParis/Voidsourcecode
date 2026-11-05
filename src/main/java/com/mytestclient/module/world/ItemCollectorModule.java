package com.mytestclient.module.world;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.module.setting.StringSetting;
import com.mytestclient.pathing.Travel;

/** Item Collector (action): walks to every dropped item around you and picks it up. Same as {@code \pickup}. */
public class ItemCollectorModule extends Module {
    private final StringSetting filter = add(new StringSetting("Filter", "Only items whose name/id contains this (empty = everything).", "", 40));
    private final NumberSetting radius = add(new NumberSetting("Radius", "How far to look for items.", 32, 4, 128, 4));

    public ItemCollectorModule() {
        super("Item Collector", "Action: walks to the dropped items around you and picks them up.", Category.WORLD);
    }

    @Override
    public boolean isAction() {
        return true;
    }

    @Override
    protected void onAction() {
        if (mc.player == null) return;
        Travel.pickup(filter.get().isBlank() ? null : filter.get(), radius.get());
    }
}
