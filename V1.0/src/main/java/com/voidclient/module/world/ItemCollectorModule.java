package com.voidclient.module.world;

import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.setting.NumberSetting;
import com.voidclient.module.setting.StringSetting;
import com.voidclient.pathing.Travel;

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
