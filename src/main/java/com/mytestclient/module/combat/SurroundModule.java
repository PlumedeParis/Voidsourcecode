package com.mytestclient.module.combat;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.util.ChatUtil;
import com.mytestclient.util.CombatUtil;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Surround: puts obsidian on the four sides of your feet, so end crystals and most explosions
 * can't hurt you from the ground. ⚠ DETECTABLE: automated block placement.
 */
public class SurroundModule extends Module {
    private final BooleanSetting center = add(new BooleanSetting("Center", "Move to the middle of the block first.", true));
    private final BooleanSetting disableOnJump = add(new BooleanSetting("Disable On Jump", "Turn off when you jump or leave the block.", true));
    private final NumberSetting perTick = add(new NumberSetting("Blocks Per Tick", "Placements per tick.", 2, 1, 4, 1));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", "Look at the blocks you place.", false));

    private BlockPos start;

    public SurroundModule() {
        super("Surround", "Surrounds your feet with obsidian against crystals and explosions.", Category.COMBAT);
        setRisk(Risk.DETECTABLE, "Automated, very fast block placement.");
    }

    @Override
    protected void onEnable() {
        if (mc.player == null) return;
        start = mc.player.getBlockPos();
        if (center.get()) {
            mc.player.setPosition(start.getX() + 0.5, mc.player.getY(), start.getZ() + 0.5);
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
        }
    }

    @Override
    public void onTick() {
        BlockPos feet = mc.player.getBlockPos();
        if (start == null) start = feet; // enabled from the config before joining
        if (disableOnJump.get() && start != null && (!feet.equals(start) || mc.options.jumpKey.isPressed())) {
            setEnabled(false);
            return;
        }
        int slot = obsidianSlot();
        if (slot < 0) {
            ChatUtil.error("Surround: no obsidian in your hotbar.");
            setEnabled(false);
            return;
        }
        int placed = 0;
        for (Direction d : Direction.Type.HORIZONTAL) {
            if (placed >= perTick.getInt()) break;
            BlockPos side = feet.offset(d);
            if (!mc.world.getBlockState(side).isReplaceable()) continue;
            // nothing to click on: put a support block under it first
            if (CombatUtil.place(side, slot, rotate.get()) || CombatUtil.place(side.down(), slot, rotate.get())) placed++;
        }
    }

    private int obsidianSlot() {
        for (int i = 0; i < 9; i++) {
            var s = mc.player.getInventory().getStack(i);
            if (s.isOf(Items.OBSIDIAN) || s.isOf(Items.CRYING_OBSIDIAN)) return i;
        }
        return -1;
    }
}
