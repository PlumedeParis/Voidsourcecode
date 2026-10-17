package com.mytestclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mytestclient.MyTestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Small helpers to read/write pretty JSON files safely (write to temp file, then move). */
public final class JsonFiles {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonFiles() {
    }

    public static JsonElement read(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            MyTestClient.LOGGER.error("Could not read {}", file, e);
            return null;
        }
    }

    public static boolean write(Path file, JsonElement json) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(json), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            MyTestClient.LOGGER.error("Could not write {}", file, e);
            return false;
        }
    }
}
