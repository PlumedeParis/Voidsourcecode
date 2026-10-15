package com.mytestclient.finder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Base Finder: scores every chunk you load by the blocks players place and the world never
 * generates (shulker boxes, ender chests, beacons, concrete, hoppers, signs, lit portals...),
 * plus common ones (chests, furnaces, beds, glass...) that only count in numbers, since villages
 * and dungeons have a few. Chunks above the threshold are reported as bases/stashes.
 */
public final class BaseScanner {
    public record Base(String dim, ChunkPos chunk, BlockPos center, int score, String summary, long time) {
    }

    private static final Map<String, Base> BASES = new HashMap<>();
    private static final Map<Block, Integer> WEIGHT = new HashMap<>();
    private static final Map<Block, String> LABEL = new HashMap<>();

    static {
        for (Block b : Registries.BLOCK) {
            String id = Registries.BLOCK.getId(b).getPath();
            int w = 0;
            String label = null;
            if (id.endsWith("shulker_box")) { w = 12; label = "shulker"; }
            else if (id.equals("ender_chest")) { w = 10; label = "ender chest"; }
            else if (id.equals("beacon")) { w = 15; label = "beacon"; }
            else if (id.equals("enchanting_table")) { w = 10; label = "enchanting table"; }
            else if (id.contains("anvil")) { w = 8; label = "anvil"; }
            else if (id.equals("nether_portal")) { w = 3; label = "lit portal"; }
            else if (id.endsWith("_concrete")) { w = 3; label = "concrete"; }
            else if (id.equals("hopper") || id.equals("observer") || id.equals("crafter") || id.equals("comparator")) { w = 5; label = "redstone"; }
            else if (id.equals("respawn_anchor") || id.equals("lodestone") || id.equals("conduit")) { w = 10; label = id.replace('_', ' '); }
            else if (id.endsWith("_sign") || id.endsWith("_hanging_sign")) { w = 6; label = "sign"; }
            else if (id.equals("netherite_block") || id.equals("diamond_block") || id.equals("emerald_block")) { w = 15; label = id.replace("_block", " block"); }
            else if (id.equals("slime_block") || id.equals("honey_block") || id.equals("note_block") || id.equals("jukebox")) { w = 4; label = id.replace('_', ' '); }
            else if (id.equals("chest") || id.equals("trapped_chest") || id.equals("barrel")) { w = 2; label = "chest"; }
            else if (id.equals("furnace") || id.equals("blast_furnace") || id.equals("smoker")) { w = 1; label = "furnace"; }
            else if (id.equals("crafting_table")) { w = 1; label = "crafting table"; }
            else if (id.endsWith("_bed")) { w = 2; label = "bed"; }
            else if (id.equals("glass") || id.endsWith("stained_glass")) { w = 1; label = "glass"; }
            else if (id.equals("spawner")) { w = -20; } // dungeon: chests are natural there
            if (w != 0) {
                WEIGHT.put(b, w);
                if (label != null) LABEL.put(b, label);
            }
        }
    }

    private BaseScanner() {
    }

    public static void clear() {
        BASES.clear();
    }

    public static List<Base> bases() {
        return new ArrayList<>(BASES.values());
    }

    /** Scores a freshly loaded chunk; returns the base if it is one and new. */
    public static Base scan(WorldChunk chunk, String dim, int threshold) {
        int score = 0, strong = 0;
        long sx = 0, sy = 0, sz = 0;
        int n = 0;
        Map<String, Integer> counts = new TreeMap<>();
        ChunkSection[] sections = chunk.getSectionArray();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection s = sections[i];
            if (s == null || s.isEmpty() || !s.hasAny(st -> WEIGHT.containsKey(st.getBlock()))) continue;
            int baseY = chunk.sectionIndexToCoord(i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState st = s.getBlockState(x, y, z);
                        Integer w = WEIGHT.get(st.getBlock());
                        if (w == null) continue;
                        score += w;
                        if (w >= 5) strong++;
                        String label = LABEL.get(st.getBlock());
                        if (label != null) counts.merge(label, 1, Integer::sum);
                        if (w > 0) {
                            sx += x;
                            sy += baseY + y;
                            sz += z;
                            n++;
                        }
                    }
                }
            }
        }
        if (n == 0 || score < threshold || strong == 0 && score < threshold * 2) return null;
        ChunkPos cp = chunk.getPos();
        BlockPos center = new BlockPos(cp.getStartX() + (int) (sx / n), (int) (sy / n), cp.getStartZ() + (int) (sz / n));
        StringBuilder summary = new StringBuilder();
        counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(5).forEach(e -> {
            if (!summary.isEmpty()) summary.append(", ");
            summary.append(e.getValue()).append(' ').append(e.getKey());
        });
        String key = dim + "|" + cp.x + "|" + cp.z;
        Base base = new Base(dim, cp, center, score, summary.toString(), System.currentTimeMillis());
        Base old = BASES.put(key, base);
        return old == null ? base : null;
    }
}
