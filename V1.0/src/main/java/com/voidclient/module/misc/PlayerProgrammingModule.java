package com.voidclient.module.misc;

import com.voidclient.gui.ProgramEditorScreen;
import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.Risk;

/**
 * Player Programming (action): opens the block editor where you build bots/macros Scratch-style
 * and give each one a start/stop key. Programs run with their key even when the editor is closed.
 */
public class PlayerProgrammingModule extends Module {
    public PlayerProgrammingModule() {
        super("Player Programming", "Action: build bots/macros with Scratch-like blocks and start/stop them with a key.", Category.MISC);
        setRisk(Risk.DETECTABLE, "Programs play for you (moving, mining, attacking, chatting): bot-like behaviour servers can detect.");
    }

    @Override
    public boolean isAction() {
        return true;
    }

    @Override
    protected void onAction() {
        mc.send(() -> mc.setScreen(new ProgramEditorScreen(mc.currentScreen)));
    }
}
