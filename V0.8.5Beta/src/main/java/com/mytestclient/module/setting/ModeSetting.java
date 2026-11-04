package com.mytestclient.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.function.BooleanSupplier;

/** One-of-many choice, rendered as a dropdown. */
public class ModeSetting extends Setting<String> {
    private final List<String> modes;

    public ModeSetting(String name, String description, String defaultValue, String... modes) {
        super(name, description, defaultValue);
        this.modes = List.of(modes);
        if (!this.modes.contains(defaultValue)) throw new IllegalArgumentException("Default mode not in list: " + defaultValue);
    }

    public ModeSetting visibleWhen(BooleanSupplier condition) {
        setVisibility(condition);
        return this;
    }

    @Override
    protected String sanitize(String v) {
        return v != null && modes.contains(v) ? v : defaultValue;
    }

    public boolean is(String mode) {
        return value.equals(mode);
    }

    public List<String> getModes() {
        return modes;
    }

    public void cycle() {
        set(modes.get((modes.indexOf(value) + 1) % modes.size()));
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement element) {
        set(element.getAsString());
    }
}
