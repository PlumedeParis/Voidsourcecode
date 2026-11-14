package com.voidclient.module.combat;

import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.Risk;
import com.voidclient.module.setting.BooleanSetting;
import com.voidclient.module.setting.NumberSetting;
import com.voidclient.module.visuals.HoleEspModule;
import com.voidclient.util.CombatUtil;
import com.voidclient.util.Digger;
import com.voidclient.util.Friends;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

/** Hole Filler: fills the safe holes around you with obsidian so enemies cannot hide in them. */
public class HoleFillerModule extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", "How far to fill holes.", 4, 2, 5, 1));
    private final BooleanSetting onlyNearEnemies = add(new BooleanSetting("Only Near Enemies", "Only fill holes within 3 blocks of an enemy player.", true));
    private final NumberSetting perTick = add(new NumberSetting("Blocks Per Tick", "Placements per tick.", 1, 1, 4, 1));

    public HoleFillerModule() {
        super("Hole Filler", "Fills crystal-safe holes near enemies with obsidian.", Category.COMBAT);
        setRisk(Risk.DETECTABLE, "Automated block placement.");
    }

    @Override
    public void onTick() {
        int slot = Digger.blockSlot(s -> s.isOf(Items.OBSIDIAN) || s.isOf(Items.CRYING_OBSIDIAN) || s.isOf(Items.COBWEB));
        if (slot < 0) return;
        int r = range.getInt(), placed = 0;
        BlockPos me = mc.player.getBlockPos();
        for (int x = -r; x <= r && placed < perTick.getInt(); x++) {
            for (int z = -r; z <= r && placed < perTick.getInt(); z++) {
                for (int y = -2; y <= 1 && placed < perTick.getInt(); y++) {
                    BlockPos p = me.add(x, y, z);
                    if (p.equals(me) || HoleEspModule.holeType(mc.world, p) == 0) continue;
                    if (onlyNearEnemies.get() && !enemyNear(p)) continue;
                    if (CombatUtil.place(p, slot, true)) placed++;
                }
            }
        }
    }

    private boolean enemyNear(BlockPos p) {
        for (PlayerEntity e : mc.world.getPlayers()) {
            if (e == mc.player || Friends.isFriend(e.getName().getString())) continue;
            if (e.squaredDistanceTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5) < 9) return true;
        }
        return false;
    }
}
