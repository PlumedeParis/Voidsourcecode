package com.mytestclient.pathing;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Set;

/** What the pathfinder needs to know about blocks: can I walk through it, stand on it, break it, and how fast. */
public final class BlockInfo {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /** Never walk into these. */
    private static final Set<Block> DANGER = Set.of(Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.SWEET_BERRY_BUSH, Blocks.POWDER_SNOW,
            Blocks.NETHER_PORTAL, Blocks.END_PORTAL, Blocks.END_GATEWAY, Blocks.WITHER_ROSE, Blocks.CACTUS, Blocks.MAGMA_BLOCK,
            Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.POINTED_DRIPSTONE);
    /** Never break these (too slow, or someone's base). */
    private static final Set<Block> UNBREAKABLE = Set.of(Blocks.BEDROCK, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.END_PORTAL_FRAME,
            Blocks.RESPAWN_ANCHOR, Blocks.REINFORCED_DEEPSLATE, Blocks.BARRIER, Blocks.SPAWNER, Blocks.BUDDING_AMETHYST, Blocks.INFESTED_STONE,
            Blocks.INFESTED_DEEPSLATE, Blocks.INFESTED_COBBLESTONE, Blocks.INFESTED_STONE_BRICKS);
    /** Cheap blocks the pathfinder may place to bridge gaps and pillar up. */
    private static final Set<String> THROWAWAY = Set.of("dirt", "cobblestone", "cobbled_deepslate", "netherrack", "stone", "andesite",
            "diorite", "granite", "tuff", "blackstone", "basalt", "end_stone", "deepslate", "sandstone", "coarse_dirt", "mossy_cobblestone");

    private BlockInfo() {
    }

    public static ClientWorld world() {
        return mc.world;
    }

    public static BlockState state(BlockPos pos) {
        return mc.world.getBlockState(pos);
    }

    public static boolean loaded(BlockPos pos) {
        return mc.world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static boolean isWater(BlockPos pos) {
        return mc.world.getFluidState(pos).isIn(FluidTags.WATER);
    }

    public static boolean isLava(BlockPos pos) {
        return mc.world.getFluidState(pos).isIn(FluidTags.LAVA);
    }

    /** No collision, no lava, nothing that hurts. Water is passable. */
    public static boolean passable(BlockPos pos) {
        BlockState s = state(pos);
        if (DANGER.contains(s.getBlock()) || isLava(pos)) return false;
        return s.getCollisionShape(mc.world, pos).isEmpty();
    }

    /** Something you can stand on (top of the block). */
    public static boolean solid(BlockPos pos) {
        BlockState s = state(pos);
        if (DANGER.contains(s.getBlock()) || isLava(pos)) return false;
        return !s.getCollisionShape(mc.world, pos).isEmpty() && s.isSideSolidFullSquare(mc.world, pos, Direction.UP);
    }

    /** Can stand with feet at {@code feet}: support below, or floating in water. */
    public static boolean standable(BlockPos feet) {
        return solid(feet.down()) || isWater(feet) || isWater(feet.down());
    }

    /** Can be broken safely (not unbreakable, no container, won't release lava). */
    public static boolean breakable(BlockPos pos) {
        BlockState s = state(pos);
        if (s.isAir() || UNBREAKABLE.contains(s.getBlock())) return false;
        if (s.getHardness(mc.world, pos) < 0 || s.hasBlockEntity()) return false;
        for (Direction d : Direction.values()) {
            if (d != Direction.DOWN && isLava(pos.offset(d))) return false; // would let lava flow onto us
        }
        return true;
    }

    public static boolean isFalling(BlockPos pos) {
        return state(pos).getBlock() instanceof FallingBlock;
    }

    /** Ticks to break with the best tool in the hotbar (rough: no enchantments/effects). */
    public static double breakTicks(BlockPos pos) {
        BlockState s = state(pos);
        float hardness = s.getHardness(mc.world, pos);
        if (hardness < 0) return Double.POSITIVE_INFINITY;
        if (hardness == 0) return 1;
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            float speed = Math.max(1f, stack.getMiningSpeedMultiplier(s));
            boolean harvest = !s.isToolRequired() || stack.isSuitableFor(s);
            best = Math.min(best, Math.ceil(hardness * (harvest ? 30 : 100) / speed));
        }
        return best;
    }

    /** Best hotbar slot to mine this block. */
    public static int bestToolSlot(BlockPos pos) {
        BlockState s = state(pos);
        int best = mc.player.getInventory().getSelectedSlot();
        float bestScore = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            float score = stack.getMiningSpeedMultiplier(s) * (!s.isToolRequired() || stack.isSuitableFor(s) ? 10 : 1);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /** Hotbar slot of a throwaway block, or -1. */
    public static int throwawaySlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof BlockItem && THROWAWAY.contains(Registries.ITEM.getId(stack.getItem()).getPath())) return i;
        }
        return -1;
    }
}
