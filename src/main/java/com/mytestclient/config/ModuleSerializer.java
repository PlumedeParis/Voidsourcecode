package com.mytestclient.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mytestclient.module.Module;
import com.mytestclient.module.ModuleManager;

import java.util.Map;

/** Converts the state of all modules to/from JSON. Shared by the config and profiles. */
public final class ModuleSerializer {
    private ModuleSerializer() {
    }

    public static JsonObject serialize(ModuleManager modules) {
        JsonObject obj = new JsonObject();
        for (Module module : modules.all()) obj.add(module.getName(), module.toJson());
        return obj;
    }

    public static void deserialize(ModuleManager modules, JsonObject obj) {
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            Module module = modules.byName(entry.getKey());
            if (module != null && entry.getValue().isJsonObject()) module.fromJson(entry.getValue().getAsJsonObject());
        }
    }
}
