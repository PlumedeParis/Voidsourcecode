package com.mytestclient.util;

import com.mytestclient.MyTestClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.chunk.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Seed Finder: searches for seeds where chosen structures are close to spawn (0 0) and/or the
 * spawn biome is a given one, e.g. {@code village 300 stronghold 2000 biome:cherry_grove}.
 * <ol>
 *     <li>Fast filter, no world generation: the structure grid (region start + frequency) is
 *     computed for each seed; seeds without a possible spot in range are dropped instantly.</li>
 *     <li>Check: the real generator of that seed confirms biomes, exclusion zones and the exact
 *     structure type (and stronghold rings, spawn biome).</li>
 * </ol>
 * Runs on its own thread; results are printed in chat as they are found.
 */
public final class SeedFinder {
    private record StructureCriterion(String query, Set<String> ids, int radius, SeedWorld.Dim dim, List<RegistryEntry<StructureSet>> sets) {
    }

    private static final AtomicBoolean running = new AtomicBoolean();
    private static volatile boolean stopRequested;

    private SeedFinder() {
    }

    public static boolean isRunning() {
        return running.get();
    }

    public static void stop() {
        stopRequested = true;
    }

    private record Parsed(List<StructureCriterion> structures, String biome, String error) {
    }

    /** Synchronous check of one seed (tests, debugging). @return what was found, or null */
    public static String check(long seed, String criteria) {
        Parsed p = parse(criteria);
        if (p.error() != null) throw new IllegalArgumentException(p.error());
        return quickFilter(seed, p.structures()) ? fullCheck(seed, p.structures(), p.biome()) : null;
    }

    /** Seeds per second of the grid-only filter (tests). */
    public static long quickFilterRate(String criteria, int seeds) {
        Parsed p = parse(criteria);
        long t = System.nanoTime();
        long passed = 0;
        for (int i = 0; i < seeds; i++) if (quickFilter(i * 0x9E3779B97F4A7C15L, p.structures())) passed++;
        MyTestClient.LOGGER.info("quick filter: {} of {} seeds passed", passed, seeds);
        return seeds * 1_000_000_000L / Math.max(1, System.nanoTime() - t);
    }

    /** Parses and starts a search. @return an error message, or null if started */
    public static String start(String criteria, int wanted, Long startSeed) {
        if (running.get()) return "a search is already running (\\seedfinder stop)";
        Parsed p = parse(criteria);
        if (p.error() != null) return p.error();
        final List<StructureCriterion> structures = p.structures();
        final String biomeId = p.biome();
        stopRequested = false;
        running.set(true);
        Thread thread = new Thread(() -> run(structures, biomeId, wanted, startSeed), "VoidClient-SeedFinder");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
        return null;
    }

    private static Parsed parse(String criteria) {
        List<StructureCriterion> structures = new ArrayList<>();
        String biome = null;
        String[] t = criteria.trim().toLowerCase(Locale.ROOT).split("\\s+");
        for (int i = 0; i < t.length; i++) {
            if (t[i].isEmpty()) continue;
            if (t[i].startsWith("biome:")) {
                biome = t[i].substring(6);
                if (!biome.contains(":")) biome = "minecraft:" + biome;
                continue;
            }
            String query = t[i];
            Set<String> ids = StructureLocator.idsFor(query);
            if (ids.isEmpty()) return new Parsed(null, null, "unknown structure '" + query + "'");
            int radius = 500;
            if (i + 1 < t.length) {
                try {
                    radius = Integer.parseInt(t[i + 1]);
                    i++;
                } catch (NumberFormatException ignored) {
                }
            }
            String dimId = StructureLocator.dimensionOf(ids.iterator().next());
            structures.add(new StructureCriterion(query, ids, radius, SeedWorld.Dim.of(dimId), new ArrayList<>()));
        }
        if (structures.isEmpty() && biome == null) {
            return new Parsed(null, null, "nothing to look for. Example: \\seedfinder village 300 stronghold 2000 biome:cherry_grove");
        }

        // structure sets containing each wanted structure (seed independent)
        var registry = SeedWorld.lookup().getOrThrow(RegistryKeys.STRUCTURE_SET);
        for (StructureCriterion c : structures) {
            registry.streamEntries().forEach(set -> {
                for (StructureSet.WeightedEntry e : set.value().structures()) {
                    if (c.ids().contains(SeedWorld.structureId(e.structure()))) {
                        c.sets().add(set);
                        return;
                    }
                }
            });
        }
        return new Parsed(structures, biome, null);
    }

    private static void say(String text, boolean good) {
        MinecraftClient.getInstance().execute(() -> {
            if (good) ChatUtil.success(text);
            else ChatUtil.info(text);
        });
    }

