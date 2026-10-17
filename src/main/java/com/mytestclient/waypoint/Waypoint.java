package com.mytestclient.waypoint;

import com.google.gson.JsonObject;

/** A named marker in a specific world + dimension. */
public class Waypoint {
    public String name;
    public int x;
    public int y;
    public int z;
    /** Dimension id, e.g. {@code minecraft:overworld}. */
    public String dimension;
    /** World key from {@link WaypointManager#currentWorldKey()} (server address or save name). */
    public String world;
    public int color;
    public boolean visible = true;
    public boolean death;

    public Waypoint(String name, int x, int y, int z, String dimension, String world, int color) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.world = world;
        this.color = color;
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", name);
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("z", z);
        obj.addProperty("dimension", dimension);
        obj.addProperty("world", world);
        obj.addProperty("color", String.format("#%08X", color));
        obj.addProperty("visible", visible);
        obj.addProperty("death", death);
        return obj;
    }

    public static Waypoint fromJson(JsonObject obj) {
        Waypoint wp = new Waypoint(
                obj.get("name").getAsString(),
                obj.get("x").getAsInt(),
                obj.get("y").getAsInt(),
                obj.get("z").getAsInt(),
                obj.get("dimension").getAsString(),
                obj.get("world").getAsString(),
                (int) Long.parseLong(obj.get("color").getAsString().replace("#", ""), 16));
        if (obj.has("visible")) wp.visible = obj.get("visible").getAsBoolean();
        if (obj.has("death")) wp.death = obj.get("death").getAsBoolean();
        return wp;
    }
}
