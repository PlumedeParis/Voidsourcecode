package com.mytestclient.gui;

import com.mytestclient.module.Modules;
import com.mytestclient.module.misc.ClickGuiModule;
import com.mytestclient.render.Colors;

/**
 * "Void" colour palette of the GUI and HUD: deep blue-black surfaces, soft white text and a
 * two-colour accent gradient (configurable in-game in Misc &gt; ClickGUI).
 */
public final class Theme {
    public static final int BG = 0xF20A0A12;
    public static final int SIDEBAR = 0xFF0D0D16;
    public static final int HEADER = 0xFF0F0F19;
    public static final int PANEL = 0xFF11111B;
    public static final int CARD = 0xFF161622;
    public static final int CARD_HOVER = 0xFF1C1C2B;
    public static final int ROW_HOVER = 0x12FFFFFF;
    public static final int FIELD = 0xFF0C0C14;
    public static final int BUTTON = 0xFF1E1E2D;
    public static final int BUTTON_HOVER = 0xFF29293C;
    public static final int SWITCH_OFF = 0xFF2A2A3B;
    public static final int SLIDER_BG = 0xFF23233A;
    public static final int SEPARATOR = 0x16FFFFFF;
    public static final int BORDER = 0x22FFFFFF;
    public static final int TEXT = 0xFFEDEDF5;
    public static final int TEXT_DIM = 0xFF9C9CB4;
    public static final int TEXT_MUTED = 0xFF5E5E78;
    public static final int GOOD = 0xFF4ADE80;
    public static final int BAD = 0xFFF43F5E;
    public static final int WARN = 0xFFFBBF24;
    public static final int HUD_BG = 0x900A0A12;

    public static final int DEFAULT_ACCENT = 0xFF8B5CF6;
    public static final int DEFAULT_ACCENT2 = 0xFF22D3EE;

    private Theme() {
    }

    public static int accent() {
        ClickGuiModule gui = Modules.get(ClickGuiModule.class);
        if (gui == null) return DEFAULT_ACCENT;
        int c = gui.accent.get() | 0xFF000000;
        return c == 0xFF7C8CFF ? DEFAULT_ACCENT : c; // old default from before the redesign
    }

    /** Second colour of the accent gradient. */
    public static int accent2() {
        ClickGuiModule gui = Modules.get(ClickGuiModule.class);
        return gui == null ? DEFAULT_ACCENT2 : (gui.accent2.get() | 0xFF000000);
    }

    /** Point {@code t} (0..1) along the accent gradient. */
    public static int gradient(float t) {
        return Colors.lerp(accent(), accent2(), t);
    }

    public static boolean tooltips() {
        ClickGuiModule gui = Modules.get(ClickGuiModule.class);
        return gui == null || gui.tooltips.get();
    }

    public static boolean animations() {
        ClickGuiModule gui = Modules.get(ClickGuiModule.class);
        return gui == null || gui.animations.get();
    }
}
