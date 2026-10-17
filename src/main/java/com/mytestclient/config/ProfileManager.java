package com.mytestclient.config;

import com.google.gson.JsonElement;
import com.mytestclient.MyTestClient;
import com.mytestclient.module.ModuleManager;
import com.mytestclient.module.SafeMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Module presets. A profile is a snapshot of every module (enabled state, keybind, settings)
 * stored as {@code config/mytestclient/profiles/<name>.json}.
 */
public class ProfileManager {
    private final Path dir;
    private final ModuleManager modules;

    public ProfileManager(Path dir, ModuleManager modules) {
        this.dir = dir;
        this.modules = modules;
    }

    /** Keeps names filesystem-safe. */
    public static String sanitize(String name) {
        String clean = name.trim().replaceAll("[^A-Za-z0-9_\\- ]", "").replace(' ', '_');
        return clean.length() > 32 ? clean.substring(0, 32) : clean;
    }

    public List<String> list() {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(dir)) return names;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> {
                        String file = p.getFileName().toString();
                        names.add(file.substring(0, file.length() - 5));
                    });
        } catch (IOException e) {
            MyTestClient.LOGGER.error("Could not list profiles", e);
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public boolean save(String name) {
        name = sanitize(name);
        if (name.isEmpty()) return false;
        boolean ok = JsonFiles.write(dir.resolve(name + ".json"), ModuleSerializer.serialize(modules));
        if (ok) setActive(name);
        return ok;
    }

    public boolean load(String name) {
        JsonElement json = JsonFiles.read(dir.resolve(sanitize(name) + ".json"));
        if (json == null || !json.isJsonObject()) return false;
        ModuleSerializer.deserialize(modules, json.getAsJsonObject());
        // profiles enable modules quietly, so re-apply Safe Mode afterwards
        SafeMode.enforce();
        setActive(name);
        return true;
    }

    public boolean delete(String name) {
        try {
            boolean deleted = Files.deleteIfExists(dir.resolve(sanitize(name) + ".json"));
            if (deleted && name.equals(getActive())) setActive("");
            return deleted;
        } catch (IOException e) {
            MyTestClient.LOGGER.error("Could not delete profile {}", name, e);
            return false;
        }
    }

    public String getActive() {
        return MyTestClient.get().config().getActiveProfile();
    }

    private void setActive(String name) {
        MyTestClient.get().config().setActiveProfile(name);
        MyTestClient.get().config().save();
    }
}
