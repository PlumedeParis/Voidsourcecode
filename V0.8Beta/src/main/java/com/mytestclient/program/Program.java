package com.mytestclient.program;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** A named program: its start key and its script (a list of blocks). */
public final class Program {
    public String name;
    /** GLFW key that starts/stops it, 0 = none. */
    public int key;
    public final List<ProgBlock> blocks = new ArrayList<>();

    public Program(String name) {
        this.name = name;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("key", key);
        o.add("blocks", ProgBlock.listToJson(blocks));
        return o;
    }

    public static Program fromJson(JsonObject o) {
        Program p = new Program(o.get("name").getAsString());
        if (o.has("key")) p.key = o.get("key").getAsInt();
        if (o.has("blocks")) ProgBlock.listFromJson(o.getAsJsonArray("blocks"), p.blocks);
        return p;
    }

    public Program copy(String newName) {
        Program p = new Program(newName);
        for (ProgBlock b : blocks) p.blocks.add(b.copy());
        return p;
    }
}
