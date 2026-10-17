package com.mytestclient.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mytestclient.config.JsonFiles;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Friend list (player names), ignored by combat modules. Managed with {@code \friend add/del/list},
 * saved to {@code config/mytestclient/friends.json}.
 */
public final class Friends {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("mytestclient").resolve("friends.json");
    private static final Set<String> NAMES = new TreeSet<>();
    private static boolean loaded;

    private Friends() {
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        JsonElement json = JsonFiles.read(FILE);
        if (json != null && json.isJsonArray()) for (JsonElement e : json.getAsJsonArray()) NAMES.add(e.getAsString().toLowerCase(Locale.ROOT));
    }

    public static boolean isFriend(String name) {
        ensureLoaded();
        return NAMES.contains(name.toLowerCase(Locale.ROOT));
    }

    public static boolean add(String name) {
        ensureLoaded();
        boolean added = NAMES.add(name.toLowerCase(Locale.ROOT));
        save();
        return added;
    }

    public static boolean remove(String name) {
        ensureLoaded();
        boolean removed = NAMES.remove(name.toLowerCase(Locale.ROOT));
        save();
        return removed;
    }

    public static Set<String> all() {
        ensureLoaded();
        return Collections.unmodifiableSet(NAMES);
    }

    private static void save() {
        JsonArray array = new JsonArray();
        for (String n : NAMES) array.add(n);
        JsonFiles.write(FILE, array);
    }
}
