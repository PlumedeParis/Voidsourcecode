package com.mytestclient.waypoint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mytestclient.MyTestClient;
import com.mytestclient.config.JsonFiles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Stores waypoints for all worlds in {@code config/mytestclient/waypoints.json}. */
public class WaypointManager {
    public static final int[] PALETTE = {
            0xFF6C8CFF, 0xFF4ADE80, 0xFFFACC15, 0xFFF97316, 0xFFEF4444, 0xFFEC4899, 0xFFA855F7, 0xFF22D3EE, 0xFFFFFFFF
    };

    private final Path file;
    private final List<Waypoint> waypoints = new ArrayList<>();

    public WaypointManager(Path file) {
        this.file = file;
    }

    public void load() {
        waypoints.clear();
        JsonElement json = JsonFiles.read(file);
        if (json == null || !json.isJsonArray()) return;
        for (JsonElement element : json.getAsJsonArray()) {
            try {
                waypoints.add(Waypoint.fromJson(element.getAsJsonObject()));
            } catch (RuntimeException e) {
                MyTestClient.LOGGER.warn("Skipping invalid waypoint {}", element);
            }
        }
    }

    public void save() {
        JsonArray array = new JsonArray();
        for (Waypoint wp : waypoints) array.add(wp.toJson());
        JsonFiles.write(file, array);
    }

    public List<Waypoint> all() {
        return Collections.unmodifiableList(waypoints);
    }

    /** Waypoints belonging to the current server/save (any dimension). */
    public List<Waypoint> forCurrentWorld() {
        String key = currentWorldKey();
        List<Waypoint> result = new ArrayList<>();
        for (Waypoint wp : waypoints) if (wp.world.equals(key)) result.add(wp);
        return result;
    }

    public void add(Waypoint waypoint) {
        waypoints.add(waypoint);
        save();
    }

    public void remove(Waypoint waypoint) {
        waypoints.remove(waypoint);
        save();
    }

    public void removeIf(java.util.function.Predicate<Waypoint> filter) {
        if (waypoints.removeIf(filter)) save();
    }

    /** Identifies the current world: the save name in singleplayer, the address in multiplayer. */
    public static String currentWorldKey() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.isInSingleplayer() && mc.getServer() != null) {
            return "sp:" + mc.getServer().getSaveProperties().getLevelName();
        }
        ServerInfo info = mc.getCurrentServerEntry();
        return info != null ? "mp:" + info.address : "unknown";
    }

    public static String currentDimension() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.world == null ? "" : mc.world.getRegistryKey().getValue().toString();
    }
}
