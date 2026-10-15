package com.mytestclient.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mytestclient.MyTestClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.biome.source.TheEndBiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.structure.Structure;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A world generator for any seed, running entirely on the client.
 * <p>
 * Minecraft ships the code that builds all vanilla worldgen registries
 * ({@link BuiltinRegistries#createWrapperLookup()}): biomes, noise, terrain settings, structures
 * and structure sets. With them we create the same biome source and noise the server would use
 * for a seed — so biomes are exact — and we re-implement the structure placement checks
 * (grid, frequency, exclusion zones, weighted choice, biome tags read from the game's data,
 * stronghold rings) so structures are checked against their real biome requirements anywhere,
 * without being on the server.
 * <p>
 * Assumes a default world type (not amplified/large biomes/custom datapacks).
 */
public final class SeedWorld {
    public enum Dim {
        OVERWORLD("minecraft:overworld", -64, 384),
        NETHER("minecraft:the_nether", 0, 256),
        END("minecraft:the_end", 0, 256);

        public final String id;
        final int bottom, height;

        Dim(String id, int bottom, int height) {
            this.id = id;
            this.bottom = bottom;
            this.height = height;
        }

        public static Dim of(String id) {
            for (Dim d : values()) if (d.id.equals(id)) return d;
            return OVERWORLD;
        }
    }

    /** A structure found by the generator. */
    public record Found(String id, ChunkPos chunk, int x, int y, int z) {
        public String path() {
            return id.substring(id.indexOf(':') + 1);
        }
    }

    private static volatile RegistryWrapper.WrapperLookup lookup;
    private static final Map<String, SeedWorld> CACHE = new ConcurrentHashMap<>();

    public final long seed;
    public final Dim dim;
    private final BiomeSource biomeSource;
    private final NoiseConfig noise;
    private final MultiNoiseUtil.MultiNoiseSampler sampler;
    private final NoiseChunkGenerator generator;
    private final List<RegistryEntry<StructureSet>> sets;
    private final HeightLimitView heightView;
    /** Cached results per (structure set, region). */
    private final Map<Long, Found> regionCache = new ConcurrentHashMap<>();

    /** Builds (once, ~1-2 s) all vanilla worldgen registries. Thread-safe. */
    public static RegistryWrapper.WrapperLookup lookup() {
        if (lookup == null) {
            synchronized (SeedWorld.class) {
                if (lookup == null) {
                    long t = System.currentTimeMillis();
                    lookup = BuiltinRegistries.createWrapperLookup();
                    MyTestClient.LOGGER.info("Built worldgen registries in {} ms", System.currentTimeMillis() - t);
                }
            }
        }
        return lookup;
    }

    /** Cached generator for a seed and dimension. May take a moment the first time: call off the render thread. */
    public static SeedWorld get(long seed, Dim dim) {
        return CACHE.computeIfAbsent(seed + "|" + dim, k -> new SeedWorld(seed, dim));
    }

    private SeedWorld(long seed, Dim dim) {
        this.seed = seed;
        this.dim = dim;
        RegistryWrapper.WrapperLookup l = lookup();
        RegistryKey<ChunkGeneratorSettings> settingsKey = switch (dim) {
            case OVERWORLD -> ChunkGeneratorSettings.OVERWORLD;
            case NETHER -> ChunkGeneratorSettings.NETHER;
            case END -> ChunkGeneratorSettings.END;
        };
        RegistryEntry<ChunkGeneratorSettings> settings = l.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS).getOrThrow(settingsKey);
        this.biomeSource = switch (dim) {
            case OVERWORLD -> MultiNoiseBiomeSource.create(l.getOrThrow(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                    .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
            case NETHER -> MultiNoiseBiomeSource.create(l.getOrThrow(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                    .getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER));
            case END -> TheEndBiomeSource.createVanilla(l.getOrThrow(RegistryKeys.BIOME));
        };
        this.noise = NoiseConfig.create(settings.value(), l.getOrThrow(RegistryKeys.NOISE_PARAMETERS), seed);
        this.sampler = noise.getMultiNoiseSampler();
        this.generator = new NoiseChunkGenerator(biomeSource, settings);
        this.heightView = HeightLimitView.create(dim.bottom, dim.height);
        // structure sets that can generate in this dimension (some structure's biomes exist here)
        Set<String> here = new HashSet<>();
        for (RegistryEntry<Biome> b : biomeSource.getBiomes()) here.add(biomeId(b));
        List<RegistryEntry<StructureSet>> list = new ArrayList<>();
        l.getOrThrow(RegistryKeys.STRUCTURE_SET).streamEntries().forEach(set -> {
            for (StructureSet.WeightedEntry e : set.value().structures()) {
                Set<String> valid = validBiomes(e.structure().value());
                if (valid == null || valid.stream().anyMatch(here::contains)) {
                    list.add(set);
                    return;
                }
            }
        });
        this.sets = list;
    }

    // ------------------------------------------------------------------ biomes / terrain

    public RegistryEntry<Biome> biomeAt(int x, int y, int z) {
        return biomeSource.getBiome(x >> 2, y >> 2, z >> 2, sampler);
    }

    public static String biomeId(RegistryEntry<Biome> biome) {
        return biome.getKey().map(k -> k.getValue().toString()).orElse("minecraft:unknown");
    }

    /** Terrain surface height (before decoration), like the world generator sees it. */
    public int surfaceY(int x, int z) {
        return generator.getHeight(x, z, Heightmap.Type.WORLD_SURFACE_WG, heightView, noise);
    }

    /**
     * Which blocks of a column are air in the raw noise terrain (no carvers or features), as bits:
     * bit {@code i} = y {@code minY + i}. Used by the Nether route planner. Up to 128 blocks.
     */
    public long[] airMask(int x, int z, int minY, int maxY) {
        net.minecraft.world.gen.chunk.VerticalBlockSample column = generator.getColumnSample(x, z, heightView, noise);
        long[] mask = new long[2];
        for (int y = minY; y <= maxY && y - minY < 128; y++) {
            if (column.getState(y).isAir()) mask[(y - minY) >> 6] |= 1L << ((y - minY) & 63);
        }
        return mask;
    }

    // ------------------------------------------------------------------ structures

    public List<RegistryEntry<StructureSet>> structureSets() {
        return sets;
    }

    // ------------------------------------------------------------------ biome tags

    /**
     * Biome tags (e.g. which biomes allow villages) come from data packs, which only a server
     * loads; the built-in registries leave them unbound. We read the tag files shipped in the
     * game jar ({@code data/<ns>/tags/worldgen/biome/...}) and resolve nested tags ourselves.
     */
    private static final Map<String, Set<String>> BIOME_TAGS = new ConcurrentHashMap<>();

    public static Set<String> biomeTag(String tagId) {
        Set<String> cached = BIOME_TAGS.get(tagId);
        if (cached != null) return cached;
        Set<String> result = new HashSet<>();
        BIOME_TAGS.put(tagId, result); // guards against cycles while resolving
        String ns = tagId.contains(":") ? tagId.substring(0, tagId.indexOf(':')) : "minecraft";
        String path = tagId.substring(tagId.indexOf(':') + 1);
        try (InputStream in = MinecraftClient.class.getResourceAsStream("/data/" + ns + "/tags/worldgen/biome/" + path + ".json")) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement v : root.getAsJsonArray("values")) {
                    String value = v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString() : v.getAsString();
                    if (value.startsWith("#")) result.addAll(biomeTag(value.substring(1)));
                    else result.add(value.contains(":") ? value : "minecraft:" + value);
                }
            } else {
                MyTestClient.LOGGER.warn("Biome tag {} not found", tagId);
            }
        } catch (Exception e) {
            MyTestClient.LOGGER.warn("Could not read biome tag {}", tagId, e);
        }
        return result;
    }

    /** Biome ids a structure may generate in, or null if unknown (then any biome is accepted). */
    private static Set<String> validBiomes(Structure structure) {
        RegistryEntryList<Biome> list = structure.getValidBiomes();
        return list.getTagKey().map(tag -> biomeTag(tag.id().toString())).orElse(null);
    }

    public static String setId(RegistryEntry<StructureSet> set) {
        return set.getKey().map(k -> k.getValue().toString()).orElse("?");
    }

    public static String structureId(RegistryEntry<Structure> structure) {
        return structure.getKey().map(k -> k.getValue().toString()).orElse("?");
    }

    /** Height at which a structure checks its biome. */
    private int checkY(String id, int x, int z) {
        String path = id.substring(id.indexOf(':') + 1);
        return switch (path) {
            case "ancient_city" -> -27;
            case "trial_chambers" -> -30;
            case "mineshaft", "mineshaft_mesa" -> 30;
            case "fortress" -> 64;
            case "bastion_remnant" -> 33;
            case "nether_fossil" -> 40;
            default -> surfaceY(x, z);
        };
    }

    /** Whether a structure of the set would really generate at {@code start} (biome + terrain checks). */
    private Found validStructure(StructureSet set, ChunkPos start) {
        List<StructureSet.WeightedEntry> entries = new ArrayList<>(set.structures());
        if (entries.size() == 1) return check(entries.get(0), start);
        // weighted choice exactly like ChunkGenerator#setStructureStarts, retrying when the biome refuses
        ChunkRandom random = new ChunkRandom(new CheckedRandom(0L));
        random.setCarverSeed(seed, start.x, start.z);
        int total = 0;
        for (StructureSet.WeightedEntry e : entries) total += e.weight();
        while (!entries.isEmpty()) {
            int roll = random.nextInt(total);
            int index = 0;
            for (StructureSet.WeightedEntry e : entries) {
                roll -= e.weight();
                if (roll < 0) break;
                index++;
            }
            StructureSet.WeightedEntry chosen = entries.get(Math.min(index, entries.size() - 1));
            Found found = check(chosen, start);
            if (found != null) return found;
            entries.remove(chosen);
            total -= chosen.weight();
        }
        return null;
    }

    private Found check(StructureSet.WeightedEntry entry, ChunkPos start) {
        String id = structureId(entry.structure());
        int x = start.getStartX() + 8, z = start.getStartZ() + 8;
        int y = checkY(id, x, z);
        if (id.endsWith(":end_city") && y < 60) return null; // end cities need high ground
        Set<String> valid = validBiomes(entry.structure().value());
        if (valid != null && !valid.contains(biomeId(biomeAt(x, y, z)))) return null;
        return new Found(id, start, x, y, z);
    }

    /**
     * Structure of a random-spread set in a region (cached), or null if none generates there.
     */
    public Found inRegion(RegistryEntry<StructureSet> setEntry, int regionX, int regionZ) {
        StructurePlacement placement = setEntry.value().placement();
        if (!(placement instanceof RandomSpreadStructurePlacement spread)) return null;
        long key = ((long) setId(setEntry).hashCode() << 40) ^ ((long) regionX << 20) ^ (regionZ & 0xFFFFFL);
        Found cached = regionCache.get(key);
        if (cached != null) return cached.id.isEmpty() ? null : cached;
        ChunkPos start = spread.getStartChunk(seed, regionX * spread.getSpacing(), regionZ * spread.getSpacing());
        boolean generates = placement.applyFrequencyReduction(start.x, start.z, seed) && !excluded(setEntry, start);
        Found found = generates ? validStructure(setEntry.value(), start) : null;
        regionCache.put(key, found == null ? new Found("", start, 0, 0, 0) : found);
        return found;
    }

    /**
     * Exclusion zones (vanilla: pillager outposts never start within 10 chunks of a village grid
     * position). Mirrors StructurePlacement#applyExclusionZone.
     */
    private boolean excluded(RegistryEntry<StructureSet> setEntry, ChunkPos start) {
        if (!setId(setEntry).equals("minecraft:pillager_outposts")) return false;
        for (RegistryEntry<StructureSet> other : sets) {
            if (!setId(other).equals("minecraft:villages") || !(other.value().placement() instanceof RandomSpreadStructurePlacement v)) continue;
            int radius = 10, spacing = v.getSpacing();
            for (int rx = Math.floorDiv(start.x - radius, spacing); rx <= Math.floorDiv(start.x + radius, spacing); rx++) {
                for (int rz = Math.floorDiv(start.z - radius, spacing); rz <= Math.floorDiv(start.z + radius, spacing); rz++) {
                    ChunkPos village = v.getStartChunk(seed, rx * spacing, rz * spacing);
                    if (Math.abs(village.x - start.x) <= radius && Math.abs(village.z - start.z) <= radius) return true;
                }
            }
        }
        return false;
    }

    private final Map<String, List<Found>> ringCache = new ConcurrentHashMap<>();

    /**
     * Stronghold (concentric rings) positions, exactly like StructurePlacementCalculator: ring
     * angles from the seed, then each one moved to a preferred biome within 112 blocks using the
     * real biome source.
     */
    public List<Found> ringPositions(RegistryEntry<StructureSet> setEntry) {
        if (!(setEntry.value().placement() instanceof ConcentricRingsStructurePlacement rings)) return List.of();
        return ringCache.computeIfAbsent(setId(setEntry), k -> computeRings(setEntry, rings));
    }

    private List<Found> computeRings(RegistryEntry<StructureSet> setEntry, ConcentricRingsStructurePlacement rings) {
        String id = structureId(setEntry.value().structures().get(0).structure());
        Set<String> preferred = rings.getPreferredBiomes().getTagKey().map(t -> biomeTag(t.id().toString())).orElse(Set.of());
        List<Found> list = new ArrayList<>();
        int distance = rings.getDistance(), count = rings.getCount(), spread = rings.getSpread();
        net.minecraft.util.math.random.Random random = new CheckedRandom(seed);
        double angle = random.nextDouble() * Math.PI * 2.0;
        int inRing = 0, ring = 0;
        for (int i = 0; i < count; i++) {
            double dist = (4 * distance + distance * ring * 6) + (random.nextDouble() - 0.5) * (distance * 2.5);
            int chunkX = (int) Math.round(Math.cos(angle) * dist);
            int chunkZ = (int) Math.round(Math.sin(angle) * dist);
            net.minecraft.util.math.random.Random split = random.split();
            var located = biomeSource.locateBiome(chunkX * 16 + 8, 0, chunkZ * 16 + 8, 112,
                    b -> preferred.contains(biomeId(b)), split, sampler);
            ChunkPos c = located != null
                    ? new ChunkPos(located.getFirst().getX() >> 4, located.getFirst().getZ() >> 4)
                    : new ChunkPos(chunkX, chunkZ);
            list.add(new Found(id, c, c.getStartX() + 8, 40, c.getStartZ() + 8));
            angle += Math.PI * 2.0 / spread;
            if (++inRing == spread) {
                ring++;
                inRing = 0;
                spread += 2 * spread / (ring + 1);
                spread = Math.min(spread, count - i);
                angle += random.nextDouble() * Math.PI * 2.0;
            }
        }
        return list;
    }

    /** Nearest structure whose id path matches {@code matcher}, searching up to {@code maxRings} regions out. */
    public Found nearest(java.util.function.Predicate<String> matcher, int fromX, int fromZ, int maxRings) {
        Found best = null;
        double bestDist = Double.MAX_VALUE;
        for (RegistryEntry<StructureSet> set : structureSets()) {
            boolean relevant = false;
            for (StructureSet.WeightedEntry e : set.value().structures()) if (matcher.test(structureId(e.structure()))) relevant = true;
            if (!relevant) continue;
            StructurePlacement placement = set.value().placement();
            if (placement instanceof ConcentricRingsStructurePlacement) {
                for (Found f : ringPositions(set)) {
                    double d = sq(f.x - fromX, f.z - fromZ);
                    if (matcher.test(f.id) && d < bestDist) {
                        bestDist = d;
                        best = f;
                    }
                }
                continue;
            }
            if (!(placement instanceof RandomSpreadStructurePlacement spread)) continue;
            int spacing = spread.getSpacing();
            int rx0 = Math.floorDiv(fromX >> 4, spacing), rz0 = Math.floorDiv(fromZ >> 4, spacing);
            for (int r = 0; r <= maxRings; r++) {
                double ringMin = Math.max(0, r - 1) * spacing * 16.0;
                if (ringMin * ringMin > bestDist) break;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        Found f = inRegion(set, rx0 + dx, rz0 + dz);
                        if (f == null || !matcher.test(f.id)) continue;
                        double d = sq(f.x - fromX, f.z - fromZ);
                        if (d < bestDist) {
                            bestDist = d;
                            best = f;
                        }
                    }
                }
            }
        }
        return best;
    }

    private static double sq(double dx, double dz) {
        return dx * dx + dz * dz;
    }
}
