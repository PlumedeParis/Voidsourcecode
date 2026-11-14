package com.voidclient.gui.component;

import com.voidclient.module.setting.BooleanSetting;
import com.voidclient.module.setting.ColorSetting;
import com.voidclient.module.setting.ListSetting;
import com.voidclient.module.setting.ModeSetting;
import com.voidclient.module.setting.NumberSetting;
import com.voidclient.module.setting.Setting;
import com.voidclient.module.setting.StringSetting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Maps setting types to GUI components. To support a new setting type, create the setting class
 * and its component, then call {@link #register} (e.g. in this static block).
 */
public final class SettingComponents {
    private static final Map<Class<?>, Function<Setting<?>, SettingComponent>> FACTORIES = new LinkedHashMap<>();

    static {
        register(BooleanSetting.class, BooleanComponent::new);
        register(NumberSetting.class, NumberComponent::new);
        register(ModeSetting.class, ModeComponent::new);
        register(ColorSetting.class, ColorComponent::new);
        register(StringSetting.class, StringComponent::new);
        register(ListSetting.class, ListComponent::new);
    }

    private SettingComponents() {
    }

    public static <S extends Setting<?>> void register(Class<S> type, Function<S, SettingComponent> factory) {
        FACTORIES.put(type, setting -> factory.apply(type.cast(setting)));
    }

    public static SettingComponent create(Setting<?> setting) {
        for (Class<?> c = setting.getClass(); c != null; c = c.getSuperclass()) {
            Function<Setting<?>, SettingComponent> factory = FACTORIES.get(c);
            if (factory != null) return factory.apply(setting);
        }
        throw new IllegalArgumentException("No GUI component for " + setting.getClass().getName());
    }
}
