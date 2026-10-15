package com.mytestclient.gui;

import com.mytestclient.module.Modules;
import com.mytestclient.module.misc.ClickGuiModule;

/**
 * Colour palette of the GUI and HUD. Edit these constants to re-skin everything; the accent
 * colour is configurable in-game (Misc &gt; ClickGUI).
 */
public final class Theme {
    public static final int BG = 0xF2121318;
    public static final int SIDEBAR = 0xFF16181F;
    public static final int HEADER = 0xFF1A1D25;
    public static final int PANEL = 0xFF1B1E26;
    public static final int CARD = 0xFF232731;
    public static final int CARD_HOVER = 0xFF2B303C;
    public static final int ROW_HOVER = 0x14FFFFFF;
    public static final int FIELD = 0xFF14161C;
    public static final int BUTTON = 0xFF2A2F3B;
    public static final int BUTTON_HOVER = 0xFF353B49;
    public static final int SWITCH_OFF = 0xFF3A3F4B;
    public static final int SLIDER_BG = 0xFF2F3440;
    public static final int SEPARATOR = 0x22FFFFFF;
    public static final int TEXT = 0xFFE6E8EF;
    public static final int TEXT_DIM = 0xFF8A90A0;
    public static final int TEXT_MUTED = 0xFF5F6575;
    public static final int GOOD = 0xFF4ADE80;
    public static final int BAD = 0xFFEF4444;
    public static final int WARN = 0xFFFFC857;
    public static final int HUD_BG = 0x90101216;

    public static final int DEFAULT_ACCENT = 0xFF7C8CFF;

    private Theme() {
    }

    public static int accent() {
        ClickGuiModule gui = Modules.get(ClickGuiModule.class);
        return gui == null ? DEFAULT_ACCENT : (gui.accent.get() | 0xFF000000);
    }

    public static boolean tooltips() {
        ClickGuiModule gui = Modules.get(ClickGuiModule.class);
        return gui == null || gui.tooltips.get();
    }
}
