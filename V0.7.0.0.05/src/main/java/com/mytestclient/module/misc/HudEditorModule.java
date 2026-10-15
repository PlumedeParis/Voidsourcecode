package com.mytestclient.module.misc;

import com.mytestclient.gui.HudEditorScreen;
import com.mytestclient.module.Category;
import com.mytestclient.module.Module;

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
