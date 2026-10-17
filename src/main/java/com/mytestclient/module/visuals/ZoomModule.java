package com.mytestclient.module.visuals;

import com.mytestclient.module.BindMode;
import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

/**
 * Zoom: OptiFine-style zoom while holding C (default), with a smooth transition. Applied in
 * {@code GameRendererMixin#getFov}: your FOV option is never modified, so it can't get stuck or
 * be saved wrongly. Purely visual, no warning.
 */
public class ZoomModule extends Module {
    private final NumberSetting factor = add(new NumberSetting("Factor", "How much to zoom in.", 4, 1.5, 15, 0.5));
    private final BooleanSetting smoothTransition = add(new BooleanSetting("Smooth Transition", "Animate zooming in and out.", true));
    private final BooleanSetting smoothCamera = add(new BooleanSetting("Smooth Camera", "Use cinematic camera while zoomed.", true));

    private float current = 1f;
    private long lastFrame = System.nanoTime();
    private boolean originalSmooth;

    public ZoomModule() {
        super("Zoom", "Zoom in while holding the key, like a spyglass.", Category.VISUALS, GLFW.GLFW_KEY_C);
        setDefaultBindMode(BindMode.HOLD);
    }

    @Override
    public boolean persistsState() {
        return false;
    }

    @Override
    protected void onEnable() {
        originalSmooth = mc.options.smoothCameraEnabled;
        if (smoothCamera.get()) mc.options.smoothCameraEnabled = true;
    }

    @Override
    protected void onDisable() {
        mc.options.smoothCameraEnabled = originalSmooth;
    }

    /** Called every frame from the FOV mixin (also while disabled, to animate zooming out). */
    public float apply(float fov) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        float target = isEnabled() ? factor.getFloat() : 1f;
        current = smoothTransition.get() ? current + (target - current) * Math.min(1f, dt * 14f) : target;
        if (Math.abs(current - target) < 0.001f) current = target;
        return fov / current;
    }
}
