package com.mytestclient.util;

import com.mytestclient.MyTestClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Plans an elytra route before flying, so the autopilot knows where it will pass instead of
 * deciding at every wall.
 * <p>
 * The area between you and the target is cut into a grid (8 to 64 blocks per cell depending on
 * the distance) and searched with A*. What each cell contains comes from, best first:
 * <ol>
 *     <li>the chunks your client has loaded (exact),</li>
 *     <li>the world seed ({@code \setseed}, or singleplayer), using the real terrain generator
 *     (precise: the shape of the land, without trees and caves),</li>
 *     <li>nothing: unknown cells are assumed to be average terrain and re-planned when their
 *     chunks load.</li>
 * </ol>
 * Overworld / End: the cost of a cell is its distance plus how much you'd have to climb, so the
 * route goes around big mountains when that's shorter than flying over them.
 * Nether: every cell holds its free gaps (air at least 4 blocks high between the lava sea and the
 * bedrock roof); the route links gaps that overlap, so it follows real tunnels and open caverns.
 */
public final class RoutePlanner {
    public static final int NETHER_MIN = 34, NETHER_MAX = 121;
    /** Route height above the terrain in the Overworld/End. */
    public static final int CLEARANCE = 10;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VoidClient-Route");
        t.setDaemon(true);
        return t;
    });

    private RoutePlanner() {
    }

    /** A planned route: a polyline from the start to the target. */
    public static final class Route {
        public final List<Vec3d> points;
        private final double[] cumulative;
        public final int cell;
        public final boolean usedSeed;
        /** Cells on the route whose terrain was unknown (neither loaded nor from the seed). */
        public final int unknownCells;
        /** True when no full route was found and the end is a straight line. */
        public final boolean partial;
        public final long millis;

        Route(List<Vec3d> points, int cell, boolean usedSeed, int unknownCells, boolean partial, long millis) {
            this.points = points;
            this.cell = cell;
            this.usedSeed = usedSeed;
            this.unknownCells = unknownCells;
            this.partial = partial;
            this.millis = millis;
            cumulative = new double[points.size()];
            for (int i = 1; i < points.size(); i++) {
                Vec3d a = points.get(i - 1), b = points.get(i);
                cumulative[i] = cumulative[i - 1] + Math.hypot(b.x - a.x, b.z - a.z);
            }
        }

        /** Horizontal length in blocks. */
        public double length() {
            return cumulative[cumulative.length - 1];
        }

        /** Point at {@code s} blocks (horizontally) along the route; y is interpolated. */
        public Vec3d at(double s) {
            if (s <= 0) return points.get(0);
            if (s >= length()) return points.get(points.size() - 1);
            int lo = 0, hi = cumulative.length - 1;
            while (hi - lo > 1) {
                int mid = (lo + hi) >>> 1;
                if (cumulative[mid] <= s) lo = mid;
                else hi = mid;
            }
            double seg = cumulative[hi] - cumulative[lo];
            double t = seg < 1e-9 ? 0 : (s - cumulative[lo]) / seg;
            return points.get(lo).lerp(points.get(hi), t);
        }

        /** Like {@link #project} but measures the distance in 3D (Nether: tunnels above each other). */
        public double project3(Vec3d p, double hint) {
            double best = hint, bestDist = Double.MAX_VALUE;
            for (int i = 1; i < points.size(); i++) {
                if (cumulative[i] < hint - 48 || cumulative[i - 1] > hint + 128) continue;
                Vec3d a = points.get(i - 1), b = points.get(i);
                Vec3d ab = b.subtract(a);
                double len2 = ab.lengthSquared();
                double t = len2 < 1e-9 ? 0 : MathHelper.clamp(p.subtract(a).dotProduct(ab) / len2, 0, 1);
                double d = a.add(ab.multiply(t)).squaredDistanceTo(p);
                if (d < bestDist) {
                    bestDist = d;
                    best = cumulative[i - 1] + (cumulative[i] - cumulative[i - 1]) * t;
                }
            }
            return best;
        }

        /** The route from {@code s} to {@code s + len}, as its own corner points (for drawing). */
        public List<Vec3d> ahead(double s, double len) {
            List<Vec3d> out = new ArrayList<>();
            out.add(at(s));
            for (int i = 0; i < points.size(); i++) {
                if (cumulative[i] > s && cumulative[i] < s + len) out.add(points.get(i));
            }
            out.add(at(s + len));
            return out;
        }

        /** Direction of the route (horizontal unit vector) at {@code s}. */
        public Vec3d tangent(double s) {
            Vec3d a = at(s - 1), b = at(s + 1);
            double dx = b.x - a.x, dz = b.z - a.z, l = Math.hypot(dx, dz);
            return l < 1e-6 ? Vec3d.ZERO : new Vec3d(dx / l, 0, dz / l);
        }

        /** Distance along the route of the point closest (horizontally) to {@code p}, searched near {@code hint}. */
        public double project(Vec3d p, double hint) {
            double best = hint, bestDist = Double.MAX_VALUE;
            for (int i = 1; i < points.size(); i++) {
                if (cumulative[i] < hint - 64 || cumulative[i - 1] > hint + 256) continue;
                Vec3d a = points.get(i - 1), b = points.get(i);
                double dx = b.x - a.x, dz = b.z - a.z, len2 = dx * dx + dz * dz;
                double t = len2 < 1e-9 ? 0 : MathHelper.clamp(((p.x - a.x) * dx + (p.z - a.z) * dz) / len2, 0, 1);
                double cx = a.x + dx * t - p.x, cz = a.z + dz * t - p.z;
                double d = cx * cx + cz * cz;
                if (d < bestDist) {
                    bestDist = d;
                    best = cumulative[i - 1] + Math.sqrt(len2) * t;
                }
            }
            return best;
        }
    }

    /**
     * Starts planning (snapshot of loaded chunks on this thread, search on a worker thread).
     *
     * @param blocked positions where the autopilot got stuck: their cells are avoided
     */
    public static CompletableFuture<Route> plan(Vec3d from, Vec3d to, Long seed, Collection<BlockPos> blocked) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        boolean nether = world.getRegistryKey() == World.NETHER;
        boolean end = world.getRegistryKey() == World.END;
        SeedWorld.Dim dim = nether ? SeedWorld.Dim.NETHER : end ? SeedWorld.Dim.END : SeedWorld.Dim.OVERWORLD;

        double distance = Math.hypot(to.x - from.x, to.z - from.z);
        int cell = distance < 600 ? 8 : distance < 3000 ? 16 : distance < 12000 ? 32 : 64;
        int margin = (int) MathHelper.clamp(distance * 0.35, 96, 2048);
        Grid grid = new Grid(cell, nether, end,
                MathHelper.floor(Math.min(from.x, to.x) - margin) >> 4, MathHelper.floor(Math.min(from.z, to.z) - margin) >> 4,
                MathHelper.floor(Math.max(from.x, to.x) + margin) >> 4, MathHelper.floor(Math.max(from.z, to.z) + margin) >> 4);
        grid.snapshot(world);
        for (BlockPos b : blocked) grid.blocked.add(grid.key(Math.floorDiv(b.getX(), cell), Math.floorDiv(b.getZ(), cell)));

        return CompletableFuture.supplyAsync(() -> {
            long t0 = System.currentTimeMillis();
            if (seed != null) {
                try {
                    grid.seedWorld = SeedWorld.get(seed, dim);
                } catch (Throwable e) {
                    MyTestClient.LOGGER.warn("Route planner: no seed terrain", e);
                }
            }
            Route route = grid.search(from, to, t0);
            MyTestClient.LOGGER.info("Planned route: {} points, {} blocks, cell {}, {} ms", route.points.size(), (int) route.length(), cell, route.millis);
            return route;
        }, EXECUTOR);
    }

    // ------------------------------------------------------------------ grid + A*

    private static final class Grid {
        final int cell;
        final boolean nether, end;
        final int minCX, minCZ, maxCX, maxCZ;
        /** Overworld/End: per loaded chunk, the max height of each 4x4 quad (16 values). */
        final Map<Long, short[]> heights = new HashMap<>();
        /** Nether: per loaded chunk, the air bitmask of 4 columns (8-block spacing). */
        final Map<Long, long[][]> air = new HashMap<>();
        final Set<Long> blocked = new HashSet<>();
        final Map<Long, int[][]> gapCache = new HashMap<>();
        final Map<Long, long[]> columnCache = new HashMap<>();
        final Map<Long, Integer> heightCache = new HashMap<>();
        final Set<Long> unknown = new HashSet<>();
        SeedWorld seedWorld;
        boolean seedUsed;

        Grid(int cell, boolean nether, boolean end, int minCX, int minCZ, int maxCX, int maxCZ) {
            this.cell = cell;
            this.nether = nether;
            this.end = end;
            this.minCX = minCX;
            this.minCZ = minCZ;
            this.maxCX = maxCX;
            this.maxCZ = maxCZ;
        }

        long key(int gx, int gz) {
            return ChunkPos.toLong(gx, gz);
        }

        /** Copies what we need from the loaded chunks (client thread only). */
        void snapshot(ClientWorld world) {
            MinecraftClient mc = MinecraftClient.getInstance();
            int view = mc.options.getClampedViewDistance() + 2;
            int pcx = mc.player.getChunkPos().x, pcz = mc.player.getChunkPos().z;
            BlockPos.Mutable m = new BlockPos.Mutable();
            for (int cx = Math.max(minCX, pcx - view); cx <= Math.min(maxCX, pcx + view); cx++) {
                for (int cz = Math.max(minCZ, pcz - view); cz <= Math.min(maxCZ, pcz + view); cz++) {
                    if (!world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                    if (nether) {
                        long[][] cols = new long[4][];
                        for (int i = 0; i < 4; i++) {
                            int x = (cx << 4) + ((i & 1) == 0 ? 4 : 12), z = (cz << 4) + ((i & 2) == 0 ? 4 : 12);
                            long[] mask = new long[2];
                            for (int y = NETHER_MIN; y <= NETHER_MAX; y++) {
                                m.set(x, y, z);
                                BlockState s = world.getBlockState(m);
                                if (s.getCollisionShape(world, m).isEmpty() && s.getFluidState().isEmpty()) {
                                    mask[(y - NETHER_MIN) >> 6] |= 1L << ((y - NETHER_MIN) & 63);
                                }
                            }
                            cols[i] = mask;
                        }
                        air.put(ChunkPos.toLong(cx, cz), cols);
                    } else {
                        short[] q = new short[16];
                        for (int qx = 0; qx < 4; qx++) {
                            for (int qz = 0; qz < 4; qz++) {
                                int max = world.getBottomY();
                                for (int sx = 0; sx < 4; sx += 2) {
                                    for (int sz = 0; sz < 4; sz += 2) {
                                        max = Math.max(max, world.getTopY(Heightmap.Type.MOTION_BLOCKING, (cx << 4) + qx * 4 + sx + 1, (cz << 4) + qz * 4 + sz + 1));
                                    }
                                }
                                q[qx + qz * 4] = (short) max;
                            }
                        }
                        heights.put(ChunkPos.toLong(cx, cz), q);
                    }
                }
            }
        }

        boolean inBounds(int gx, int gz) {
            int x = gx * cell, z = gz * cell;
            return (x >> 4) >= minCX && (x >> 4) <= maxCX && (z >> 4) >= minCZ && (z >> 4) <= maxCZ;
        }

        /** Overworld/End: highest terrain in the cell. */
        int height(int gx, int gz) {
            long k = key(gx, gz);
            Integer cached = heightCache.get(k);
            if (cached != null) return cached;
            int x0 = gx * cell, z0 = gz * cell;
            int max = Integer.MIN_VALUE;
            for (int x = x0; x < x0 + cell; x += 4) {
                for (int z = z0; z < z0 + cell; z += 4) {
                    short[] q = heights.get(ChunkPos.toLong(x >> 4, z >> 4));
                    if (q != null) max = Math.max(max, q[((x & 15) >> 2) + ((z & 15) >> 2) * 4]);
                }
            }
            if (max == Integer.MIN_VALUE && seedWorld != null) {
                // shape of the land from the generator; +6 for trees it doesn't include
                max = seedWorld.surfaceY(x0 + cell / 2, z0 + cell / 2) + 6;
                if (cell >= 32) {
                    max = Math.max(max, seedWorld.surfaceY(x0 + cell / 4, z0 + cell / 4) + 6);
                    max = Math.max(max, seedWorld.surfaceY(x0 + 3 * cell / 4, z0 + 3 * cell / 4) + 6);
                }
                seedUsed = true;
            }
            if (max == Integer.MIN_VALUE) {
                unknown.add(k);
                max = end ? 70 : 90; // unknown: assume average hills
            }
            heightCache.put(k, max);
            return max;
        }

        /** Nether: the free gaps {from, to} (inclusive air) of the cell's centre column. */
        /** Air bits of a column (loaded snapshot, else seed terrain, else null = unknown). */
        long[] airMaskAt(int x, int z) {
            long[][] cols = air.get(ChunkPos.toLong(x >> 4, z >> 4));
            if (cols != null) return cols[((x & 15) >= 8 ? 1 : 0) + ((z & 15) >= 8 ? 2 : 0)];
            if (seedWorld == null) return null;
            // snap to a 4-block grid so neighbouring checks share samples
            int sx = x & ~3, sz = z & ~3;
            long key = ChunkPos.toLong(sx, sz);
            long[] m = columnCache.get(key);
            if (m == null) {
                m = seedWorld.airMask(sx + 2, sz + 2, NETHER_MIN, NETHER_MAX);
                columnCache.put(key, m);
                seedUsed = true;
            }
            return m;
        }

        /** Nether: room for a gliding player (feet..feet+2) at this column and height. Unknown = assumed free. */
        boolean freeAt(int x, int z, int feetY) {
            if (feetY < NETHER_MIN || feetY + 2 > NETHER_MAX) return false;
            long[] m = airMaskAt(x, z);
            if (m == null) return true;
            for (int y = feetY; y <= feetY + 2; y++) {
                if ((m[(y - NETHER_MIN) >> 6] >>> ((y - NETHER_MIN) & 63) & 1) == 0) return false;
            }
            return true;
        }

        /** Nether: is the straight segment free, checked every 3 blocks? */
        boolean segmentFree(Vec3d a, Vec3d b) {
            double len = Math.hypot(b.x - a.x, b.z - a.z);
            int steps = Math.max(1, (int) Math.ceil(len / 3));
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                double x = a.x + (b.x - a.x) * t, z = a.z + (b.z - a.z) * t, y = a.y + (b.y - a.y) * t;
                if (!freeAt(MathHelper.floor(x), MathHelper.floor(z), MathHelper.floor(y))) return false;
            }
            return true;
        }

        int[][] gaps(int gx, int gz) {
            long k = key(gx, gz);
            int[][] cached = gapCache.get(k);
            if (cached != null) return cached;
            int x = gx * cell + cell / 2, z = gz * cell + cell / 2;
            long[] mask = airMaskAt(x, z);
            int[][] result;
            if (mask == null) {
                unknown.add(k);
                result = new int[][]{{40, 110}};
            } else {
                List<int[]> list = new ArrayList<>();
                int start = -1;
                for (int y = NETHER_MIN; y <= NETHER_MAX + 1; y++) {
                    boolean free = y <= NETHER_MAX && (mask[(y - NETHER_MIN) >> 6] >>> ((y - NETHER_MIN) & 63) & 1) != 0;
                    if (free && start < 0) start = y;
                    if (!free && start >= 0) {
                        if (y - start >= 4) list.add(new int[]{start, y - 1});
                        start = -1;
                    }
                }
                result = list.toArray(new int[0][]);
            }
            gapCache.put(k, result);
            return result;
        }

        static final class Node {
            final int gx, gz, gap, y;
            final double g, f;
            final Node parent;

            Node(int gx, int gz, int gap, int y, double g, double f, Node parent) {
                this.gx = gx;
                this.gz = gz;
                this.gap = gap;
                this.y = y;
                this.g = g;
                this.f = f;
                this.parent = parent;
            }
        }

        Route search(Vec3d from, Vec3d to, long t0) {
            int sx = Math.floorDiv(MathHelper.floor(from.x), cell), sz = Math.floorDiv(MathHelper.floor(from.z), cell);
            int tx = Math.floorDiv(MathHelper.floor(to.x), cell), tz = Math.floorDiv(MathHelper.floor(to.z), cell);

            int startGap = 0, startY = (int) from.y;
            if (nether) {
                int[][] g = gaps(sx, sz);
                double best = Double.MAX_VALUE;
                for (int i = 0; i < g.length; i++) {
                    double d = from.y < g[i][0] ? g[i][0] - from.y : from.y > g[i][1] ? from.y - g[i][1] : 0;
                    if (d < best) {
                        best = d;
                        startGap = i;
                    }
                }
                if (g.length == 0) {
                    gapCache.put(key(sx, sz), new int[][]{{(int) from.y - 1, (int) from.y + 3}});
                    startGap = 0;
                }
                int[] gap = gaps(sx, sz)[startGap];
                startY = MathHelper.clamp((int) from.y, gap[0] + 1, Math.max(gap[0] + 1, gap[1] - 2));
            }

            PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
            Map<Long, Double> bestG = new HashMap<>();
            Node start = new Node(sx, sz, startGap, startY, 0, heuristic(sx, sz, tx, tz), null);
            open.add(start);
            bestG.put(nodeKey(sx, sz, startGap), 0.0);
            Node goal = null, closest = start;
            int pops = 0;
            while (!open.isEmpty()) {
                Node n = open.poll();
                if (n.gx == tx && n.gz == tz) {
                    goal = n;
                    break;
                }
                Double known = bestG.get(nodeKey(n.gx, n.gz, n.gap));
                if (known != null && known < n.g - 1e-9) continue;
                if (n.f - n.g < closest.f - closest.g) closest = n;
                if (++pops > 200_000 || System.currentTimeMillis() - t0 > 8000) break;

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        int nx = n.gx + dx, nz = n.gz + dz;
                        if (!inBounds(nx, nz) || blocked.contains(key(nx, nz))) continue;
                        double d = cell * (dx != 0 && dz != 0 ? Math.sqrt(2) : 1);
                        if (nether) {
                            int[] from_ = gaps(n.gx, n.gz)[n.gap];
                            int[][] next = gaps(nx, nz);
                            for (int i = 0; i < next.length; i++) {
                                int lo = Math.max(from_[0], next[i][0]) + 1, hi = Math.min(from_[1], next[i][1]) - 2;
                                if (hi < lo) continue; // gaps don't connect
                                int y = MathHelper.clamp(n.y, lo, hi);
                                // the wall between two cell centres can be thin: check the middle column too
                                int mx = (n.gx * cell + nx * cell) / 2 + cell / 2, mz = (n.gz * cell + nz * cell) / 2 + cell / 2;
                                if (!freeAt(mx, mz, y)) continue;
                                // stay a bit away from the floor and the ceiling of the new gap
                                int ny = MathHelper.clamp(y, next[i][0] + 1, Math.max(next[i][0] + 1, next[i][1] - 2));
                                double cost = d + 1.2 * Math.abs(n.y - ny) + (unknown.contains(key(nx, nz)) ? d * 0.15 : 0);
                                relax(open, bestG, n, nx, nz, i, ny, cost, tx, tz);
                            }
                        } else {
                            int ha = height(n.gx, n.gz), hb = height(nx, nz);
                            double cost = d + 1.5 * Math.max(0, hb - ha) + (unknown.contains(key(nx, nz)) ? d * 0.1 : 0);
                            relax(open, bestG, n, nx, nz, 0, hb + CLEARANCE, cost, tx, tz);
                        }
                    }
                }
            }

            boolean partial = goal == null;
            Node end = partial ? closest : goal;
            List<Node> cells = new ArrayList<>();
            for (Node n = end; n != null; n = n.parent) cells.add(0, n);

            List<Vec3d> points = new ArrayList<>();
            int unknownCells = 0;
            for (int i = 0; i < cells.size(); i++) {
                Node n = cells.get(i);
                if (unknown.contains(key(n.gx, n.gz))) unknownCells++;
                double y;
                if (nether) y = n.y;
                else {
                    // highest terrain of the cell and its route neighbours, so we climb in time
                    int h = Integer.MIN_VALUE;
                    for (int j = Math.max(0, i - 1); j <= Math.min(cells.size() - 1, i + 2); j++) h = Math.max(h, height(cells.get(j).gx, cells.get(j).gz));
                    y = h + CLEARANCE;
                }
                double x = n.gx * cell + cell / 2.0, z = n.gz * cell + cell / 2.0;
                if (i == 0) {
                    x = from.x;
                    z = from.z;
                }
                points.add(new Vec3d(x, y, z));
            }
            if (!partial) {
                Vec3d last = points.get(points.size() - 1);
                points.set(points.size() - 1, new Vec3d(to.x, last.y, to.z));
            } else {
                points.add(new Vec3d(to.x, nether ? MathHelper.clamp(to.y, 40, 110) : points.get(points.size() - 1).y, to.z));
            }
            if (points.size() == 1) points.add(new Vec3d(to.x, points.get(0).y, to.z));
            List<Vec3d> straight = nether ? straightenNether(points) : straightenSurface(points);
            return new Route(simplify(straight), cell, seedUsed, unknownCells, partial, System.currentTimeMillis() - t0);
        }

        /** Removes zigzags: jumps to the furthest point reachable in a straight, checked line. */
        private List<Vec3d> straightenNether(List<Vec3d> in) {
            if (in.size() < 3) return in;
            List<Vec3d> out = new ArrayList<>();
            int i = 0;
            out.add(in.get(0));
            while (i < in.size() - 1) {
                int next = i + 1;
                for (int j = Math.min(in.size() - 1, i + 16); j > i + 1; j--) {
                    if (segmentFree(in.get(i), in.get(j))) {
                        next = j;
                        break;
                    }
                }
                out.add(in.get(next));
                i = next;
            }
            return out;
        }

        /** Overworld/End: straight lines where the terrain under them stays below the flight height. */
        private List<Vec3d> straightenSurface(List<Vec3d> in) {
            if (in.size() < 3) return in;
            List<Vec3d> out = new ArrayList<>();
            int i = 0;
            out.add(in.get(0));
            while (i < in.size() - 1) {
                int next = i + 1;
                outer:
                for (int j = Math.min(in.size() - 1, i + 24); j > i + 1; j--) {
                    Vec3d a = in.get(i), b = in.get(j);
                    double len = Math.hypot(b.x - a.x, b.z - a.z);
                    int steps = Math.max(1, (int) Math.ceil(len / (cell / 2.0)));
                    for (int k = 1; k < steps; k++) {
                        double t = k / (double) steps;
                        int gx = Math.floorDiv(MathHelper.floor(a.x + (b.x - a.x) * t), cell);
                        int gz = Math.floorDiv(MathHelper.floor(a.z + (b.z - a.z) * t), cell);
                        if (height(gx, gz) + CLEARANCE * 0.6 > a.y + (b.y - a.y) * t) continue outer;
                    }
                    next = j;
                    break;
                }
                out.add(in.get(next));
                i = next;
            }
            return out;
        }

        private void relax(PriorityQueue<Node> open, Map<Long, Double> bestG, Node n, int nx, int nz, int gap, int y, double cost, int tx, int tz) {
            double g = n.g + cost;
            long k = nodeKey(nx, nz, gap);
            Double known = bestG.get(k);
            if (known != null && known <= g) return;
            bestG.put(k, g);
            open.add(new Node(nx, nz, gap, y, g, g + heuristic(nx, nz, tx, tz), n));
        }

        private double heuristic(int gx, int gz, int tx, int tz) {
            return Math.hypot(gx - tx, gz - tz) * cell * 0.999;
        }

        private static long nodeKey(int gx, int gz, int gap) {
            return ((long) gx & 0x3FFFFFFL) | (((long) gz & 0x3FFFFFFL) << 26) | ((long) gap << 52);
        }

        /** Drops points in the middle of straight, level stretches. */
        private static List<Vec3d> simplify(List<Vec3d> in) {
            if (in.size() < 3) return in;
            List<Vec3d> out = new ArrayList<>();
            out.add(in.get(0));
            for (int i = 1; i < in.size() - 1; i++) {
                Vec3d a = out.get(out.size() - 1), b = in.get(i), c = in.get(i + 1);
                double cross = (b.x - a.x) * (c.z - b.z) - (b.z - a.z) * (c.x - b.x);
                boolean straight = Math.abs(cross) < 1e-6 && (b.x - a.x) * (c.x - b.x) + (b.z - a.z) * (c.z - b.z) > 0;
                if (!straight || Math.abs(b.y - a.y) > 0.5 || Math.abs(c.y - b.y) > 0.5) out.add(b);
            }
            out.add(in.get(in.size() - 1));
            return out;
        }
    }
}
