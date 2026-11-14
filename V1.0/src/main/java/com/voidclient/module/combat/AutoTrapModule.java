package com.voidclient.module.combat;

import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.Risk;
import com.voidclient.module.setting.BooleanSetting;
import com.voidclient.module.setting.NumberSetting;
import com.voidclient.util.ChatUtil;
import com.voidclient.util.CombatUtil;
import com.voidclient.util.Digger;
import com.voidclient.util.Friends;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Auto Trap: boxes the nearest enemy player in obsidian (sides at head height and a roof). */
public class AutoTrapModule extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", "How close the enemy must be.", 4.5, 2, 6, 0.5));
    private final NumberSetting perTick = add(new NumberSetting("Blocks Per Tick", "Placements per tick.", 2, 1, 5, 1));
    private final BooleanSetting feet = add(new BooleanSetting("Feet Too", "Also surround their feet.", false));
    private final BooleanSetting autoDisable = add(new BooleanSetting("Auto Disable", "Turn off when the trap is complete.", true));

    public AutoTrapModule() {
        super("Auto Trap", "Traps the nearest enemy player in obsidian.", Category.COMBAT);
        setRisk(Risk.DETECTABLE, "Automated, very fast block placement.");
    }

    @Override
    public void onTick() {
        PlayerEntity target = null;
        double best = range.get() * range.get();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isSpectator() || p.isCreative() || Friends.isFriend(p.getName().getString())) continue;
            double d = mc.player.squaredDistanceTo(p);
            if (d < best) {
                best = d;
                target = p;
            }
        }
        if (target == null) return;
        int slot = Digger.blockSlot(s -> s.isOf(Items.OBSIDIAN) || s.isOf(Items.CRYING_OBSIDIAN));
        if (slot < 0) {
            ChatUtil.error("Auto Trap: no obsidian.");
            setEnabled(false);
            return;
        }
        BlockPos base = target.getBlockPos();
        List<BlockPos> plan = new ArrayList<>();
        BlockPos[] around = {base.north(), base.south(), base.east(), base.west()};
        if (feet.get()) for (BlockPos p : around) plan.add(p);
        for (BlockPos p : around) plan.add(p.up());       // head height
        plan.add(base.north().up(2));                       // support for the roof
        plan.add(base.up(2));                               // roof
        int placed = 0, missing = 0;
        for (BlockPos p : plan) {
            if (!mc.world.getBlockState(p).isReplaceable()) continue;
            missing++;
            if (placed >= perTick.getInt()) continue;
            if (CombatUtil.place(p, slot, true) || CombatUtil.place(p.down(), slot, true)) placed++;
        }
        if (missing == 0 && autoDisable.get()) setEnabled(false);
    }
}
