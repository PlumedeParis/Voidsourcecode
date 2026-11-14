package com.voidclient.util;

import com.voidclient.module.Modules;
import com.voidclient.module.misc.BypassModule;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.function.Predicate;

/**
 * Shared block breaking for Tunnel Miner, Highway Builder and the Builder.
 * <p>
 * Blocks your tool breaks in one hit (netherrack with Efficiency V, dirt, leaves... or
 * anything with Haste II) are broken many per tick, which is as "instant" as the server allows.
 * Slower blocks are mined one after the other at normal mining speed, because the server
 * checks how long each block takes.
 */
public final class Digger {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static BlockPos current;

    private Digger() {
    }

    public static boolean breakable(BlockPos pos) {
        BlockState s = mc.world.getBlockState(pos);
        return !s.isAir() && s.getFluidState().isEmpty() && s.getHardness(mc.world, pos) >= 0;
    }

    public static boolean reachable(BlockPos pos, double reach) {
        return mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= reach * reach;
    }

    /** Hotbar slot of the fastest tool for a block (current slot when nothing is better). */
    public static int bestTool(BlockState state) {
        int best = mc.player.getInventory().getSelectedSlot();
        float bestSpeed = mc.player.getInventory().getStack(best).getMiningSpeedMultiplier(state);
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isDamageable() && s.getMaxDamage() - s.getDamage() <= 3) continue; // don't break the tool
            float speed = s.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = i;
            }
        }
        return best;
    }

    private static Direction side(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        Direction best = Direction.UP;
        double bestDot = -1e9;
        for (Direction d : Direction.values()) {
            double dot = (eye.x - (pos.getX() + 0.5)) * d.getOffsetX() + (eye.y - (pos.getY() + 0.5)) * d.getOffsetY() + (eye.z - (pos.getZ() + 0.5)) * d.getOffsetZ();
            if (dot > bestDot) {
                bestDot = dot;
                best = d;
            }
        }
        return best;
    }

    /**
     * Works on a list of blocks to break (nearest first). @return true while something is left.
     *
     * @param perTick max instant breaks this tick (further capped by Bypass &gt; Actions Per Tick)
     */
    public static boolean work(List<BlockPos> targets, int perTick, boolean autoTool, double reach) {
        targets.removeIf(p -> !breakable(p) || !reachable(p, reach));
        if (targets.isEmpty()) {
            stop();
            return false;
        }
        Vec3d eye = mc.player.getEyePos();
        targets.sort((a, b) -> Double.compare(eye.squaredDistanceTo(Vec3d.ofCenter(a)), eye.squaredDistanceTo(Vec3d.ofCenter(b))));
        BypassModule bypass = Modules.get(BypassModule.class);
        if (bypass != null && bypass.isEnabled()) perTick = Math.min(perTick, bypass.actionsPerTick.getInt());

        if (current != null && (!targets.contains(current) || !breakable(current))) current = null;
        int done = 0;
        for (BlockPos pos : targets) {
            if (done >= perTick) break;
            BlockState state = mc.world.getBlockState(pos);
            if (autoTool) {
                int slot = bestTool(state);
                if (slot != mc.player.getInventory().getSelectedSlot()) mc.player.getInventory().setSelectedSlot(slot);
            }
            boolean instant = state.calcBlockBreakingDelta(mc.player, mc.world, pos) >= 1f;
            if (instant) {
                if (current != null) continue; // finish the slow block first, its progress would be reset
                Rotations.face(Vec3d.ofCenter(pos));
                mc.interactionManager.attackBlock(pos, side(pos));
                mc.player.swingHand(Hand.MAIN_HAND);
                done++;
            } else if (current == null || current.equals(pos)) {
                current = pos;
                Rotations.face(Vec3d.ofCenter(pos));
                mc.interactionManager.updateBlockBreakingProgress(pos, side(pos));
                mc.player.swingHand(Hand.MAIN_HAND);
                break; // one slow block at a time
            }
        }
        return true;
    }

    public static void stop() {
        if (current != null && mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
        current = null;
    }

    // ------------------------------------------------------------------ inventory helpers

    /** Hotbar slot holding a matching block item, moving a stack up from the inventory if needed. -1 = none. */
    public static int blockSlot(Predicate<ItemStack> match) {
        for (int i = 0; i < 9; i++) if (isBlock(mc.player.getInventory().getStack(i), match)) return i;
        if (mc.currentScreen != null) return -1; // don't click slots while a container is open
        for (int i = 9; i < 36; i++) {
            if (!isBlock(mc.player.getInventory().getStack(i), match)) continue;
            int hotbar = mc.player.getInventory().getSelectedSlot();
            for (int h = 0; h < 9; h++) {
                if (mc.player.getInventory().getStack(h).isEmpty()) {
                    hotbar = h;
                    break;
                }
            }
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, i, hotbar, SlotActionType.SWAP, mc.player);
            return hotbar;
        }
        return -1;
    }

    private static boolean isBlock(ItemStack s, Predicate<ItemStack> match) {
        return !s.isEmpty() && s.getItem() instanceof BlockItem && match.test(s);
    }

    public static int count(Predicate<ItemStack> match) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (isBlock(s, match)) n += s.getCount();
        }
        return n;
    }
}
