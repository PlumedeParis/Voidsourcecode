package com.voidclient.module.visuals;

import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.setting.BooleanSetting;
import com.voidclient.module.setting.ColorSetting;
import com.voidclient.module.setting.NumberSetting;
import com.voidclient.render.Overlay;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Hole ESP: highlights one-block holes you can stand in to be safe from end crystals:
 * green when all four sides and the floor are bedrock, another colour when some are obsidian.
 */
public class HoleEspModule extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", "Search radius in blocks.", 8, 3, 16, 1));
    private final BooleanSetting obsidian = add(new BooleanSetting("Obsidian Holes", "Also show holes made (partly) of obsidian.", true));
    private final ColorSetting bedrockColor = add(new ColorSetting("Bedrock", "Colour of full bedrock holes.", 0xFF4ADE80));
    private final ColorSetting obsidianColor = add(new ColorSetting("Obsidian", "Colour of obsidian or mixed holes.", 0xFFFBBF24));

    private record Hole(BlockPos pos, boolean bedrock) {
    }

    private List<Hole> holes = List.of();
    private int timer;

    public HoleEspModule() {
        super("Hole ESP", "Highlights crystal-safe holes (bedrock / obsidian) around you.", Category.VISUALS);
    }

    /** 0 = not a hole, 1 = obsidian/mixed, 2 = bedrock. Also used by Hole Filler. */
    public static int holeType(net.minecraft.world.World world, BlockPos pos) {
        if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.up()).isAir() || !world.getBlockState(pos.up(2)).isAir()) return 0;
        boolean allBedrock = true;
        for (BlockPos side : new BlockPos[]{pos.down(), pos.north(), pos.south(), pos.east(), pos.west()}) {
            Block b = world.getBlockState(side).getBlock();
            if (b == Blocks.BEDROCK) continue;
            if (b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN || b == Blocks.ENDER_CHEST || b == Blocks.RESPAWN_ANCHOR || b == Blocks.NETHERITE_BLOCK) allBedrock = false;
            else return 0;
        }
        return allBedrock ? 2 : 1;
    }

    @Override
    public void onTick() {
        if (timer-- > 0) return;
        timer = 5;
        List<Hole> found = new ArrayList<>();
        int r = range.getInt();
        BlockPos me = mc.player.getBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -r / 2; y <= r / 2; y++) {
                    BlockPos p = me.add(x, y, z);
                    int type = holeType(mc.world, p);
                    if (type == 2 || (type == 1 && obsidian.get())) found.add(new Hole(p, type == 2));
                }
            }
        }
        holes = found;
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        for (Hole h : holes) {
            BlockPos p = h.pos;
            o.square(p.getX(), p.getZ(), p.getX() + 1, p.getZ() + 1, p.getY() + 0.02, (h.bedrock ? bedrockColor.get() : obsidianColor.get()) | 0xFF000000);
        }
    }
}
