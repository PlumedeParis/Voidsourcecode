package com.mytestclient.pathing;

import com.mytestclient.module.Modules;
import com.mytestclient.module.movement.PathfinderModule;
import com.mytestclient.render.Overlay;
import com.mytestclient.util.CombatUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;

/**
 * Follows a {@link PathSearch} path by pressing the movement keys and turning the player, mining
 * whatever is in the way, bridging and pillaring when the path says so. Re-plans when it gets
 * stuck or pushed off the path, and keeps planning further when the goal is beyond the loaded
 * terrain (partial paths).
 */
public final class Walker {
    public enum State {IDLE, SEARCHING, WALKING, ARRIVED, FAILED}

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static State state = State.IDLE;
    private static Goal goal;
    private static PathSearch search;
    private static List<PathSearch.Node> path;
    private static boolean partial;
    private static int index, stuckTicks, offTicks, failures, searchTicks;
    private static BlockPos breaking;
    private static String failReason = "";

    private Walker() {
    }

    public static State state() {
        return state;
    }

    public static String failReason() {
        return failReason;
    }

    public static Goal goal() {
        return goal;
    }

    public static void walkTo(Goal g) {
        goal = g;
        failures = 0;
        state = State.SEARCHING;
        startSearch();
    }

    public static void stop() {
        if (state == State.IDLE) return;
        state = State.IDLE;
        goal = null;
        search = null;
        path = null;
        stopBreaking();
        releaseKeys();
    }

    private static PathSearch.Settings settings() {
        PathfinderModule m = Modules.get(PathfinderModule.class);
        return m != null ? m.searchSettings() : new PathSearch.Settings(true, true, 3, 150_000);
    }

    private static void startSearch() {
        ClientPlayerEntity p = mc.player;
        search = new PathSearch(feet(p), goal, settings());
        path = null;
        searchTicks = 0;
        state = State.SEARCHING;
    }

    public static BlockPos feet(ClientPlayerEntity p) {
        return BlockPos.ofFloored(p.getX(), p.getY() + 0.2, p.getZ());
    }

