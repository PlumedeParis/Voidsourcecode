package com.voidclient.program;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** A named program: its start key and its script (a list of blocks). */
public final class Program {
    public String name;
    /** GLFW key that starts/stops it, 0 = none. */
    public int key;
    public final List<ProgBlock> blocks = new ArrayList<>();
    /** What starts the program: args "trigger" (one of {@link #TRIGGERS}) and "value" (number or text). */
    public final ProgBlock hat = new ProgBlock("hat");

    public static final List<String> TRIGGERS = List.of("key is pressed", "health is below", "a player appears", "a totem pops",
            "chat says", "inventory is full", "I join a world", "every (seconds)");

    public String trigger() {
        String t = hat.arg("trigger");
        return TRIGGERS.contains(t) ? t : TRIGGERS.get(0);
    }

    /** Default value shown when a trigger is picked. */
    public static String defaultValue(String trigger) {
        return switch (trigger) {
            case "health is below" -> "8";
            case "chat says" -> "hello";
            case "every (seconds)" -> "60";
            default -> "";
        };
    }

    public Program(String name) {
        this.name = name;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("key", key);
        o.addProperty("trigger", trigger());
        o.addProperty("value", hat.arg("value"));
        o.add("blocks", ProgBlock.listToJson(blocks));
        return o;
    }

    public static Program fromJson(JsonObject o) {
        Program p = new Program(o.get("name").getAsString());
        if (o.has("key")) p.key = o.get("key").getAsInt();
        if (o.has("trigger")) p.hat.args.put("trigger", o.get("trigger").getAsString());
        if (o.has("value")) p.hat.args.put("value", o.get("value").getAsString());
        if (o.has("blocks")) ProgBlock.listFromJson(o.getAsJsonArray("blocks"), p.blocks);
        return p;
    }

    public Program copy(String newName) {
        Program p = new Program(newName);
        for (ProgBlock b : blocks) p.blocks.add(b.copy());
        p.hat.args.putAll(hat.args);
        return p;
    }

    /** Compact text form to share a program ("VOID1:..."): gzip + base64 of the JSON. */
    public String toShareCode() {
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(bytes)) {
                gz.write(toJson().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return "VOID1:" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        } catch (java.io.IOException e) {
            return "";
        }
    }

    /** @return the program in a share code, or null if the text is not one */
    public static Program fromShareCode(String code) {
        try {
            String c = code.trim();
            if (!c.startsWith("VOID1:")) return null;
            byte[] raw = java.util.Base64.getUrlDecoder().decode(c.substring(6));
            try (java.util.zip.GZIPInputStream gz = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(raw))) {
                byte[] json = gz.readNBytes(1 << 20); // 1 MB is far more than any program
                Program p = fromJson(com.google.gson.JsonParser.parseString(new String(json, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
                p.key = 0; // never bind a key from someone else's code
                return p;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
