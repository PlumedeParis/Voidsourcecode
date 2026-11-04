package com.mytestclient.module.movement;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.BooleanSetting;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Water Walk: walk (and run, and jump) on water like on the ground. If you are in water you
 * float back up to the surface. Sneak to dive. ⚠ DETECTABLE: standing on liquid is impossible
 * in vanilla.
 */
public class WaterWalkModule extends Module {
    private final BooleanSetting lava = add(new BooleanSetting("Lava", "Also walk on lava (you still burn if you fall in).", true));
    private final BooleanSetting sneakToSink = add(new BooleanSetting("Sneak To Dive", "Hold sneak to go into the water.", true));
    private final BooleanSetting floatUp = add(new BooleanSetting("Float Up", "When you are in the liquid, rise back to the surface.", true));

    public WaterWalkModule() {
        super("Water Walk", "Walk on water (and lava) like on solid ground. Sneak to dive.", Category.MOVEMENT);
        setRisk(Risk.DETECTABLE, "Standing on water is impossible in vanilla; servers and anti-cheats notice.");
    }

    private boolean walkable(FluidState f) {
        return !f.isEmpty() && (f.isIn(FluidTags.WATER) || (lava.get() && f.isIn(FluidTags.LAVA)));
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p.isGliding() || p.getAbilities().flying || p.hasVehicle()) return;
        if (sneakToSink.get() && mc.options.sneakKey.isPressed()) return;
        Vec3d v = p.getVelocity();

        // in the liquid: swim back up to the surface
        boolean inLiquid = p.isTouchingWater() || (lava.get() && p.isInLava());
        BlockPos feet = BlockPos.ofFloored(p.getX(), p.getY() + 0.1, p.getZ());
        if (floatUp.get() && inLiquid && walkable(mc.world.getFluidState(feet))) {
            p.setVelocity(v.x, Math.max(v.y, 0.12), v.z);
            p.fallDistance = 0;
            return;
        }

        // just above the surface: stand on it
        BlockPos below = BlockPos.ofFloored(p.getX(), p.getY() - 0.3, p.getZ());
        FluidState f = mc.world.getFluidState(below);
        if (!walkable(f) || !mc.world.getFluidState(below.up()).isEmpty()) return;
        double surface = below.getY() + f.getHeight(mc.world, below);
        double above = p.getY() - surface;
        if (above > -0.6 && above < 0.3 && v.y <= 0.1) {
            p.setPosition(p.getX(), surface, p.getZ());
            p.setVelocity(v.x, 0, v.z);
            p.setOnGround(true);
            p.fallDistance = 0;
        }
    }
}
