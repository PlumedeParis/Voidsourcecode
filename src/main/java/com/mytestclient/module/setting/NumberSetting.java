package com.mytestclient.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.function.BooleanSupplier;

/** Numeric value with a range and step, rendered as a slider. */
public class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
        super(name, description, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
        set(defaultValue);
    }

    public NumberSetting visibleWhen(BooleanSupplier condition) {
        setVisibility(condition);
        return this;
    }

    @Override
    protected Double sanitize(Double v) {
        if (v == null || v.isNaN()) return defaultValue;
        double clamped = Math.max(min, Math.min(max, v));
        if (step > 0) clamped = min + Math.round((clamped - min) / step) * step;
        clamped = Math.max(min, Math.min(max, clamped));
        return Math.round(clamped * 1_000_000d) / 1_000_000d;
    }

    public int getInt() {
        return (int) Math.round(value);
    }

    public float getFloat() {
        return value.floatValue();
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStep() {
        return step;
    }

    /** Value formatted with as many decimals as the step needs. */
    public String format() {
        if (step >= 1) return Integer.toString(getInt());
        int decimals = Math.max(1, (int) Math.ceil(-Math.log10(step)));
        return String.format("%." + decimals + "f", value);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement element) {
        set(element.getAsDouble());
    }
}