    /** Called every client tick (start of tick) while a task uses the walker. */
    public static void tick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || state == State.IDLE || state == State.ARRIVED || state == State.FAILED) return;
        BlockPos feet = feet(p);
        if (goal.isDone(feet) && (p.isOnGround() || p.isTouchingWater())) {
            state = State.ARRIVED;
            stopBreaking();
            releaseKeys();
            return;
        }

        if (state == State.SEARCHING) {
            releaseKeys();
            PathSearch.Status s = search.step(4000);
            searchTicks++;
            if (s == PathSearch.Status.RUNNING) return;
            if (s == PathSearch.Status.FAILED) {
                fail("no path found (blocked, or the area isn't loaded)");
                return;
            }
            path = search.path();
            partial = s == PathSearch.Status.PARTIAL;
            search = null;
            index = 0;
            stuckTicks = 0;
            offTicks = 0;
            state = State.WALKING;
            if (path.size() < 2) {
                fail("no path found");
                return;
            }
        }

        // ---- follow the path
        if (index + 1 >= path.size()) {
            // end of a partial path (or just arrived on the last node without the goal being done)
            if (++failures > 6) {
                fail("could not get closer");
                return;
            }
            startSearch();
            return;
        }
        // skip ahead if we're already further along
        for (int i = Math.min(path.size() - 1, index + 4); i > index; i--) {
            if (path.get(i).pos.equals(feet)) {
                index = i;
                stuckTicks = 0;
                failures = 0;
                break;
            }
        }
        if (index + 1 >= path.size()) return;
        PathSearch.Node cur = path.get(index), next = path.get(index + 1);

        // pushed off the path?
        if (!feet.equals(cur.pos) && !feet.equals(next.pos) && Math.sqrt(feet.getSquaredDistance(next.pos)) > 2.5) {
            if (++offTicks > 20) {
                startSearch();
                return;
            }
        } else offTicks = 0;

        if (++stuckTicks > 160) {
            if (++failures > 6) {
                fail("stuck");
                return;
            }
            startSearch();
            return;
        }

        // 1. clear the way
        for (BlockPos b : blocksToClear(cur.pos, next)) {
            if (!BlockInfo.passable(b) && !BlockInfo.isWater(b)) {
                releaseKeys();
                if (!BlockInfo.breakable(b)) {
                    startSearch();
                    return;
                }
                mine(b);
                stuckTicks = Math.max(0, stuckTicks - 1); // mining is progress
                return;
            }
        }
        stopBreaking();

        // 2. place what the path needs
        if (next.action == PathSearch.Action.BRIDGE && !BlockInfo.solid(next.pos.down())) {
            releaseKeys();
            mc.options.sneakKey.setPressed(true);
            int slot = BlockInfo.throwawaySlot();
            if (slot < 0 || !CombatUtil.place(next.pos.down(), slot, true)) {
                // too far from the edge to click: shuffle towards it
                face(next.pos);
                mc.options.forwardKey.setPressed(slot >= 0 && horizontalDistance(p, cur.pos) < 0.3);
                if (slot < 0) startSearch();
            }
            return;
        }
        if (next.action == PathSearch.Action.PILLAR) {
            releaseKeys();
            p.setPitch(90);
            mc.options.jumpKey.setPressed(true);
            if (p.getY() > cur.pos.getY() + 0.9 && BlockInfo.state(cur.pos).isReplaceable()) {
                int slot = BlockInfo.throwawaySlot();
                if (slot < 0) {
                    startSearch();
                    return;
                }
                CombatUtil.place(cur.pos, slot, false);
            }
            if (feet.equals(next.pos) && p.isOnGround()) advance();
            return;
        }

        // 3. move
        if (feet.equals(next.pos) && (p.isOnGround() || p.isTouchingWater()) && horizontalDistance(p, next.pos) < 0.45) {
            advance();
            return;
        }
        if (feet.equals(next.pos) && next.action == PathSearch.Action.DIG_DOWN) {
            advance();
            return;
        }
        face(next.pos);
        boolean horizontal = next.pos.getX() != cur.pos.getX() || next.pos.getZ() != cur.pos.getZ();
        mc.options.forwardKey.setPressed(horizontal || horizontalDistance(p, next.pos) > 0.25);
        mc.options.sneakKey.setPressed(false);
        boolean straight = index + 2 < path.size() && sameDirection(cur.pos, next.pos, path.get(index + 2).pos);
        PathfinderModule m = Modules.get(PathfinderModule.class);
        mc.options.sprintKey.setPressed(m != null && m.sprint() && straight && !p.isTouchingWater());
        boolean up = next.pos.getY() > cur.pos.getY();
        boolean jump = (up && horizontalDistance(p, next.pos) < 1.7) || (p.horizontalCollision && p.isOnGround())
                || (p.isTouchingWater() && next.pos.getY() >= cur.pos.getY());
        mc.options.jumpKey.setPressed(jump);
    }

    private static void advance() {
        index++;
        stuckTicks = 0;
        failures = 0;
    }

    private static void fail(String reason) {
        failReason = reason;
        state = State.FAILED;
        search = null;
        stopBreaking();
        releaseKeys();
    }

    /** Blocks that must be free before entering {@code next} from {@code from}. */
    private static List<BlockPos> blocksToClear(BlockPos from, PathSearch.Node next) {
        BlockPos to = next.pos;
        if (next.action == PathSearch.Action.DIG_DOWN) return List.of(to);
        if (next.action == PathSearch.Action.PILLAR) return List.of(from.up(2));
        if (to.getY() > from.getY()) return List.of(from.up(2), to.up(), to); // step up: head room first
        int y = Math.max(from.getY(), to.getY());
        BlockPos column = new BlockPos(to.getX(), y, to.getZ());
        return List.of(column.up(), column); // head first so gravel falls into the feet block, mined next
    }

    // ------------------------------------------------------------------ mining

    /** Breaks {@code target}, or whatever is in front of it on the way. */
    public static void mine(BlockPos target) {
        ClientPlayerEntity p = mc.player;
        Vec3d eyes = p.getEyePos();
        Vec3d center = Vec3d.ofCenter(target);
        BlockHitResult hit = mc.world.raycast(new RaycastContext(eyes, center, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p));
        BlockPos pos = target;
        Direction side = Direction.getFacing(eyes.x - center.x, eyes.y - center.y, eyes.z - center.z);
        if (hit.getType() == HitResult.Type.BLOCK) {
            if (!hit.getBlockPos().equals(target) && BlockInfo.breakable(hit.getBlockPos())) pos = hit.getBlockPos();
            side = hit.getSide();
        }
        p.getInventory().setSelectedSlot(BlockInfo.bestToolSlot(pos));
        CombatUtil.lookAt(Vec3d.ofCenter(pos));
        if (!pos.equals(breaking)) {
            mc.interactionManager.attackBlock(pos, side);
            breaking = pos;
        } else {
            mc.interactionManager.updateBlockBreakingProgress(pos, side);
        }
        p.swingHand(Hand.MAIN_HAND);
    }

    public static void stopBreaking() {
        if (breaking != null && mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
        breaking = null;
    }

    // ------------------------------------------------------------------ helpers

    private static void face(BlockPos pos) {
        ClientPlayerEntity p = mc.player;
        double dx = pos.getX() + 0.5 - p.getX(), dz = pos.getZ() + 0.5 - p.getZ();
        if (dx * dx + dz * dz > 1e-4) p.setYaw((float) Math.toDegrees(Math.atan2(dz, dx)) - 90f);
        p.setPitch(MathHelper.clamp(p.getPitch(), -30f, 45f) * 0.8f + 10f * 0.2f);
    }

    private static double horizontalDistance(ClientPlayerEntity p, BlockPos pos) {
        return Math.hypot(pos.getX() + 0.5 - p.getX(), pos.getZ() + 0.5 - p.getZ());
    }

    private static boolean sameDirection(BlockPos a, BlockPos b, BlockPos c) {
        return b.getX() - a.getX() == c.getX() - b.getX() && b.getZ() - a.getZ() == c.getZ() - b.getZ();
    }

    public static void releaseKeys() {
        mc.options.forwardKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        KeyBinding.updatePressedStates();
    }

    /** Draws the path ahead. */
    public static void render(Overlay o) {
        if (path == null || state != State.WALKING) return;
        for (int i = Math.max(1, index); i < Math.min(path.size(), index + 200); i++) {
            BlockPos a = path.get(i - 1).pos, b = path.get(i).pos;
            o.line(a.getX() + 0.5, a.getY() + 0.1, a.getZ() + 0.5, b.getX() + 0.5, b.getY() + 0.1, b.getZ() + 0.5, 0xCC4ADE80);
        }
    }
}
