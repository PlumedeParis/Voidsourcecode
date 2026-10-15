package com.mytestclient.module;

/** How a module's keybind behaves. */
public enum BindMode {
    /** Press once to enable, press again to disable. */
    TOGGLE("Toggle"),
    /** Enabled only while the key is held down. */
    HOLD("Hold");

    private final String displayName;

    BindMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
