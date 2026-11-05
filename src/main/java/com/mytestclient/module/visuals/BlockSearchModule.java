package com.mytestclient.module.visuals;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.ColorSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.module.setting.ListSetting;
import com.mytestclient.render.Overlay;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Block Search: finds and outlines specific blocks (e.g. spawners, ancient debris, diamond ore)
 * in loaded chunks.
 * <p>
 * Performance: chunks are scanned incrementally (a few per tick), empty sections and sections
 * whose palette does not contain a searched block are skipped without looking at a single block.
 * <p>
 * ⚠ CLIENT_SIDE: X-ray-like information advantage; banned on most servers (and many servers
 * hide ores from clients with anti-xray, so results there are unreliable).
 */
public class BlockSearchModule extends Module {
    private final ListSetting blocks = add(new ListSetting("Blocks", "Blocks to search for (pick them in the menu).", ListSetting.Kind.BLOCK,
            "minecraft:spawner", "minecraft:ancient_debris", "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"));
    private final NumberSetting radius = add(new NumberSetting("Chunk Radius", "Chunks around you to scan.", 6, 1, 16, 1));
    private final NumberSetting chunksPerTick = add(new NumberSetting("Chunks Per Tick", "Higher scans faster but costs more per tick.", 2, 1, 16, 1));
    private final NumberSetting maxRender = add(new NumberSetting("Max Shown", "Maximum number of outlined blocks.", 300, 10, 2000, 10));
    private final BooleanSetting tracers = add(new BooleanSetting("Tracers", "Draw a line to each found block.", false));
    private final ColorSetting color = add(new ColorSetting("Color", "Outline colour.", 0xFF22D3EE));

    private final Map<Long, List<BlockPos>> found = new HashMap<>();
    private final Map<Block, Boolean> matchCache = new HashMap<>();
    private List<String> parsed;
    private int cursor;

    public BlockSearchModule() {
        super("Block Search", "Highlight specific blocks (spawners, ores...) in loaded chunks.", Category.VISUALS);
        setRisk(Risk.CLIENT_SIDE, "X-ray-like information advantage, banned on most servers. Servers with anti-xray hide ores, so results there are unreliable.");
    }

    /** Resets caches when the selection changes in the picker. */
    private void parseTargets() {
        if (blocks.get() == parsed) return;
        parsed = blocks.get();
        matchCache.clear();
        found.clear();
    }

    private boolean matches(BlockState state) {
        return matchCache.computeIfAbsent(state.getBlock(), b -> blocks.contains(Registries.BLOCK.getId(b).toString()));
    }

    @Override
    public void onTick() {
        parseTargets();
        if (blocks.size() == 0) return;
        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4, r = radius.getInt();
        int side = r * 2 + 1, total = side * side;

        // forget chunks that are out of range
        found.keySet().removeIf(key -> Math.abs(ChunkPos.getPackedX(key) - pcx) > r || Math.abs(ChunkPos.getPackedZ(key) - pcz) > r);

        for (int i = 0; i < chunksPerTick.getInt(); i++) {
            cursor = (cursor + 1) % total;
            int cx = pcx - r + cursor % side, cz = pcz - r + cursor / side;
            if (!mc.world.getChunkManager().isChunkLoaded(cx, cz)) continue;
            scanChunk(mc.world.getChunk(cx, cz), cx, cz);
        }
    }

    private void scanChunk(WorldChunk chunk, int cx, int cz) {
        List<BlockPos> result = new ArrayList<>();
        ChunkSection[] sections = chunk.getSectionArray();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;
            if (!section.getBlockStateContainer().hasAny(this::matches)) continue;
            int baseY = chunk.sectionIndexToCoord(i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (matches(section.getBlockState(x, y, z))) result.add(new BlockPos(cx * 16 + x, baseY + y, cz * 16 + z));
                    }
                }
            }
        }
        long key = ChunkPos.toLong(cx, cz);
        if (result.isEmpty()) found.remove(key);
        else found.put(key, result);
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        int shown = 0, limit = maxRender.getInt(), c = color.get();
        for (List<BlockPos> list : found.values()) {
            for (BlockPos p : list) {
                if (shown++ >= limit) return;
                o.box(new Box(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1), c);
                if (tracers.get()) o.tracer(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, c);
            }
        }
    }

    @Override
    protected void onDisable() {
        found.clear();
    }

    @Override
    public void onWorldLeave() {
        found.clear();
    }
}
