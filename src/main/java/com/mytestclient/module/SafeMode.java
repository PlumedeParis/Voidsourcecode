package com.mytestclient.module;

import com.mytestclient.module.misc.ClickGuiModule;
import com.mytestclient.util.Notifications;
import net.minecraft.client.MinecraftClient;

/**
 * Safe Mode (Misc &gt; ClickGUI): while on, modules marked {@link Risk#DETECTABLE} cannot be
 * enabled on multiplayer servers. They keep working in singleplayer and LAN worlds you host.
 */
public final class SafeMode {
    private SafeMode() {
    }

    public static boolean isOn() {
        ClickGuiModule gui = Modules.get(ClickGuiModule.class);
        return gui != null && gui.safeMode.get();
    }

    /** Returns why {@code module} may not be enabled right now, or null if it may. */
    public static String blockReason(Module module) {
        if (module.getRisk() != Risk.DETECTABLE || !isOn()) return null;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.isInSingleplayer()) return null;
        return "blocked by Safe Mode on multiplayer";
    }

    /** Disables detectable modules when joining a multiplayer server with Safe Mode on. */
    public static void enforce() {
        int disabled = 0;
        for (Module module : Modules.all()) {
            if (module.isEnabled() && blockReason(module) != null) {
                module.setEnabledQuietly(false);
                disabled++;
            }
        }
        if (disabled > 0) Notifications.warn("Safe Mode disabled " + disabled + " detectable module(s)");
    }
}
