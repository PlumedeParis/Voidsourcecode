package com.mytestclient.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.function.BooleanSupplier;

/** Free text value, rendered as a text box. */
public class StringSetting extends Setting<String> {
    private final int maxLength;

    public StringSetting(String name, String description, String defaultValue, int maxLength) {
        super(name, description, defaultValue);
        this.maxLength = maxLength;
    }

    public StringSetting visibleWhen(BooleanSupplier condition) {
        setVisibility(condition);
        return this;
    }

    @Override
    protected String sanitize(String v) {
        if (v == null) return defaultValue;
        return v.length() > maxLength ? v.substring(0, maxLength) : v;
    }

    public int getMaxLength() {
        return maxLength;
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
