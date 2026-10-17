package com.mytestclient.flight;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.MagmaBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Free space of the Nether at 2x2x2-block resolution, read from the real blocks of the chunks the
 * client has loaded (the same idea as Baritone's elytra pathfinder).
 * <p>
 * A cell is free when its 8 blocks have no collision, no liquid and nothing that burns. Each
 * chunk is 8x8 cells by 64 layers (y 0..127), stored as 64 longs (one bit per cell), computed when
 * the chunk loads and again when its blocks change. Arrays are replaced, never mutated, so the
 * path planner can read them from its own thread.
 */
public final class VoxelWorld {
    public static final int LAYERS = 64; // y 0..127 in 2-block layers

    private static final Map<Long, long[]> CHUNKS = new ConcurrentHashMap<>();
    private static final Set<Long> DIRTY = ConcurrentHashMap.newKeySet();
    private static final Map<BlockState, Boolean> PASSABLE = new IdentityHashMap<>();
    private static ClientWorld world;

    private VoxelWorld() {
    }

    private static boolean active(ClientWorld w) {
        return w != null && w.getRegistryKey() == net.minecraft.world.World.NETHER;
    }

    /** Cell mask of a chunk, or null if not loaded/computed. Thread-safe. */
    public static long[] chunk(int chunkX, int chunkZ) {
        return CHUNKS.get(ChunkPos.toLong(chunkX, chunkZ));
    }

    private static boolean passable(BlockState s) {
        Boolean b = PASSABLE.get(s);
        if (b == null) {
            b = s.getFluidState().isEmpty()
                    && !(s.getBlock() instanceof AbstractFireBlock) && !(s.getBlock() instanceof CampfireBlock) && !(s.getBlock() instanceof MagmaBlock)
                    && s.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).isEmpty();
            PASSABLE.put(s, b);
        }
        return b;
    }

    /** Client thread: a chunk was loaded. */
    public static void onChunkLoad(ClientWorld w, WorldChunk chunk) {
        if (w != world) {
            CHUNKS.clear();
            DIRTY.clear();
            world = w;
        }
        if (!active(w)) return;
        CHUNKS.put(chunk.getPos().toLong(), compute(chunk));
    }

    /** Client thread: a block changed; the chunk is recomputed on the next tick. */
    public static void onBlockChange(BlockPos pos) {
        if (active(world)) DIRTY.add(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4));
    }

    /** Client thread, every tick: recompute a few changed chunks, forget unloaded ones. */
    public static void tick() {
        ClientWorld w = MinecraftClient.getInstance().world;
        if (w != world) {
            CHUNKS.clear();
            DIRTY.clear();
            world = w;
        }
        if (!active(w)) return;
        int budget = 4;
        var it = DIRTY.iterator();
        while (it.hasNext() && budget-- > 0) {
            long key = it.next();
            it.remove();
            int cx = ChunkPos.getPackedX(key), cz = ChunkPos.getPackedZ(key);
            if (w.getChunkManager().isChunkLoaded(cx, cz)) CHUNKS.put(key, compute(w.getChunk(cx, cz)));
        }
        if (w.getTime() % 100 == 0) {
            CHUNKS.keySet().removeIf(k -> !w.getChunkManager().isChunkLoaded(ChunkPos.getPackedX(k), ChunkPos.getPackedZ(k)));
        }
    }

    private static long[] compute(WorldChunk chunk) {
        long[] layers = new long[LAYERS];
        ChunkSection[] sections = chunk.getSectionArray();
        int bottom = chunk.getBottomY();
        boolean[] free = new boolean[16 * 16 * 2];
        for (int cy = 0; cy < LAYERS; cy++) {
            int y0 = cy * 2;
            for (int dy = 0; dy < 2; dy++) {
                int y = y0 + dy;
                int si = (y - bottom) >> 4;
                ChunkSection s = si >= 0 && si < sections.length ? sections[si] : null;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        free[dy * 256 + z * 16 + x] = s == null || s.isEmpty() || passable(s.getBlockState(x, (y - bottom) & 15, z));
                    }
                }
            }
            long bits = 0;
            for (int cz = 0; cz < 8; cz++) {
                for (int cx = 0; cx < 8; cx++) {
                    boolean ok = true;
                    for (int dy = 0; dy < 2 && ok; dy++) {
                        for (int dz = 0; dz < 2 && ok; dz++) {
                            for (int dx = 0; dx < 2 && ok; dx++) {
                                ok = free[dy * 256 + (cz * 2 + dz) * 16 + cx * 2 + dx];
                            }
                        }
                    }
                    if (ok) bits |= 1L << (cx + cz * 8);
                }
            }
            layers[cy] = bits;
        }
        return layers;
    }

    /**
     * Is the cell free according to loaded chunks? 1 = free, 0 = blocked, -1 = unknown (not loaded).
     * Cell coordinates are block coordinates / 2.
     */
    public static int state(int cx, int cy, int cz) {
        if (cy < 0 || cy >= LAYERS) return 0;
        long[] c = CHUNKS.get(ChunkPos.toLong(cx >> 3, cz >> 3));
        if (c == null) return -1;
        return (c[cy] >>> ((cx & 7) + (cz & 7) * 8) & 1) != 0 ? 1 : 0;
    }
}
