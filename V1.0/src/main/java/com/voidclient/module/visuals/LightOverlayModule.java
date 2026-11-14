package com.voidclient.module.visuals;

import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.setting.NumberSetting;
import com.voidclient.render.Overlay;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;

import java.util.ArrayList;
import java.util.List;

/** Light Overlay: marks the blocks around you where hostile mobs can spawn (block light 0), to light up a base. */
public class LightOverlayModule extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", "Search radius in blocks.", 10, 4, 20, 1));
    private List<BlockPos> spots = List.of();
    private int timer;

    public LightOverlayModule() {
        super("Light Overlay", "Marks blocks where monsters can spawn (light level 0).", Category.VISUALS);
    }

    @Override
    public void onTick() {
        if (timer-- > 0) return;
        timer = 10;
        List<BlockPos> found = new ArrayList<>();
        int r = range.getInt();
        BlockPos me = mc.player.getBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -4; y <= 4; y++) {
                    BlockPos p = me.add(x, y, z);
                    BlockState below = mc.world.getBlockState(p.down());
                    if (!below.isSideSolidFullSquare(mc.world, p.down(), Direction.UP)) continue;
                    if (!mc.world.getBlockState(p).getCollisionShape(mc.world, p).isEmpty() || !mc.world.getFluidState(p).isEmpty()) continue;
                    if (mc.world.getLightLevel(LightType.BLOCK, p) == 0) found.add(p);
                    if (found.size() >= 400) break;
                }
            }
        }
        spots = found;
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        for (BlockPos p : spots) {
            boolean sky = mc.world.getLightLevel(LightType.SKY, p) > 0; // only at night
            o.square(p.getX() + 0.3, p.getZ() + 0.3, p.getX() + 0.7, p.getZ() + 0.7, p.getY() + 0.02, sky ? 0xFFFBBF24 : 0xFFF43F5E);
        }
    }
}
