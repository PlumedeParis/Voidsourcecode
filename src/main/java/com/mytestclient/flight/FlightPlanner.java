package com.mytestclient.flight;

import com.mytestclient.MyTestClient;
import com.mytestclient.util.RoutePlanner;
import com.mytestclient.util.SeedWorld;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Nether elytra path planner, Baritone style: weighted A* through the free 2x2x2 cells of
 * {@link VoxelWorld} (26 directions, no corner cutting, keeps away from walls).
 * <ul>
 *     <li>Loaded chunks: their real blocks.</li>
 *     <li>Unloaded chunks: the seed's terrain if known ({@code \seed nether ...}), otherwise assumed
 *     free; the autopilot re-plans as soon as loaded terrain contradicts the path.</li>
 * </ul>
 * The result is straightened into long, verified straight lines and returned as a 3D route.
 */
public final class FlightPlanner {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VoidClient-FlightPlanner");
        t.setDaemon(true);
        return t;
    });
    private static final int MAX_NODES = 2_500_000;
    private static final long MAX_MILLIS = 6000;
    private static final double WEIGHT = 1.6;

    /** Seed terrain masks per chunk, for the current seed only. */
    private static final Map<Long, long[]> SEED_CHUNKS = new ConcurrentHashMap<>();
    private static volatile long seedOfCache = Long.MIN_VALUE;

    private FlightPlanner() {
    }

    public static CompletableFuture<RoutePlanner.Route> plan(Vec3d from, Vec3d to, Long seed, Collection<BlockPos> blocked) {
        Set<Long> blockedCells = new HashSet<>();
        for (BlockPos b : blocked) blockedCells.add(key(b.getX() >> 1, MathHelper.clamp(b.getY() >> 1, 0, 63), b.getZ() >> 1));
        return CompletableFuture.supplyAsync(() -> new Search(from, to, seed, blockedCells).run(), EXECUTOR);
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) | (((long) z & 0x3FFFFFFL) << 26) | ((long) y << 52);
    }

    private static final int[][] DIRS;

    static {
        List<int[]> d = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++)
                    if (dx != 0 || dy != 0 || dz != 0) d.add(new int[]{dx, dy, dz});
        DIRS = d.toArray(new int[0][]);
    }

    private static final class Search {
        final Vec3d from, to;
        final Long seed;
        final Set<Long> blocked;
        SeedWorld seedWorld;
        boolean usedSeed;
        int unknown;

        // node storage (arrays instead of objects: millions of nodes)
        int[] nx = new int[1 << 16], ny = new int[1 << 16], nz = new int[1 << 16], parent = new int[1 << 16];
        double[] g = new double[1 << 16], f = new double[1 << 16];
        boolean[] closed = new boolean[1 << 16];
        int count;
        final Long2IntOpenHashMap index = new Long2IntOpenHashMap();
        // binary heap of node ids by f
        int[] heap = new int[1 << 16];
        int heapSize;

        Search(Vec3d from, Vec3d to, Long seed, Set<Long> blocked) {
            this.from = from;
            this.to = to;
            this.seed = seed;
            this.blocked = blocked;
            index.defaultReturnValue(-1);
        }

        // -------------------------------------------------------------- free space

        /** 1 free, 0 blocked. Unknown terrain: seed if known, else assumed free. */
        boolean free(int x, int y, int z) {
            if (y < 1 || y > 60) return false; // keep off the lava sea floor layer and the bedrock roof
            if (blocked.contains(key(x, y, z))) return false;
            int s = VoxelWorld.state(x, y, z);
            if (s >= 0) return s == 1;
            if (seedWorld != null) {
                long[] m = seedChunk(x >> 3, z >> 3);
                usedSeed = true;
                return (m[y] >>> ((x & 7) + (z & 7) * 8) & 1) != 0;
            }
            unknown = 1;
            return true;
        }

        long[] seedChunk(int cx, int cz) {
            long k = ChunkPos.toLong(cx, cz);
            long[] m = SEED_CHUNKS.get(k);
            if (m != null) return m;
            m = new long[VoxelWorld.LAYERS];
            // one terrain column per 4x4 blocks (2x2 cells)
            for (int qz = 0; qz < 4; qz++) {
                for (int qx = 0; qx < 4; qx++) {
                    long[] air = seedWorld.airMask((cx << 4) + qx * 4 + 2, (cz << 4) + qz * 4 + 2, 0, 127);
                    for (int cy = 0; cy < VoxelWorld.LAYERS; cy++) {
                        int y0 = cy * 2;
                        boolean ok = (air[y0 >> 6] >>> (y0 & 63) & 1) != 0 && (air[(y0 + 1) >> 6] >>> ((y0 + 1) & 63) & 1) != 0;
                        if (!ok) continue;
                        for (int dz = 0; dz < 2; dz++) {
                            for (int dx = 0; dx < 2; dx++) m[cy] |= 1L << (qx * 2 + dx + (qz * 2 + dz) * 8);
                        }
                    }
                }
            }
            if (SEED_CHUNKS.size() > 40_000) SEED_CHUNKS.clear();
            SEED_CHUNKS.put(k, m);
            return m;
        }

        boolean nearWall(int x, int y, int z) {
            return !free(x + 1, y, z) || !free(x - 1, y, z) || !free(x, y + 1, z) || !free(x, y - 1, z) || !free(x, y, z + 1) || !free(x, y, z - 1);
        }

        // -------------------------------------------------------------- A*

        int node(int x, int y, int z) {
            long k = key(x, y, z);
            int i = index.get(k);
            if (i >= 0) return i;
            if (count == nx.length) grow();
            i = count++;
            nx[i] = x;
            ny[i] = y;
            nz[i] = z;
            g[i] = Double.MAX_VALUE;
            parent[i] = -1;
            index.put(k, i);
            return i;
        }

        void grow() {
            int n = nx.length * 2;
            nx = java.util.Arrays.copyOf(nx, n);
            ny = java.util.Arrays.copyOf(ny, n);
            nz = java.util.Arrays.copyOf(nz, n);
            parent = java.util.Arrays.copyOf(parent, n);
            g = java.util.Arrays.copyOf(g, n);
            f = java.util.Arrays.copyOf(f, n);
            closed = java.util.Arrays.copyOf(closed, n);
        }

        void push(int i) {
            if (heapSize == heap.length) heap = java.util.Arrays.copyOf(heap, heap.length * 2);
            int p = heapSize++;
            heap[p] = i;
            while (p > 0) {
                int up = (p - 1) >> 1;
                if (f[heap[up]] <= f[heap[p]]) break;
                int t = heap[up];
                heap[up] = heap[p];
                heap[p] = t;
                p = up;
            }
        }

        int pop() {
            int top = heap[0];
            heap[0] = heap[--heapSize];
            int p = 0;
            while (true) {
                int l = p * 2 + 1, r = l + 1, m = p;
                if (l < heapSize && f[heap[l]] < f[heap[m]]) m = l;
                if (r < heapSize && f[heap[r]] < f[heap[m]]) m = r;
                if (m == p) break;
                int t = heap[m];
                heap[m] = heap[p];
                heap[p] = t;
                p = m;
            }
            return top;
        }

        double h(int x, int y, int z, int tx, int ty, int tz) {
            double dx = x - tx, dz = z - tz, dy = (y - ty) * 0.3;
            return Math.sqrt(dx * dx + dz * dz + dy * dy) * WEIGHT;
        }

        RoutePlanner.Route run() {
            long t0 = System.currentTimeMillis();
            if (seed != null) {
                try {
                    if (seed != seedOfCache) {
                        SEED_CHUNKS.clear();
                        seedOfCache = seed;
                    }
                    seedWorld = SeedWorld.get(seed, SeedWorld.Dim.NETHER);
                } catch (Throwable e) {
                    MyTestClient.LOGGER.warn("Flight planner: no seed terrain", e);
                }
            }
            int sx = MathHelper.floor(from.x) >> 1, sy = MathHelper.clamp(MathHelper.floor(from.y + 0.3) >> 1, 1, 60), sz = MathHelper.floor(from.z) >> 1;
            // start from a free cell next to the player if the player's own cell straddles a block
            if (!free(sx, sy, sz)) {
                outer:
                for (int r = 1; r <= 5; r++)
                    for (int dy = -r; dy <= r; dy++)
                        for (int dx = -r; dx <= r; dx++)
                            for (int dz = -r; dz <= r; dz++)
                                if (free(sx + dx, sy + dy, sz + dz)) {
                                    sx += dx;
                                    sy += dy;
                                    sz += dz;
                                    break outer;
                                }
            }
            int tx = MathHelper.floor(to.x) >> 1, tz = MathHelper.floor(to.z) >> 1;
            int ty = MathHelper.clamp(MathHelper.floor(to.y) >> 1, 2, 58);

            int start = node(sx, sy, sz);
            g[start] = 0;
            f[start] = h(sx, sy, sz, tx, ty, tz);
            push(start);
            int best = start, goal = -1;
            double bestH = f[start];
            int expanded = 0;
            while (heapSize > 0) {
                int cur = pop();
                if (closed[cur]) continue;
                closed[cur] = true;
                int x = nx[cur], y = ny[cur], z = nz[cur];
                if (Math.abs(x - tx) <= 2 && Math.abs(z - tz) <= 2) {
                    goal = cur;
                    break;
                }
                double hh = f[cur] - g[cur];
                if (hh < bestH) {
                    bestH = hh;
                    best = cur;
                }
                if (++expanded > MAX_NODES || (expanded & 4095) == 0 && System.currentTimeMillis() - t0 > MAX_MILLIS) break;
                double wallCost = nearWall(x, y, z) ? 0.8 : 0;
                for (int[] d : DIRS) {
                    int ax = x + d[0], ay = y + d[1], az = z + d[2];
                    if (!free(ax, ay, az)) continue;
                    // no corner cutting: every partial step must be free too
                    if (d[0] != 0 && d[1] != 0 && !free(x + d[0], y + d[1], z)) continue;
                    if (d[0] != 0 && d[2] != 0 && !free(x + d[0], y, z + d[2])) continue;
                    if (d[1] != 0 && d[2] != 0 && !free(x, y + d[1], z + d[2])) continue;
                    int nonZero = (d[0] != 0 ? 1 : 0) + (d[1] != 0 ? 1 : 0) + (d[2] != 0 ? 1 : 0);
                    if (nonZero > 1 && ((d[0] != 0 && !free(x + d[0], y, z)) || (d[1] != 0 && !free(x, y + d[1], z)) || (d[2] != 0 && !free(x, y, z + d[2])))) continue;
                    double step = nonZero == 1 ? 1 : nonZero == 2 ? 1.4142 : 1.7321;
                    if (d[1] != 0) step *= 1.15; // slight preference for level flight
                    double ng = g[cur] + step * (1 + wallCost);
                    int n = node(ax, ay, az);
                    if (closed[n] || ng >= g[n]) continue;
                    g[n] = ng;
                    parent[n] = cur;
                    f[n] = ng + h(ax, ay, az, tx, ty, tz);
                    push(n);
                }
            }
            boolean partial = goal < 0;
            int end = partial ? best : goal;
            List<int[]> cells = new ArrayList<>();
            for (int i = end; i >= 0; i = parent[i]) cells.add(0, new int[]{nx[i], ny[i], nz[i]});
            List<Vec3d> pts = new ArrayList<>();
            pts.add(from);
            for (int[] c : cells) pts.add(center(c));
            if (!partial) pts.add(new Vec3d(to.x, pts.get(pts.size() - 1).y, to.z));
            if (pts.size() < 2) pts.add(new Vec3d(to.x, from.y, to.z));
            List<Vec3d> straight = straighten(pts);
            long ms = System.currentTimeMillis() - t0;
            MyTestClient.LOGGER.info("Flight planner: {} cells, {} expanded, {} ms, partial={}", cells.size(), expanded, ms, partial);
            return new RoutePlanner.Route(straight, 2, usedSeed, unknown, partial, ms, true);
        }

        static Vec3d center(int[] c) {
            return new Vec3d(c[0] * 2 + 1, c[1] * 2 + 0.7, c[2] * 2 + 1);
        }

        /** Is a straight line free for the hitbox (checked every half block, with the hitbox corners)? */
        boolean lineFree(Vec3d a, Vec3d b) {
            double len = a.distanceTo(b);
            int steps = Math.max(1, (int) Math.ceil(len * 2));
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                double x = a.x + (b.x - a.x) * t, y = a.y + (b.y - a.y) * t, z = a.z + (b.z - a.z) * t;
                for (int c = 0; c < 4; c++) {
                    double ox = (c & 1) == 0 ? -0.4 : 0.4, oz = (c & 2) == 0 ? -0.4 : 0.4;
                    if (!free(MathHelper.floor(x + ox) >> 1, MathHelper.floor(y) >> 1, MathHelper.floor(z + oz) >> 1)) return false;
                    if (!free(MathHelper.floor(x + ox) >> 1, MathHelper.floor(y + 0.7) >> 1, MathHelper.floor(z + oz) >> 1)) return false;
                }
            }
            return true;
        }

        List<Vec3d> straighten(List<Vec3d> in) {
            if (in.size() < 3) return in;
            List<Vec3d> out = new ArrayList<>();
            int i = 0;
            out.add(in.get(0));
            while (i < in.size() - 1) {
                int next = i + 1;
                // binary-ish search: try far first
                for (int j = Math.min(in.size() - 1, i + 64); j > i + 1; j -= Math.max(1, (j - i) / 6)) {
                    if (lineFree(in.get(i), in.get(j))) {
                        next = j;
                        break;
                    }
                }
                out.add(in.get(next));
                i = next;
            }
            return out;
        }
    }
}
