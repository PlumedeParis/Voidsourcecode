package com.mytestclient.module.hud;

import com.mytestclient.hud.HudModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.List;

/** Potion HUD: active status effects with level and remaining time. */
public class PotionHud extends HudModule {
    private static final String[] ROMAN = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};

    public PotionHud() {
        super("Potions", "Shows active effects with level and remaining time.", 100, 50);
    }

    @Override
    protected void render(DrawContext ctx, int x, int y, float tickDelta) {
        List<Line> lines = new ArrayList<>();
        for (StatusEffectInstance effect : mc.player.getStatusEffects()) {
            String name = effect.getEffectType().value().getName().getString();
            int amp = effect.getAmplifier();
            String level = amp < ROMAN.length ? ROMAN[amp] : " " + (amp + 1);
            String time;
            if (effect.isInfinite()) time = "∞";
            else {
                int seconds = effect.getDuration() / 20;
                time = String.format("%d:%02d", seconds / 60, seconds % 60);
            }
            lines.add(new Line("", name + level + "  " + time, effect.getEffectType().value().getColor() | 0xFF000000));
        }
        if (lines.isEmpty()) {
            setSize(1, 1);
            return;
        }
        drawLines(ctx, x, y, lines);
    }
}
