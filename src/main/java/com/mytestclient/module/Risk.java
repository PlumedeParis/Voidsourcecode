package com.mytestclient.module;

/**
 * Why a module shows a ⚠ icon next to its toggle.
 * <ul>
 *     <li>{@link #CLIENT_SIDE} (yellow): only changes what <i>you</i> see. The server never knows,
 *     but the result may be unreliable in multiplayer (the server keeps overriding it, or the data
 *     simply isn't sent to clients), or the feature may be against a server's rules.</li>
 *     <li>{@link #DETECTABLE} (red): changes what the client sends to the server (movement,
 *     clicks, inventory actions). Server-side anti-cheats can see this, and most public servers
 *     forbid it. Blocked on multiplayer while Safe Mode is on.</li>
 * </ul>
 */
public enum Risk {
    NONE(0, ""),
    CLIENT_SIDE(0xFFFFC857, "Local only / unreliable in multiplayer"),
    DETECTABLE(0xFFFF5C5C, "Detectable by servers");

    private final int color;
    private final String title;

    Risk(int color, String title) {
        this.color = color;
        this.title = title;
    }

    public int getColor() {
        return color;
    }

    public String getTitle() {
        return title;
    }
}
