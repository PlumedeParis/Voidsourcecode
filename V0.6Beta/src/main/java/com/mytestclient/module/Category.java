package com.mytestclient.module;

/**
 * Module categories shown in the click GUI sidebar. Add a constant here to get a new tab.
 */
public enum Category {
    MOVEMENT("Movement"),
    VISUALS("Visuals"),
    WORLD("World"),
    COMBAT("Combat"),
    UTILITY("Utility"),
    HUD("HUD"),
    MISC("Misc");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
