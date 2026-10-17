package com.mytestclient.module.world;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.ColorSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.render.Overlay;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.HashSet;
import java.util.Set;

/**
 * New Chunks: guesses whether chunks were freshly generated or already existed.
 * <p>
 * Heuristic: right after the server generates a chunk, its liquids start flowing and the server
 * sends those block updates. Chunks that existed before have settled liquids. So a flowing
 * liquid update in a chunk marks it as "new". Other chunks are shown as "no signal" (most likely
 * old). Chunks you load for the first time this session can be tinted too.
 * <p>
 * ⚠ CLIENT_SIDE: a heuristic based on what the server sends; results vary with server software
 * and chunks without liquids give no signal at all.
 */
public class NewChunksModule extends Module {
    private final NumberSetting radius = add(new NumberSetting("Render Radius", "Chunks around you to draw.", 12, 2, 32, 1));
    private final NumberSetting yOffset = add(new NumberSetting("Y Offset", "Draw height relative to your feet.", 0, -64, 64, 1));
    private final BooleanSetting showOld = add(new BooleanSetting("Show Old", "Also outline loaded chunks without a new-chunk signal.", false));
    private final ColorSetting newColor = add(new ColorSetting("New Color", "Chunks that look freshly generated.", 0xFFEF4444));
    private final ColorSetting oldColor = add(new ColorSetting("Old Color", "Chunks with no new-chunk signal.", 0x604ADE80));

    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet newChunks = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet loadedChunks = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

    public NewChunksModule() {
        super("New Chunks", "Highlight chunks that look freshly generated vs. already explored.", Category.WORLD);
        setRisk(Risk.CLIENT_SIDE, "Heuristic based on liquid updates sent by the server. Accuracy depends on the server and terrain.");
    }

    /** Called from the network mixin for every block update the server sends. */
    public void onBlockUpdate(BlockPos pos, BlockState state) {
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty() && !fluid.isStill()) newChunks.add(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4));
    }

    /** Called from the chunk load event. */
    public void onChunkLoad(ChunkPos pos) {
        if (isEnabled()) loadedChunks.add(pos.toLong());
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4, r = radius.getInt();
        double y = mc.player.getY() + yOffset.get();
        for (int cx = pcx - r; cx <= pcx + r; cx++) {
            for (int cz = pcz - r; cz <= pcz + r; cz++) {
                long key = ChunkPos.toLong(cx, cz);
                int color;
                if (newChunks.contains(key)) color = newColor.get();
                else if (showOld.get() && loadedChunks.contains(key)) color = oldColor.get();
                else continue;
                double x1 = cx * 16 + 0.5, z1 = cz * 16 + 0.5;
                o.square(x1, z1, x1 + 15, z1 + 15, y, color);
            }
        }
    }

    @Override
    public void onWorldLeave() {
        newChunks.clear();
        loadedChunks.clear();
    }
}
