package com.voidclient.module.misc;

import com.voidclient.gui.HudEditorScreen;
import com.voidclient.module.Category;
import com.voidclient.module.Module;

/** Opens the HUD editor where HUD elements can be dragged around. */
public class HudEditorModule extends Module {
    public HudEditorModule() {
        super("HUD Editor", "Action: open the editor to drag HUD elements into place.", Category.MISC);
    }

    @Override
    public boolean isAction() {
        return true;
    }

    @Override
    protected void onAction() {
        mc.setScreen(new HudEditorScreen(mc.currentScreen));
    }
}
