package com.voidclient.module.visuals;

import com.voidclient.mixin.SimpleOptionAccessor;
import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.setting.ModeSetting;
import com.voidclient.module.setting.NumberSetting;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Fullbright: see in the dark. Purely visual and comparable to turning the brightness slider
 * past 100%, so it has no warning icon.
 * <ul>
 *     <li>Gamma: raises the brightness option beyond its normal maximum (restored on disable).</li>
 *     <li>Night Vision: applies a client-side Night Vision effect.</li>
 * </ul>
 */
public class FullbrightModule extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Gamma raises the brightness option; Night Vision adds a client-side effect.", "Gamma", "Gamma", "Night Vision"));
    private final NumberSetting brightness = add(new NumberSetting("Brightness", "Gamma value (vanilla max is 1.0).", 12, 1, 16, 0.5)
            .visibleWhen(() -> mode.is("Gamma")));

    private Double originalGamma;
    private boolean nightVisionApplied;

    public FullbrightModule() {
        super("Fullbright", "Makes everything fully lit, even caves at night.", Category.VISUALS);
    }

    @Override
    protected void onEnable() {
        if (mode.is("Gamma")) applyGamma();
    }

    @Override
    public void onTick() {
        if (mode.is("Gamma")) {
            removeNightVision();
            applyGamma();
        } else {
            restoreGamma();
            if (!mc.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, StatusEffectInstance.INFINITE, 0, false, false));
                nightVisionApplied = true;
            }
        }
    }

    @Override
    protected void onDisable() {
        restoreGamma();
        removeNightVision();
    }

    @Override
    public void onShutdown() {
        // never leave an out-of-range gamma in options.txt
        restoreGamma();
    }

    /** From GameOptionsMixin: options.txt is being written, so show Minecraft the real brightness meanwhile. */
    public void suspendForSave(boolean saving) {
        if (originalGamma == null) return;
        double value = saving ? Math.max(0, Math.min(1.0, originalGamma)) : brightness.get();
        ((SimpleOptionAccessor) (Object) mc.options.getGamma()).voidclient$setValueDirect(value);
    }

    private void applyGamma() {
        if (originalGamma == null) originalGamma = mc.options.getGamma().getValue();
        if (mc.options.getGamma().getValue().doubleValue() != brightness.get().doubleValue()) {
            ((SimpleOptionAccessor) (Object) mc.options.getGamma()).voidclient$setValueDirect(brightness.get());
        }
    }

    private void restoreGamma() {
        if (originalGamma == null) return;
        ((SimpleOptionAccessor) (Object) mc.options.getGamma()).voidclient$setValueDirect(Math.min(1.0, originalGamma));
        originalGamma = null;
    }

    private void removeNightVision() {
        if (!nightVisionApplied) return;
        if (mc.player != null) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        nightVisionApplied = false;
    }
}
