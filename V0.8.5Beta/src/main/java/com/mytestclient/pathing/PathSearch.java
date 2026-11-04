package com.mytestclient.pathing;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Incremental A* over block positions (the player's feet). It runs a few thousand nodes per tick on
 * the client thread (so it reads the world safely) until it finds the goal, runs out of loaded
 * terrain or hits the node limit; then it returns the path to the closest node it reached.
 * <p>
 * Moves: walk (4 directions + diagonals), step up 1, fall down up to "max fall" (any height into
 * water), and, if allowed, break blocks in the way, dig straight down, place a block to cross a
 * gap (bridge) and jump-place to go straight up (pillar). Costs are in "blocks walked": breaking
 * costs its mining time, placing a fixed amount, so cheap detours are preferred.
 */
public final class PathSearch {
    public enum Status {RUNNING, FOUND, PARTIAL, FAILED}

    /** What the walker must do to enter a node. */
    public enum Action {WALK, BRIDGE, PILLAR, DIG_DOWN}

    public record Settings(boolean allowBreak, boolean allowPlace, int maxFall, int maxNodes) {
    }

    public static final class Node {
        public final BlockPos pos;
        public final Action action;
        double g, f, h;
        Node parent;

        Node(BlockPos pos, Action action) {
            this.pos = pos;
            this.action = action;
        }
    }

    private static final double PLACE_COST = 4, JUMP_COST = 0.5, BREAK_FACTOR = 0.25, LAVA_NEAR_COST = 6;
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private final Goal goal;
    private final Settings settings;
    private final boolean canPlace;
    private final PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
    private final Map<Long, Node> nodes = new HashMap<>();
    private final Node start;
    private Node best;
    private Node found;
    private int expanded;
    private Status status = Status.RUNNING;

    public PathSearch(BlockPos from, Goal goal, Settings settings) {
        this.goal = goal;
        this.settings = settings;
        this.canPlace = settings.allowPlace() && BlockInfo.throwawaySlot() >= 0;
        start = new Node(from, Action.WALK);
        start.h = goal.heuristic(from);
        start.f = start.h;
        nodes.put(from.asLong(), start);
        open.add(start);
        best = start;
    }

    public Status status() {
        return status;
    }

    public int expanded() {
        return expanded;
    }

    /** Runs up to {@code budget} expansions. */
    public Status step(int budget) {
        if (status != Status.RUNNING) return status;
        for (int i = 0; i < budget; i++) {
            Node n = open.poll();
            if (n == null || expanded >= settings.maxNodes()) {
                status = best != start && best.h < start.h - 1 ? Status.PARTIAL : Status.FAILED;
                return status;
            }
            if (n.g > nodes.get(n.pos.asLong()).g + 1e-9) continue; // stale entry
            if (goal.isDone(n.pos)) {
                found = n;
                status = Status.FOUND;
                return status;
            }
            expanded++;
            if (n.h < best.h || (n.h == best.h && n.g < best.g)) best = n;
            expand(n);
        }
        return status;
    }

    /** The path (start first) once FOUND or PARTIAL. */
    public List<Node> path() {
        Node end = found != null ? found : best;
        List<Node> list = new ArrayList<>();
        for (Node n = end; n != null; n = n.parent) list.add(0, n);
        return list;
    }

    // ------------------------------------------------------------------ moves

    private void expand(Node n) {
        BlockPos p = n.pos;
        boolean inWater = BlockInfo.isWater(p);
        for (Direction d : HORIZONTAL) {
            BlockPos t = p.offset(d);
            if (!BlockInfo.loaded(t)) continue;
            double body = clearCost(t) + clearCost(t.up());
            if (body == Double.POSITIVE_INFINITY) continue;

            if (BlockInfo.standable(t)) {
                add(n, t, Action.WALK, 1 + body + hazard(t));
            } else {
                // fall: find where we land
                boolean landed = false;
                if (body == 0) {
                    for (int k = 1; k <= 64; k++) {
                        BlockPos c = t.down(k);
                        if (!BlockInfo.passable(c)) break;
                        if (BlockInfo.isWater(c)) {
                            add(n, c, Action.WALK, 1 + k * 0.3 + hazard(c));
                            landed = true;
                            break;
                        }
                        if (BlockInfo.solid(c.down())) {
                            if (k <= settings.maxFall()) {
                                add(n, c, Action.WALK, 1 + k * 0.3 + hazard(c));
                                landed = true;
                            }
                            break;
                        }
                    }
                }
                // bridge: place a block under the next step
                if (!landed && canPlace && BlockInfo.state(t.down()).isReplaceable() && !BlockInfo.isLava(t.down())) {
                    add(n, t, Action.BRIDGE, 1 + body + PLACE_COST + hazard(t));
                }
            }

            // step up one block
            BlockPos u = t.up();
            if (BlockInfo.solid(t) || (inWater && BlockInfo.passable(t))) {
                double up = clearCost(p.up(2)) + clearCost(u) + clearCost(u.up());
                if (up != Double.POSITIVE_INFINITY && BlockInfo.standable(u)) add(n, u, Action.WALK, 1 + JUMP_COST + up + hazard(u));
            }
        }

        // diagonals: only through open space (no corner cutting through blocks)
        for (int i = 0; i < 4; i++) {
            Direction a = i < 2 ? Direction.NORTH : Direction.SOUTH, b = i % 2 == 0 ? Direction.EAST : Direction.WEST;
            BlockPos t = p.offset(a).offset(b);
            if (!BlockInfo.loaded(t)) continue;
            if (!open(p.offset(a)) || !open(p.offset(b)) || !open(t) || !BlockInfo.standable(t)) continue;
            add(n, t, Action.WALK, Math.sqrt(2) + hazard(t));
        }

        // straight up: pillar (or swim up)
        BlockPos up = p.up();
        if (inWater && BlockInfo.passable(up.up()) && BlockInfo.passable(up)) {
            add(n, up, Action.WALK, 1.5);
        } else if (canPlace && BlockInfo.standable(p)) {
            double c = clearCost(p.up(2));
            if (c != Double.POSITIVE_INFINITY) add(n, up, Action.PILLAR, 1 + PLACE_COST + c);
        }

        // straight down: dig (or sink in water)
        BlockPos down = p.down();
        if (BlockInfo.isWater(down) && BlockInfo.passable(down)) {
            add(n, down, Action.WALK, 1.2);
        } else if (settings.allowBreak() && BlockInfo.breakable(down) && !BlockInfo.isLava(down.down())
                && (BlockInfo.solid(down.down()) || BlockInfo.isWater(down.down()))) {
            add(n, down, Action.DIG_DOWN, 1 + BlockInfo.breakTicks(down) * BREAK_FACTOR + hazard(down));
        }
    }

    /** 0 if free, mining cost if breakable, infinite if blocked. */
    private double clearCost(BlockPos pos) {
        if (BlockInfo.passable(pos)) return 0;
        if (BlockInfo.isLava(pos)) return Double.POSITIVE_INFINITY;
        if (!settings.allowBreak() || !BlockInfo.breakable(pos)) return Double.POSITIVE_INFINITY;
        // gravel/sand above keeps falling in: expensive
        double extra = BlockInfo.isFalling(pos.up()) ? 6 : 0;
        return 1 + BlockInfo.breakTicks(pos) * BREAK_FACTOR + extra;
    }

    private static boolean open(BlockPos feet) {
        return BlockInfo.passable(feet) && BlockInfo.passable(feet.up());
    }

    /** Extra cost for standing next to lava. */
    private static double hazard(BlockPos feet) {
        for (Direction d : HORIZONTAL) {
            if (BlockInfo.isLava(feet.offset(d)) || BlockInfo.isLava(feet.offset(d).down())) return LAVA_NEAR_COST;
        }
        return BlockInfo.isLava(feet.down()) ? Double.POSITIVE_INFINITY : 0;
    }

    private void add(Node from, BlockPos pos, Action action, double cost) {
        if (cost == Double.POSITIVE_INFINITY || Double.isNaN(cost)) return;
        double g = from.g + cost;
        long key = pos.asLong();
        Node existing = nodes.get(key);
        if (existing != null && existing.g <= g) return;
        Node n = new Node(pos, action);
        n.g = g;
        n.h = goal.heuristic(pos);
        n.f = g + n.h * 1.1; // slightly greedy: much faster, paths stay near-optimal
        n.parent = from;
        nodes.put(key, n);
        open.add(n);
    }
}
