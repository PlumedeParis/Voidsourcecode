package com.mytestclient.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.function.BooleanSupplier;

/** ARGB colour, rendered as a swatch that expands into R/G/B/A sliders. */
public class ColorSetting extends Setting<Integer> {
    public ColorSetting(String name, String description, int argb) {
        super(name, description, argb);
    }

    public ColorSetting visibleWhen(BooleanSupplier condition) {
        setVisibility(condition);
        return this;
    }

    public int getRed() {
        return (value >> 16) & 0xFF;
    }

    public int getGreen() {
        return (value >> 8) & 0xFF;
    }

    public int getBlue() {
        return value & 0xFF;
    }

    public int getAlpha() {
        return (value >>> 24) & 0xFF;
    }

    /** Sets one channel: 0 = red, 1 = green, 2 = blue, 3 = alpha. */
    public void setChannel(int channel, int amount) {
        amount = Math.max(0, Math.min(255, amount));
        int shift = switch (channel) {
            case 0 -> 16;
            case 1 -> 8;
            case 2 -> 0;
            default -> 24;
        };
        set((value & ~(0xFF << shift)) | (amount << shift));
    }

    public int getChannel(int channel) {
        return switch (channel) {
            case 0 -> getRed();
            case 1 -> getGreen();
            case 2 -> getBlue();
            default -> getAlpha();
        };
    }

    public String toHex() {
        return String.format("#%08X", value);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(toHex());
    }

    @Override
    public void fromJson(JsonElement element) {
        String s = element.getAsString().replace("#", "");
        set((int) Long.parseLong(s, 16));
    }
}
