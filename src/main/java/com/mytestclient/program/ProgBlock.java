package com.mytestclient.program;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One block of a program: its type, its field values, and the blocks inside it (C-blocks). */
public final class ProgBlock {
    public String type;
    public final Map<String, String> args = new LinkedHashMap<>();
    public final List<ProgBlock> body = new ArrayList<>();
    public final List<ProgBlock> elseBody = new ArrayList<>();

    public ProgBlock(String type) {
        this.type = type;
    }

    /** New block with the default values of its type. */
    public static ProgBlock create(String type) {
        ProgBlock b = new ProgBlock(type);
        BlockTypes.Def def = BlockTypes.get(type);
        if (def != null) {
            def.params().forEach((k, p) -> b.args.put(k, p.def()));
            if (def.params().containsKey("cond")) BlockTypes.applyConditionDefaults(b, b.args.get("cond"));
        }
        return b;
    }

    public String arg(String key) {
        return args.getOrDefault(key, "");
    }

    public ProgBlock copy() {
        ProgBlock c = new ProgBlock(type);
        c.args.putAll(args);
        for (ProgBlock b : body) c.body.add(b.copy());
        for (ProgBlock b : elseBody) c.elseBody.add(b.copy());
        return c;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        JsonObject a = new JsonObject();
        args.forEach(a::addProperty);
        o.add("args", a);
        if (!body.isEmpty()) o.add("body", listToJson(body));
        if (!elseBody.isEmpty()) o.add("else", listToJson(elseBody));
        return o;
    }

    public static JsonArray listToJson(List<ProgBlock> list) {
        JsonArray arr = new JsonArray();
        for (ProgBlock b : list) arr.add(b.toJson());
        return arr;
    }

    public static ProgBlock fromJson(JsonObject o) {
        ProgBlock b = create(o.get("type").getAsString());
        if (o.has("args")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("args").entrySet()) b.args.put(e.getKey(), e.getValue().getAsString());
        }
        if (o.has("body")) listFromJson(o.getAsJsonArray("body"), b.body);
        if (o.has("else")) listFromJson(o.getAsJsonArray("else"), b.elseBody);
        return b;
    }

    public static void listFromJson(JsonArray arr, List<ProgBlock> into) {
        for (JsonElement e : arr) {
            try {
                ProgBlock b = fromJson(e.getAsJsonObject());
                if (BlockTypes.get(b.type) != null) into.add(b);
            } catch (Exception ignored) {
                // unknown/broken block: skipped
            }
        }
    }
}
