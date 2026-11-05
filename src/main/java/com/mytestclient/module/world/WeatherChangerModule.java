package com.mytestclient.module.world;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.ModeSetting;

/**
 * Weather Changer: shows clear, rainy or stormy weather on your screen only.
 * <p>
 * ⚠ CLIENT_SIDE: the real weather (crop growth, lightning, fishing) is unchanged.
 */
public class WeatherChangerModule extends Module {
    private final ModeSetting weather = add(new ModeSetting("Weather", "Weather to display.", "Clear", "Clear", "Rain", "Thunder"));

    public WeatherChangerModule() {
        super("Weather Changer", "Change the weather you see (client-side).", Category.WORLD);
        setRisk(Risk.CLIENT_SIDE, "Only changes what you see. The server's real weather is unaffected.");
    }

    public float getRain() {
        return weather.is("Clear") ? 0f : 1f;
    }

    public float getThunder() {
        return weather.is("Thunder") ? 1f : 0f;
    }
}