    private static void run(List<StructureCriterion> structures, String biome, int wanted, Long startSeed) {
        java.util.Random random = new java.util.Random();
        long seed = startSeed != null ? startSeed : random.nextLong();
        long checked = 0, deep = 0, lastReport = System.currentTimeMillis(), started = lastReport;
        int found = 0;
        try {
            while (!stopRequested && found < wanted) {
                seed = startSeed != null ? seed + 1 : random.nextLong();
                checked++;
                if (!quickFilter(seed, structures)) continue;
                deep++;
                String result = fullCheck(seed, structures, biome);
                if (result != null) {
                    found++;
                    say("Seed " + seed + ": " + result + "  (\\seedmap " + seed + ")", true);
                }
                long now = System.currentTimeMillis();
                if (now - lastReport > 10_000) {
                    lastReport = now;
                    say(String.format("Seed Finder: %,d seeds checked (%,d/s), %d found...", checked, checked * 1000 / Math.max(1, now - started), found), false);
                }
            }
            say(stopRequested ? "Seed Finder stopped (" + found + " found)." : "Seed Finder done: " + found + " seed(s) found in " + String.format("%,d", checked) + " checked.", true);
        } catch (Throwable t) {
            MyTestClient.LOGGER.error("Seed finder failed", t);
            say("Seed Finder error: " + t, false);
        } finally {
            running.set(false);
        }
    }

    /** Structure grid only: is there at least one possible start within each radius? */
    private static boolean quickFilter(long seed, List<StructureCriterion> structures) {
        for (StructureCriterion c : structures) {
            boolean any = false;
            for (RegistryEntry<StructureSet> set : c.sets()) {
                if (set.value().placement() instanceof ConcentricRingsStructurePlacement) {
                    any = true; // checked with the real generator
                    break;
                }
                if (!(set.value().placement() instanceof RandomSpreadStructurePlacement spread)) continue;
                if (!candidates(seed, spread, c.radius()).isEmpty()) {
                    any = true;
                    break;
                }
            }
            if (!any) return false;
        }
        return true;
    }

    /** Regions whose start chunk lies within {@code radius} blocks of 0 0. */
    private static List<int[]> candidates(long seed, RandomSpreadStructurePlacement spread, int radius) {
        List<int[]> out = new ArrayList<>();
        int spacing = spread.getSpacing(), regionBlocks = spacing * 16;
        int r = radius / regionBlocks + 1;
        for (int rx = -r; rx <= r; rx++) {
            for (int rz = -r; rz <= r; rz++) {
                ChunkPos start = spread.getStartChunk(seed, rx * spacing, rz * spacing);
                double x = start.getStartX() + 8, z = start.getStartZ() + 8;
                if (x * x + z * z > (double) radius * radius) continue;
                if (!spread.applyFrequencyReduction(start.x, start.z, seed)) continue;
                out.add(new int[]{rx, rz});
            }
        }
        return out;
    }

    /** Real generator check. @return a description of what was found, or null */
    private static String fullCheck(long seed, List<StructureCriterion> structures, String biome) {
        StringBuilder sb = new StringBuilder();
        SeedWorld overworld = null;
        if (biome != null) {
            overworld = SeedWorld.create(seed, SeedWorld.Dim.OVERWORLD);
            String at = SeedWorld.biomeId(overworld.biomeAt(0, 64, 0));
            if (!at.equals(biome)) return null;
            sb.append("spawn in ").append(BiomeColors.pretty(at));
        }
        for (StructureCriterion c : structures) {
            SeedWorld w = c.dim() == SeedWorld.Dim.OVERWORLD && overworld != null ? overworld : SeedWorld.create(seed, c.dim());
            if (c.dim() == SeedWorld.Dim.OVERWORLD) overworld = w;
            SeedWorld.Found best = null;
            double bestD = Double.MAX_VALUE;
            for (RegistryEntry<StructureSet> set : c.sets()) {
                if (set.value().placement() instanceof ConcentricRingsStructurePlacement) {
                    for (SeedWorld.Found f : w.ringPositions(set, 3)) {
                        double d = Math.hypot(f.x(), f.z());
                        if (d <= c.radius() && d < bestD) {
                            bestD = d;
                            best = f;
                        }
                    }
                } else if (set.value().placement() instanceof RandomSpreadStructurePlacement spread) {
                    for (int[] region : candidates(seed, spread, c.radius())) {
                        SeedWorld.Found f = w.inRegion(set, region[0], region[1]);
                        if (f == null || !c.ids().contains(f.id())) continue;
                        double d = Math.hypot(f.x(), f.z());
                        if (d <= c.radius() && d < bestD) {
                            bestD = d;
                            best = f;
                        }
                    }
                }
            }
            if (best == null) return null;
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(BiomeColors.pretty(best.id())).append(" at ").append(best.x()).append(' ').append(best.z())
                    .append(" (").append(Math.round(bestD)).append("m)");
        }
        return sb.toString();
    }
}
