package com.mytestclient.finder;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Activity Radar: finds players beyond the range where you can see them.
 * <p>
 * The server tells your client about every block that changes in every chunk you have loaded
 * (your whole render distance), but it only sends other players' positions when they are close
 * (often 48-128 blocks). So when someone mines, builds or breaks blocks far away, the block
 * changes still reach you. Natural changes (water, leaves, crops, fire, grass spreading...) are
 * ignored; what's left is sorted into:
 * <ul>
 *     <li><b>mining</b>: stone/ores/deepslate turning into air underground (strip mining, caving),</li>
 *     <li><b>building</b>: crafted blocks appearing (planks, glass, torches, chests, beds...),</li>
 *     <li><b>breaking</b>: crafted blocks disappearing,</li>
 *     <li><b>redstone</b>: levers and buttons being used.</li>
 * </ul>
 * Changes are grouped per chunk into hotspots with a count and the last position.
 */
public final class Activity {
    public enum Kind {MINING, BUILDING, BREAKING, REDSTONE}

    public static final class Hotspot {
        public final String dim;
        public final int chunkX, chunkZ;
        public final int[] counts = new int[Kind.values().length];
        public BlockPos last;
        public long firstTime, lastTime;

        Hotspot(String dim, int chunkX, int chunkZ) {
            this.dim = dim;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        public int total() {
            int t = 0;
            for (int c : counts) t += c;
            return t;
        }

        public Kind main() {
            Kind best = Kind.MINING;
            for (Kind k : Kind.values()) if (counts[k.ordinal()] > counts[best.ordinal()]) best = k;
            return best;
        }

        public String describe() {
            StringBuilder sb = new StringBuilder();
            for (Kind k : Kind.values()) {
                if (counts[k.ordinal()] == 0) continue;
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(counts[k.ordinal()]).append(' ').append(k.name().toLowerCase());
            }
            return sb.toString();
        }
    }

    private static final Pattern NATURAL_STONE = Pattern.compile(
            "^(stone|deepslate|andesite|diorite|granite|tuff|calcite|netherrack|blackstone|basalt|end_stone|.*_ore|ancient_debris|smooth_basalt|dripstone_block)$");
    private static final Pattern CRAFTED = Pattern.compile(
            ".*(planks|_slab|_stairs|glass|_wool|concrete|bricks|torch|lantern|chest|barrel|furnace|smoker|crafting_table|_bed|_door|trapdoor"
                    + "|_fence|fence_gate|_wall|rail|redstone_wire|repeater|comparator|piston|observer|hopper|dropper|dispenser|_sign|carpet"
                    + "|ladder|cobblestone|obsidian|bookshelf|anvil|enchanting_table|shulker_box|beacon|scaffolding|iron_bars|chain|terracotta"
                    + "|_block|bell|campfire|lectern|loom|barrel|composter|grindstone|stonecutter|smithing_table|cartography|fletching|brewing).*");
    /** Things that change on their own and must never count. */
    private static final Pattern NOISE = Pattern.compile(
            ".*(leaves|fire|snow|ice|grass|dirt|farmland|vine|kelp|seagrass|bamboo|sugar_cane|cactus|chorus|copper|sculk|coral|dripstone"
                    + "|egg|frogspawn|concrete_powder|mycelium|moss|azalea|lichen|roots|sapling|wheat|carrots|potatoes|beetroots|stem|cocoa"
                    + "|berry|cave_vines|powder_snow|amethyst|propagule|nylium|water|lava|bubble_column|sand|gravel|anvil|mud|podzol|tripwire"
                    + "|target|sniffer|pitcher|torchflower|lily|dead_bush|short_grass|tall_grass|fern|flower|tulip|poppy|dandelion|daisy|orchid"
                    + "|allium|bluet|cornflower|rose|peony|lilac|sunflower|spore|honey|bee|scaffolding|redstone_ore|creaking|resin).*");

    private static final Map<Long, Hotspot> HOTSPOTS = new HashMap<>();
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private Activity() {
    }

    public static void clear() {
        HOTSPOTS.clear();
    }

    /** Forgets hotspots with no activity since {@code cutoff}. */
    public static void prune(long cutoff) {
        HOTSPOTS.values().removeIf(h -> h.lastTime < cutoff);
    }

    /** Classification per block, computed once (regexes are slow for every block update). */
    private static final java.util.IdentityHashMap<net.minecraft.block.Block, Integer> CLASS = new java.util.IdentityHashMap<>();
    private static final int C_STONE = 1, C_CRAFTED = 2, C_NOISE = 4, C_REDSTONE = 8;

    private static int classOf(net.minecraft.block.Block b) {
        Integer c = CLASS.get(b);
        if (c != null) return c;
        String id = Registries.BLOCK.getId(b).getPath();
        int v = 0;
        if (NATURAL_STONE.matcher(id).matches()) v |= C_STONE;
        if (CRAFTED.matcher(id).matches()) v |= C_CRAFTED;
        if (NOISE.matcher(id).matches()) v |= C_NOISE;
        if (id.equals("lever") || id.endsWith("_button")) v |= C_REDSTONE;
        CLASS.put(b, v);
        return v;
    }

    public static List<Hotspot> hotspots() {
        return new ArrayList<>(HOTSPOTS.values());
    }

    /** A block is about to change from {@code old} to {@code now} (client thread, before the change). */
    public static void onBlockChange(BlockPos pos, BlockState old, BlockState now, int minDistance) {
        if (mc.player == null || mc.world == null || old == now) return;
        if (pos.getSquaredDistance(mc.player.getBlockPos()) < (double) minDistance * minDistance) return; // ourselves
        if (!old.getFluidState().isEmpty() || !now.getFluidState().isEmpty()) return;
        int oc = classOf(old.getBlock()), nc = classOf(now.getBlock());
        Kind kind = null;
        if (old.getBlock() == now.getBlock()) {
            if ((nc & C_REDSTONE) != 0) kind = Kind.REDSTONE;
        } else if (now.isAir() && (oc & C_STONE) != 0) {
            int surface = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, pos.getX(), pos.getZ());
            if (pos.getY() < surface - 4) kind = Kind.MINING;
        } else if ((oc & C_NOISE) != 0 || (nc & C_NOISE) != 0) {
            return;
        } else if ((old.isAir() || old.isReplaceable()) && (nc & C_CRAFTED) != 0) {
            kind = Kind.BUILDING;
        } else if (now.isAir() && (oc & C_CRAFTED) != 0) {
            kind = Kind.BREAKING;
        }
        if (kind == null) return;
        String dim = mc.world.getRegistryKey().getValue().toString();
        long key = (((long) (pos.getX() >> 4)) << 32) ^ ((pos.getZ() >> 4) & 0xFFFFFFFFL) ^ ((long) dim.hashCode() << 52);
        Hotspot h = HOTSPOTS.computeIfAbsent(key, k -> new Hotspot(dim, pos.getX() >> 4, pos.getZ() >> 4));
        long now_ = System.currentTimeMillis();
        if (h.firstTime == 0) h.firstTime = now_;
        h.counts[kind.ordinal()]++;
        h.last = pos.toImmutable();
        h.lastTime = now_;
    }
}
