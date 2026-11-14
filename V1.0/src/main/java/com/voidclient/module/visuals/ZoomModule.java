package com.voidclient.module.visuals;

import com.voidclient.module.BindMode;
import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.setting.BooleanSetting;
import com.voidclient.module.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

/**
 * Zoom: hold C to zoom in. Only the rendered field of view is divided (in
 * {@code GameRendererMixin#getFov}); no game option is ever changed. The old version switched
 * Minecraft's cinematic camera on while zooming and could leave it stuck on, so that is gone
 * for good: the camera always follows the mouse directly.
 */
public class ZoomModule extends Module {
    private final NumberSetting factor = add(new NumberSetting("Factor", "How much to zoom in.", 4, 1.5, 15, 0.5));
    private final BooleanSetting smoothTransition = add(new BooleanSetting("Smooth Transition", "Animate zooming in and out.", true));

    private float current = 1f;
    private long lastFrame = System.nanoTime();

    public ZoomModule() {
        super("Zoom", "Zoom in while holding the key, like a spyglass.", Category.VISUALS, GLFW.GLFW_KEY_C);
        setDefaultBindMode(BindMode.HOLD);
    }

    @Override
    public boolean persistsState() {
        return false;
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
