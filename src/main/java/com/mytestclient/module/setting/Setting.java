package com.mytestclient.module.setting;

import com.google.gson.JsonElement;

import java.util.function.BooleanSupplier;

/**
 * A configurable value of a module. Every setting is rendered automatically by the GUI
 * (see {@code gui.component.SettingComponents}) and saved to JSON.
 *
 * @param <T> the value type
 */
public abstract class Setting<T> {
    private final String name;
    private final String description;
    protected final T defaultValue;
    protected T value;
    private BooleanSupplier visibility = () -> true;

    protected Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = sanitize(value);
    }

    /** Clamp/validate incoming values. */
    protected T sanitize(T value) {
        return value == null ? defaultValue : value;
    }

    public void reset() {
        set(defaultValue);
    }

    public T getDefault() {
        return defaultValue;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isVisible() {
        return visibility.getAsBoolean();
    }

    /** Subclasses expose a typed {@code visibleWhen} that calls this. */
    protected void setVisibility(BooleanSupplier visibility) {
        this.visibility = visibility;
    }

    public abstract JsonElement toJson();

    public abstract void fromJson(JsonElement element);
}
