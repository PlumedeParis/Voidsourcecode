package com.mytestclient.module.misc;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.NumberSetting;
import net.minecraft.client.gui.screen.DeathScreen;

/** Auto Respawn: clicks "Respawn" for you after dying (a death waypoint is still created). */
public class AutoRespawnModule extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks to wait on the death screen.", 10, 0, 100, 1));

    private int timer;

    public AutoRespawnModule() {
        super("Auto Respawn", "Respawns automatically after you die.", Category.MISC);
    }

    @Override
    public void onTick() {
        if (!(mc.currentScreen instanceof DeathScreen)) {
            timer = delay.getInt();
            return;
        }
        if (timer-- > 0) return;
        mc.player.requestRespawn();
        mc.setScreen(null);
    }
}
