package com.mytestclient.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mytestclient.MyTestClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.structure.Structure;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Finds structures from the world seed, using Minecraft's own placement algorithms.
 * <p>
 * The placement parameters (spacing, separation, salt, weights...) are read from the vanilla
 * data shipped inside the game jar ({@code data/minecraft/worldgen/structure_set/*.json}), so
 * they always match the installed version.
 * <ul>
 *     <li><b>Singleplayer</b>: the integrated server's real locate (exact, like {@code /locate}).</li>
 *     <li><b>Multiplayer</b> (seed from {@code \setseed}): the structure grid is computed exactly,
 *     but the client cannot run the biome generator, so it cannot check that the biome allows
 *     the structure. Nether fortresses/bastions are almost always right (bastions can't spawn in
 *     basalt deltas); strongholds are accurate to about 112 blocks; biome-dependent structures
 *     such as villages are "possible" spots only.</li>
 * </ul>
 */
public final class StructureLocator {
    private static final String[] SETS = {
            "villages", "desert_pyramids", "igloos", "jungle_temples", "swamp_huts", "pillager_outposts",
            "ocean_monuments", "woodland_mansions", "buried_treasures", "mineshafts", "ruined_portals",
            "shipwrecks", "ocean_ruins", "nether_complexes", "nether_fossils", "end_cities", "ancient_cities",
            "strongholds", "trail_ruins", "trial_chambers"
    };

    /** Friendly names -> structure id paths. */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("bastion", "bastion_remnant"),
            Map.entry("mansion", "mansion"),
            Map.entry("monument", "monument"),
            Map.entry("outpost", "pillager_outpost"),
            Map.entry("temple", "jungle_pyramid"),
            Map.entry("pyramid", "desert_pyramid"),
            Map.entry("witch_hut", "swamp_hut"),
            Map.entry("portal", "ruined_portal"),
            Map.entry("city", "ancient_city"),
            Map.entry("trial", "trial_chambers"),
            Map.entry("endcity", "end_city"));

    public record Entry(String id, int weight) {
    }

    public record Placement(String set, String type, int spacing, int separation, int salt, boolean triangular,
                            float frequency, String frequencyMethod, int distance, int count, int spread, List<Entry> structures) {
    }

    /** A located structure. {@code exact} = from the real generator; otherwise computed from the seed. */
    public record Result(String name, BlockPos pos, boolean exact, String note) {
    }

    private static Map<String, Placement> placements;
    private static long ringSeed = Long.MIN_VALUE;
    private static List<ChunkPos> ringCache = List.of();

    private StructureLocator() {
    }

    // ------------------------------------------------------------------ data

    private static synchronized Map<String, Placement> placements() {
        if (placements != null) return placements;
        placements = new LinkedHashMap<>();
        for (String set : SETS) {
            try (InputStream in = MinecraftClient.class.getResourceAsStream("/data/minecraft/worldgen/structure_set/" + set + ".json")) {
                if (in == null) continue;
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                placements.put(set, parse(set, root));
            } catch (Exception e) {
                MyTestClient.LOGGER.warn("Could not read structure set {}", set, e);
            }
        }
        if (placements.isEmpty()) {
            // fallback (1.21.x values) if the data could not be read
            placements.put("nether_complexes", new Placement("nether_complexes", "minecraft:random_spread", 27, 4, 30084232, false, 1f, "default", 0, 0, 0,
                    List.of(new Entry("minecraft:fortress", 2), new Entry("minecraft:bastion_remnant", 3))));
            placements.put("villages", new Placement("villages", "minecraft:random_spread", 34, 8, 10387312, false, 1f, "default", 0, 0, 0,
                    List.of(new Entry("minecraft:village_plains", 1))));
            placements.put("strongholds", new Placement("strongholds", "minecraft:concentric_rings", 0, 0, 0, false, 1f, "default", 32, 128, 3,
                    List.of(new Entry("minecraft:stronghold", 1))));
        }
        return placements;
    }

    private static Placement parse(String set, JsonObject root) {
        JsonObject p = root.getAsJsonObject("placement");
        List<Entry> entries = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("structures")) {
            JsonObject o = e.getAsJsonObject();
            entries.add(new Entry(o.get("structure").getAsString(), o.has("weight") ? o.get("weight").getAsInt() : 1));
        }
        return new Placement(set,
                p.get("type").getAsString(),
                p.has("spacing") ? p.get("spacing").getAsInt() : 0,
                p.has("separation") ? p.get("separation").getAsInt() : 0,
                p.has("salt") ? p.get("salt").getAsInt() : 0,
                p.has("spread_type") && p.get("spread_type").getAsString().equals("triangular"),
                p.has("frequency") ? p.get("frequency").getAsFloat() : 1f,
                p.has("frequency_reduction_method") ? p.get("frequency_reduction_method").getAsString() : "default",
                p.has("distance") ? p.get("distance").getAsInt() : 0,
                p.has("count") ? p.get("count").getAsInt() : 0,
                p.has("spread") ? p.get("spread").getAsInt() : 0,
                entries);
    }

    /** Names for autocompletion. */
    public static List<String> names() {
        Set<String> names = new LinkedHashSet<>(ALIASES.keySet());
        for (Placement p : placements().values()) {
            names.add(p.set);
            for (Entry e : p.structures) names.add(path(e.id));
        }
        names.add("village");
        names.add("fortress");
        names.add("stronghold");
        return new ArrayList<>(names);
    }

    public static boolean isStructureName(String query) {
        return !matching(query).isEmpty();
    }

    private static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    /** Structure ids matching a user query ("village", "fortress", "bastion", "villages"...). */
    private static Map<Placement, List<String>> matching(String query) {
        String q = query.toLowerCase(Locale.ROOT).replace(' ', '_');
        q = ALIASES.getOrDefault(q, q);
        Map<Placement, List<String>> result = new LinkedHashMap<>();
        for (Placement p : placements().values()) {
            List<String> ids = new ArrayList<>();
            for (Entry e : p.structures) {
                String path = path(e.id);
                if (p.set.equals(q) || path.equals(q) || path.startsWith(q + "_") || (q.length() >= 4 && path.contains(q))) ids.add(e.id);
            }
            if (!ids.isEmpty()) result.put(p, ids);
        }
        return result;
    }

    private static String dimensionOf(String structureId) {
        String path = path(structureId);
        if (path.equals("fortress") || path.equals("bastion_remnant") || path.equals("nether_fossil") || path.equals("ruined_portal_nether")) {
            return "minecraft:the_nether";
        }
        if (path.equals("end_city")) return "minecraft:the_end";
        return "minecraft:overworld";
    }

    // ------------------------------------------------------------------ exact (singleplayer)

    /** Real locate on the integrated server. Completes with null if nothing is found. */
    public static CompletableFuture<Result> locateExact(String query, BlockPos from) {
        MinecraftClient mc = MinecraftClient.getInstance();
        MinecraftServer server = mc.getServer();
        if (server == null || mc.world == null) return CompletableFuture.completedFuture(null);
        var worldKey = mc.world.getRegistryKey();
        Map<Placement, List<String>> match = matching(query);
        return server.submit(() -> {
            ServerWorld world = server.getWorld(worldKey);
            if (world == null) return null;
            Registry<Structure> registry = server.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
            List<RegistryEntry<Structure>> entries = new ArrayList<>();
            for (List<String> ids : match.values()) {
                for (String id : ids) registry.getEntry(Identifier.of(id)).ifPresent(entries::add);
            }
            if (entries.isEmpty()) return null;
            var pair = world.getChunkManager().getChunkGenerator().locateStructure(world, RegistryEntryList.of(entries), from, 100, false);
            if (pair == null) return null;
            String name = pair.getSecond().getKey().map(k -> path(k.getValue().toString())).orElse(query);
            return new Result(name, pair.getFirst(), true, "exact");
        });
    }

    // ------------------------------------------------------------------ from seed (multiplayer)

    /**
     * Nearest structure computed from the seed, or null. Uses the client-side world generator
     * ({@link SeedWorld}) so biome requirements are checked — results are real structures, not
     * just grid candidates. Falls back to the grid-only computation if the generator can't be built.
     */
    public static Result locateFromSeed(String query, long seed, BlockPos from, String dimension) {
        Set<String> ids = new LinkedHashSet<>();
        for (List<String> list : matching(query).values()) ids.addAll(list);
        if (ids.isEmpty()) return null;
        try {
            SeedWorld world = SeedWorld.get(seed, SeedWorld.Dim.of(dimension));
            SeedWorld.Found f = world.nearest(ids::contains, from.getX(), from.getZ(), 60);
            if (f == null) return null;
            String note = f.path().equals("stronghold")
                    ? "computed from the seed with the real generator (exact position of the start; dig down)"
                    : "computed from the seed with the real generator, biome checked";
            return new Result(f.path(), new BlockPos(f.x(), Math.max(f.y(), dimension.equals("minecraft:overworld") ? 63 : f.y()), f.z()), true, note);
        } catch (Throwable t) {
            MyTestClient.LOGGER.warn("Seed world generator unavailable, using grid-only search", t);
            return locateFromSeedGridOnly(query, seed, from, dimension);
        }
    }

    private static Result locateFromSeedGridOnly(String query, long seed, BlockPos from, String dimension) {
        Result best = null;
        double bestDist = Double.MAX_VALUE;
        for (Map.Entry<Placement, List<String>> m : matching(query).entrySet()) {
            Placement p = m.getKey();
            List<String> wanted = new ArrayList<>();
            for (String id : m.getValue()) if (dimensionOf(id).equals(dimension)) wanted.add(id);
            if (wanted.isEmpty()) continue;
            Result r = p.type.endsWith("concentric_rings") ? nearestRing(p, seed, from) : nearestSpread(p, wanted, seed, from);
            if (r == null) continue;
            double d = r.pos.getSquaredDistance(from);
            if (d < bestDist) {
                bestDist = d;
                best = r;
            }
        }
        return best;
    }

    private static Result nearestSpread(Placement p, List<String> wanted, long seed, BlockPos from) {
        int spacing = p.spacing;
        int fromChunkX = from.getX() >> 4, fromChunkZ = from.getZ() >> 4;
        int regionX = Math.floorDiv(fromChunkX, spacing), regionZ = Math.floorDiv(fromChunkZ, spacing);
        ChunkPos best = null;
        String bestId = null;
        double bestDist = Double.MAX_VALUE;
        for (int r = 0; r <= 40; r++) {
            // stop once this ring cannot contain anything closer than what we have
            double ringMin = Math.max(0, (r - 1) * spacing) * 16.0;
            if (best != null && ringMin * ringMin > bestDist) break;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    ChunkPos start = startChunk(p, seed, regionX + dx, regionZ + dz);
                    if (!passesFrequency(p, seed, start)) continue;
                    String id = pickStructure(p, seed, start);
                    if (!wanted.contains(id)) continue;
                    double ddx = start.x * 16 + 8 - from.getX(), ddz = start.z * 16 + 8 - from.getZ();
                    double d = ddx * ddx + ddz * ddz;
                    if (d < bestDist) {
                        bestDist = d;
                        best = start;
                        bestId = id;
                    }
                }
            }
        }
        if (best == null) return null;
        String path = path(bestId);
        boolean reliable = path.equals("fortress") || path.equals("bastion_remnant");
        String note = reliable
                ? (path.equals("bastion_remnant") ? "from seed (bastions can't be in basalt deltas: if it's a delta, it's a fortress)" : "from seed")
                : "possible spot from seed: the biome can't be checked client-side, it may not exist";
        int y = dimensionOf(bestId).equals("minecraft:the_nether") ? 64 : 80;
        return new Result(path, new BlockPos(best.x * 16 + 8, y, best.z * 16 + 8), false, note);
    }

    /** RandomSpreadStructurePlacement#getStartChunk */
    private static ChunkPos startChunk(Placement p, long seed, int regionX, int regionZ) {
        ChunkRandom random = new ChunkRandom(new CheckedRandom(0L));
        random.setRegionSeed(seed, regionX, regionZ, p.salt);
        int bound = p.spacing - p.separation;
        int ox, oz;
        if (p.triangular) {
            ox = (random.nextInt(bound) + random.nextInt(bound)) / 2;
            oz = (random.nextInt(bound) + random.nextInt(bound)) / 2;
        } else {
            ox = random.nextInt(bound);
            oz = random.nextInt(bound);
        }
        return new ChunkPos(regionX * p.spacing + ox, regionZ * p.spacing + oz);
    }

    private static boolean passesFrequency(Placement p, long seed, ChunkPos chunk) {
        if (p.frequency >= 1f) return true;
        ChunkRandom random = new ChunkRandom(new CheckedRandom(0L));
        if (p.frequencyMethod.equals("legacy_type_1")) {
            int i = chunk.x >> 4, j = chunk.z >> 4;
            random.setSeed((long) (i ^ j << 4) ^ seed);
            random.nextInt();
            return random.nextInt((int) (1f / p.frequency)) == 0;
        }
        random.setRegionSeed(seed, chunk.x, chunk.z, p.salt);
        return random.nextFloat() < p.frequency;
    }

    /** Weighted choice between the structures of a set (ChunkGenerator#setStructureStarts). */
    private static String pickStructure(Placement p, long seed, ChunkPos chunk) {
        if (p.structures.size() == 1) return p.structures.get(0).id;
        ChunkRandom random = new ChunkRandom(new CheckedRandom(0L));
        random.setCarverSeed(seed, chunk.x, chunk.z);
        int total = 0;
        for (Entry e : p.structures) total += e.weight;
        int roll = random.nextInt(total);
        for (Entry e : p.structures) {
            roll -= e.weight;
            if (roll < 0) return e.id;
        }
        return p.structures.get(p.structures.size() - 1).id;
    }

    /** ConcentricRingsStructurePlacement (strongholds), before the biome adjustment of up to 112 blocks. */
    private static Result nearestRing(Placement p, long seed, BlockPos from) {
        if (seed != ringSeed) {
            ringSeed = seed;
            List<ChunkPos> list = new ArrayList<>();
            Random random = new CheckedRandom(seed);
            double angle = random.nextDouble() * Math.PI * 2.0;
            int inRing = 0, ring = 0, spread = p.spread;
            for (int i = 0; i < p.count; i++) {
                double dist = (4 * p.distance + p.distance * ring * 6) + (random.nextDouble() - 0.5) * (p.distance * 2.5);
                int x = (int) Math.round(Math.cos(angle) * dist);
                int z = (int) Math.round(Math.sin(angle) * dist);
                random.split(); // vanilla splits a random for the biome search here
                list.add(new ChunkPos(x, z));
                angle += Math.PI * 2.0 / spread;
                if (++inRing == spread) {
                    ring++;
                    inRing = 0;
                    spread += 2 * spread / (ring + 1);
                    spread = Math.min(spread, p.count - i);
                    angle += random.nextDouble() * Math.PI * 2.0;
                }
            }
            ringCache = list;
        }
        ChunkPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (ChunkPos c : ringCache) {
            double dx = c.x * 16 + 8 - from.getX(), dz = c.z * 16 + 8 - from.getZ();
            double d = dx * dx + dz * dz;
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        if (best == null) return null;
        return new Result("stronghold", new BlockPos(best.x * 16 + 8, 40, best.z * 16 + 8), false,
                "from seed, accurate to ~112 blocks (the game shifts it to a suitable biome); use Eyes of Ender at the end");
    }
}
