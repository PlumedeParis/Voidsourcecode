package com.mytestclient.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mytestclient.module.setting.Setting;
import com.mytestclient.render.Overlay;
import com.mytestclient.util.Notifications;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class of every feature.
 * <p>
 * To add a module: extend this class, declare settings as fields with {@link #add(Setting)},
 * override the hooks you need and register it in {@link ModuleManager}. The GUI, config,
 * profiles and keybinds pick it up automatically.
 */
public abstract class Module {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    private final String name;
    private final String description;
    private final Category category;
    private final int defaultKey;
    private final List<Setting<?>> settings = new ArrayList<>();

    private boolean enabled;
    private int key;
    private BindMode bindMode = BindMode.TOGGLE;

    private Risk risk = Risk.NONE;
    private String riskReason = "";

    protected Module(String name, String description, Category category) {
        this(name, description, category, GLFW.GLFW_KEY_UNKNOWN);
    }

    protected Module(String name, String description, Category category, int defaultKey) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.defaultKey = defaultKey;
        this.key = defaultKey;
    }

    /** Registers a setting so it shows up in the GUI and is saved to the config. */
    protected final <T extends Setting<?>> T add(T setting) {
        settings.add(setting);
        return setting;
    }

    /** Marks the module with a ⚠ icon. {@code reason} is shown in the GUI tooltip/description. */
    protected final void setRisk(Risk risk, String reason) {
        this.risk = risk;
        this.riskReason = reason;
    }

    protected final void setDefaultBindMode(BindMode mode) {
        this.bindMode = mode;
    }

    // ------------------------------------------------------------------ behaviour flags

    /** Action modules run {@link #onAction()} when triggered and have no on/off state. */
    public boolean isAction() {
        return false;
    }

    /** Whether the enabled state is written to the config (false for e.g. Freecam/Zoom). */
    public boolean persistsState() {
        return true;
    }

    // ------------------------------------------------------------------ hooks

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    protected void onAction() {
    }

    /** START of client tick, before the player moves. Only called in-world while enabled. */
    public void onPreTick() {
    }

    /** END of client tick. Only called in-world while enabled. */
    public void onTick() {
    }

    /** Draws world-space overlays projected onto the screen. Only called while enabled. */
    public void onRenderOverlay(Overlay overlay) {
    }

    /** Called when joining a world/server (whether enabled or not). */
    public void onWorldJoin() {
    }

    /** Called when leaving a world/server (whether enabled or not). */
    public void onWorldLeave() {
    }

    /** Called when the game closes. Restore any game option you changed. */
    public void onShutdown() {
    }

    // ------------------------------------------------------------------ state

    public void toggle() {
        if (isAction()) {
            onAction();
            return;
        }
        setEnabled(!enabled);
    }

    public void setEnabled(boolean value) {
        if (isAction() || value == enabled) return;
        if (value) {
            String blocked = SafeMode.blockReason(this);
            if (blocked != null) {
                Notifications.warn(name + ": " + blocked);
                return;
            }
        }
        enabled = value;
        if (value) onEnable();
        else onDisable();
        Notifications.moduleToggled(this);
    }

    /** Changes the state without notifications or Safe Mode checks (config loading, error handling). */
    public void setEnabledQuietly(boolean value) {
        if (isAction() || value == enabled) return;
        enabled = value;
        if (value) onEnable();
        else onDisable();
    }

    public void resetSettings() {
        for (Setting<?> setting : settings) setting.reset();
    }

    // ------------------------------------------------------------------ serialisation

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        if (persistsState() && !isAction()) obj.addProperty("enabled", enabled);
        obj.addProperty("key", key);
        obj.addProperty("bindMode", bindMode.name());
        JsonObject values = new JsonObject();
        for (Setting<?> setting : settings) values.add(setting.getName(), setting.toJson());
        obj.add("settings", values);
        return obj;
    }

    public void fromJson(JsonObject obj) {
        if (obj.has("key")) key = obj.get("key").getAsInt();
        if (obj.has("bindMode")) {
            try {
                bindMode = BindMode.valueOf(obj.get("bindMode").getAsString());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (obj.has("settings") && obj.get("settings").isJsonObject()) {
            JsonObject values = obj.getAsJsonObject("settings");
            for (Setting<?> setting : settings) {
                JsonElement element = values.get(setting.getName());
                if (element == null) continue;
                try {
                    setting.fromJson(element);
                } catch (RuntimeException ignored) {
                    setting.reset();
                }
            }
        }
        if (persistsState() && obj.has("enabled")) setEnabledQuietly(obj.get("enabled").getAsBoolean());
    }

    // ------------------------------------------------------------------ getters

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public List<Setting<?>> getSettings() {
        return Collections.unmodifiableList(settings);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getKey() {
        return key;
    }

    public void setKey(int key) {
        this.key = key;
    }

    public int getDefaultKey() {
        return defaultKey;
    }

    public BindMode getBindMode() {
        return bindMode;
    }

    public void setBindMode(BindMode bindMode) {
        this.bindMode = bindMode;
    }

    public Risk getRisk() {
        return risk;
    }

    public String getRiskReason() {
        return riskReason;
    }

    protected static boolean inWorld() {
        return mc.player != null && mc.world != null;
    }
}
