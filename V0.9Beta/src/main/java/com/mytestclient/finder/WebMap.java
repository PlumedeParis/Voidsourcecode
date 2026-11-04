package com.mytestclient.finder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mytestclient.MyTestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Reads player positions from the server's public web map, if it has one. Most big servers run
 * Dynmap, BlueMap, squaremap or Pl3xMap: a website showing the whole world live, players included,
 * which anyone can open in a browser. This does the same requests the website does.
 * <p>
 * Players can be hidden on those maps (invisible, vanished, sneaking, or the server turned
 * player markers off), and it only works if the map is reachable from the internet.
 */
public final class WebMap {
    public record PlayerPos(String name, double x, double y, double z, String world, String dimension) {
    }

    /** A map that answered, so refreshes go straight to it. */
    public record Source(String kind, String base, List<String> worlds) {
        @Override
        public String toString() {
            return kind + " at " + base;
        }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private WebMap() {
    }

    /** Base URLs to try for a server host (plus the user's own URL first). */
    public static List<String> candidates(String host, String custom) {
        Set<String> out = new LinkedHashSet<>();
        if (custom != null && !custom.isBlank()) {
            String c = custom.trim();
            if (!c.startsWith("http")) c = "http://" + c;
            out.add(c.replaceAll("/+$", ""));
        }
        if (host == null || host.isBlank()) return new ArrayList<>(out);
        String root = host;
        String[] parts = host.split("\\.");
        if (parts.length > 2 && !host.matches("[0-9.]+")) root = parts[parts.length - 2] + "." + parts[parts.length - 1];
        for (String port : List.of("8123", "8100", "8080", "8804", "8888")) out.add("http://" + host + ":" + port);
        if (!host.matches("[0-9.]+")) {
            out.add("https://map." + root);
            out.add("http://map." + root);
            out.add("https://" + root + "/map");
            out.add("https://dynmap." + root);
            out.add("https://bluemap." + root);
        }
        return new ArrayList<>(out);
    }

    /** Tries every candidate and map type; completes with the first one that answers, or null. */
    public static CompletableFuture<Source> discover(List<String> bases) {
        return CompletableFuture.supplyAsync(() -> {
            for (String base : bases) {
                Source s = tryDynmap(base);
                if (s == null) s = trySquaremap(base);
                if (s == null) s = tryBlueMap(base);
                if (s != null) {
                    MyTestClient.LOGGER.info("Player Finder: found {}", s);
                    return s;
                }
            }
            return null;
        });
    }

    /** Current player positions from a discovered map. */
    public static CompletableFuture<List<PlayerPos>> players(Source source) {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerPos> out = new ArrayList<>();
            switch (source.kind()) {
                case "Dynmap" -> {
                    for (String world : source.worlds()) {
                        JsonObject o = getJson(source.base() + "/up/world/" + world + "/0");
                        if (o == null || !o.has("players")) continue;
                        for (JsonElement e : o.getAsJsonArray("players")) {
                            JsonObject p = e.getAsJsonObject();
                            String w = str(p, "world", world);
                            if (w.equals("-some-other-bogus-world-")) continue; // dynmap's "hidden" marker
                            out.add(new PlayerPos(str(p, "name", "?"), num(p, "x"), num(p, "y"), num(p, "z"), w, dimension(w)));
                        }
                        break; // every world file lists all players
                    }
                }
                case "squaremap", "Pl3xMap" -> {
                    JsonObject o = getJson(source.base() + "/tiles/players.json");
                    if (o != null && o.has("players")) {
                        for (JsonElement e : o.getAsJsonArray("players")) {
                            JsonObject p = e.getAsJsonObject();
                            JsonObject pos = p.has("position") && p.get("position").isJsonObject() ? p.getAsJsonObject("position") : p;
                            String w = str(p, "world", "world");
                            out.add(new PlayerPos(str(p, "name", "?"), num(pos, "x"), pos.has("y") ? num(pos, "y") : 70, num(pos, "z"), w, dimension(w)));
                        }
                    }
                }
                case "BlueMap" -> {
                    for (String map : source.worlds()) {
                        JsonObject o = getJson(source.base() + "/maps/" + map + "/live/players.json");
                        if (o == null || !o.has("players")) continue;
                        for (JsonElement e : o.getAsJsonArray("players")) {
                            JsonObject p = e.getAsJsonObject();
                            if (p.has("foreign") && p.get("foreign").getAsBoolean()) continue; // listed on its own map
                            JsonObject pos = p.getAsJsonObject("position");
                            if (pos == null) continue;
                            out.add(new PlayerPos(str(p, "name", "?"), num(pos, "x"), num(pos, "y"), num(pos, "z"), map, dimension(map)));
                        }
                    }
                }
                default -> {
                }
            }
            return out;
        });
    }

    // ------------------------------------------------------------------ detection

    private static Source tryDynmap(String base) {
        JsonObject config = getJson(base + "/up/configuration");
        if (config == null || !config.has("worlds")) return null;
        List<String> worlds = new ArrayList<>();
        for (JsonElement w : config.getAsJsonArray("worlds")) {
            JsonObject o = w.getAsJsonObject();
            if (o.has("name")) worlds.add(o.get("name").getAsString());
        }
        return worlds.isEmpty() ? null : new Source("Dynmap", base, worlds);
    }

    private static Source trySquaremap(String base) {
        JsonObject o = getJson(base + "/tiles/players.json");
        if (o == null || !o.has("players")) return null;
        boolean pl3x = false;
        JsonArray players = o.getAsJsonArray("players");
        if (!players.isEmpty() && players.get(0).getAsJsonObject().has("position")) pl3x = true;
        return new Source(pl3x ? "Pl3xMap" : "squaremap", base, List.of());
    }

    private static Source tryBlueMap(String base) {
        JsonObject settings = getJson(base + "/settings.json");
        if (settings == null || !settings.has("maps")) return null;
        List<String> maps = new ArrayList<>();
        for (JsonElement m : settings.getAsJsonArray("maps")) {
            if (m.isJsonPrimitive()) maps.add(m.getAsString());
            else if (m.isJsonObject() && m.getAsJsonObject().has("id")) maps.add(m.getAsJsonObject().get("id").getAsString());
        }
        return maps.isEmpty() ? null : new Source("BlueMap", base, maps);
    }

    // ------------------------------------------------------------------ helpers

    private static JsonObject getJson(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;
            String body = response.body().trim();
            if (!body.startsWith("{")) return null;
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(JsonObject o, String key, String def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : def;
    }

    private static double num(JsonObject o, String key) {
        try {
            return o.has(key) ? o.get(key).getAsDouble() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Guesses the dimension from a world/map name. */
    public static String dimension(String world) {
        String w = world.toLowerCase(Locale.ROOT);
        if (w.contains("nether")) return "minecraft:the_nether";
        if (w.contains("end")) return "minecraft:the_end";
        return "minecraft:overworld";
    }
}
