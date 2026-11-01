package com.mytestclient.module.world;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;

/**
 * Time Changer: shows a different time of day on your screen (the mixin overrides the
 * client's time of day). Optionally cycles through the day quickly to preview lighting.
 * <p>
 * ⚠ CLIENT_SIDE: only you see it; the real server time (mob spawning, beds...) is unchanged.
 */
public class TimeChangerModule extends Module {
    private final NumberSetting time = add(new NumberSetting("Time", "Ticks: 0 sunrise, 6000 noon, 13000 night, 18000 midnight.", 6000, 0, 23999, 100));
    private final BooleanSetting cycle = add(new BooleanSetting("Cycle", "Animate through the day instead of a fixed time.", false));
    private final NumberSetting cycleSpeed = add(new NumberSetting("Cycle Speed", "Ticks of day time per game tick.", 40, 1, 400, 1)
            .visibleWhen(cycle::get));

    private long cycled;

    public TimeChangerModule() {
        super("Time Changer", "Change the time of day you see (client-side).", Category.WORLD);
        setRisk(Risk.CLIENT_SIDE, "Only changes what you see. The server's real time (mob spawns, sleeping) is unaffected.");
    }

    @Override
    public void onTick() {
        if (cycle.get()) cycled = (cycled + cycleSpeed.getInt()) % 24000;
        else cycled = time.getInt();
    }

    public long getTime() {
        return cycle.get() ? cycled : time.getInt();
    }
}
