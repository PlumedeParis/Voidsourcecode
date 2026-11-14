package com.voidclient.module.misc;

import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.Modules;
import com.voidclient.module.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

/** Auto Reconnect: when you are kicked or the connection drops, rejoins the same server after a delay. */
public class AutoReconnectModule extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", "Seconds to wait before reconnecting.", 5, 1, 120, 1));
    private static ServerInfo last;
    private static int ticks = -1;
    private static boolean manualLeave;

    public AutoReconnectModule() {
        super("Auto Reconnect", "Rejoins the server automatically after a kick or a lost connection.", Category.MISC);
    }

    @Override
    public void onWorldJoin() {
        remember();
    }

    @Override
    public void onTick() {
        if (last == null) remember();
    }

    private static void remember() {
        ServerInfo info = MinecraftClient.getInstance().getCurrentServerEntry();
        if (info != null) last = info;
    }

    /** Seconds left before reconnecting, or -1 (for the disconnect screen hint). */
    public static int secondsLeft() {
        return ticks < 0 ? -1 : ticks / 20 + 1;
    }

    /** Every client tick, also outside a world. */
    public static void tickAlways(MinecraftClient mc) {
        AutoReconnectModule m = Modules.get(AutoReconnectModule.class);
        if (m == null || !m.isEnabled() || last == null || !(mc.currentScreen instanceof DisconnectedScreen)) {
            ticks = -1;
            return;
        }
        if (ticks < 0) ticks = m.delay.getInt() * 20;
        if (ticks-- == 0) {
            ticks = -1;
            ConnectScreen.connect(new TitleScreen(), mc, ServerAddress.parse(last.address), last, false, null);
        }
    }
}
