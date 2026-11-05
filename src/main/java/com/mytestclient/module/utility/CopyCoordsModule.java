package com.mytestclient.module.utility;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.util.Notifications;

/** Action: copies your coordinates to the clipboard (handy for sharing or notes). */
public class CopyCoordsModule extends Module {
    public CopyCoordsModule() {
        super("Copy Coords", "Action: copy your current coordinates to the clipboard.", Category.UTILITY);
    }

    @Override
    public boolean isAction() {
        return true;
    }

    @Override
    protected void onAction() {
        if (mc.player == null) return;
        String coords = mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ();
        mc.keyboard.setClipboard(coords);
        Notifications.info("Copied " + coords);
    }
}
