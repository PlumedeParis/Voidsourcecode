package com.mytestclient.module.movement;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.FallingBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Scaffold: automatically places a block under your feet whenever you walk over air, so you
 * can bridge by just walking. Uses blocks from your hotbar.
 * <p>
 * ⚠ DETECTABLE: automated, very fast placements (often while looking elsewhere) are a classic
 * anti-cheat flag and banned on most servers.
 */
public class ScaffoldModule extends Module {
    private final BooleanSetting predict = add(new BooleanSetting("Predict", "Also place where you will be next tick (better at sprint speed).", true));
    private final BooleanSetting tower = add(new BooleanSetting("Tower", "Hold jump while standing still to build straight up.", true));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", "Look at the block face before placing (visible on your player).", false));
    private final BooleanSetting switchBack = add(new BooleanSetting("Switch Back", "Return to the previously held slot after placing.", true));
    private final BooleanSetting safeWalk = add(new BooleanSetting("Safe Walk", "Don't walk off edges while scaffolding (when no block is available).", true));
    private final NumberSetting perTick = add(new NumberSetting("Blocks Per Tick", "Maximum placements per tick.", 2, 1, 4, 1));

    public ScaffoldModule() {
        super("Scaffold", "Places blocks under you while you walk over air: bridge by just walking.", Category.MOVEMENT);
        setRisk(Risk.DETECTABLE, "Automated, fast block placements are easy for anti-cheats to spot and are banned on most servers.");
    }

    /** Used by the Safe Walk mixin. */
    public boolean wantsSafeWalk() {
        return isEnabled() && safeWalk.get();
    }

    @Override
    public void onTick() {
        if (mc.player.getAbilities().flying) return;
        int slot = findBlockSlot();
        if (slot < 0) return;

        int placed = 0;
        BlockPos under = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - 0.5, mc.player.getZ());
        if (place(under, slot)) placed++;

        if (predict.get() && placed < perTick.getInt()) {
            Vec3d v = mc.player.getVelocity();
            BlockPos next = BlockPos.ofFloored(mc.player.getX() + v.x * 2, mc.player.getY() - 0.5, mc.player.getZ() + v.z * 2);
            if (!next.equals(under) && place(next, slot)) placed++;
        }

        // tower: jump straight up and place under yourself
        if (tower.get() && mc.options.jumpKey.isPressed() && !isMovingHorizontally()) {
            Vec3d v = mc.player.getVelocity();
            if (placed > 0 || mc.player.isOnGround()) mc.player.setVelocity(v.x * 0.5, 0.42, v.z * 0.5);
        }
    }

    private boolean isMovingHorizontally() {
        return mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
    }

    /** Places a block at {@code pos} by clicking an adjacent solid face. */
    private boolean place(BlockPos pos, int slot) {
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;
        for (Direction dir : Direction.values()) {
            BlockPos neighbour = pos.offset(dir);
            if (mc.world.getBlockState(neighbour).isReplaceable()) continue;
            Direction face = dir.getOpposite();
            Vec3d hitVec = new Vec3d(
                    neighbour.getX() + 0.5 + face.getOffsetX() * 0.5,
                    neighbour.getY() + 0.5 + face.getOffsetY() * 0.5,
                    neighbour.getZ() + 0.5 + face.getOffsetZ() * 0.5);
            if (mc.player.getEyePos().squaredDistanceTo(hitVec) > 4.5 * 4.5) continue;

            int previous = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(slot);
            if (rotate.get()) lookAt(hitVec);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hitVec, face, neighbour, false));
            mc.player.swingHand(Hand.MAIN_HAND);
            if (switchBack.get()) mc.player.getInventory().setSelectedSlot(previous);
            return true;
        }
        return false;
    }

    /** Hotbar slot holding a full, non-falling block; prefers the currently held one. */
    private int findBlockSlot() {
        int selected = mc.player.getInventory().getSelectedSlot();
        if (isGoodBlock(mc.player.getInventory().getStack(selected))) return selected;
        for (int i = 0; i < 9; i++) if (isGoodBlock(mc.player.getInventory().getStack(i))) return i;
        return -1;
    }

    private boolean isGoodBlock(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        if (block instanceof FallingBlock) return false;
        return block.getDefaultState().isFullCube(mc.world, BlockPos.ORIGIN);
    }

    private void lookAt(Vec3d target) {
        double dx = target.x - mc.player.getX(), dy = target.y - mc.player.getEyeY(), dz = target.z - mc.player.getZ();
        mc.player.setYaw((float) Math.toDegrees(Math.atan2(dz, dx)) - 90f);
        mc.player.setPitch(MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), -90f, 90f));
    }
}
