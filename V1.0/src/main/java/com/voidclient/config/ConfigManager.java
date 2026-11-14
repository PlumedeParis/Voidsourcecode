package com.voidclient.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.voidclient.VoidClient;
import com.voidclient.module.ModuleManager;

import java.nio.file.Path;

/**
 * Saves and loads {@code config/voidclient/config.json}: every module's enabled state,
 * keybind, bind mode and settings, plus the name of the last loaded profile.
 */
public class ConfigManager {
    private static final int VERSION = 1;

    private final Path file;
    private final ModuleManager modules;
    private String activeProfile = "";

    public ConfigManager(Path file, ModuleManager modules) {
        this.file = file;
        this.modules = modules;
    }

    public void load() {
        JsonElement root = JsonFiles.read(file);
        if (root == null || !root.isJsonObject()) {
            VoidClient.LOGGER.info("No config found, writing defaults to {}", file);
            save();
            return;
        }
        JsonObject obj = root.getAsJsonObject();
        if (obj.has("modules") && obj.get("modules").isJsonObject()) {
            ModuleSerializer.deserialize(modules, obj.getAsJsonObject("modules"));
        }
        if (obj.has("activeProfile")) activeProfile = obj.get("activeProfile").getAsString();
    }

    public void save() {
        JsonObject obj = new JsonObject();
        obj.addProperty("version", VERSION);
        obj.addProperty("activeProfile", activeProfile);
        obj.add("modules", ModuleSerializer.serialize(modules));
        JsonFiles.write(file, obj);
    }

    public String getActiveProfile() {
        return activeProfile;
    }

    public void setActiveProfile(String activeProfile) {
        this.activeProfile = activeProfile;
    }
}
