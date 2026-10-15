package com.mytestclient.module.visuals;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.ColorSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.render.Overlay;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;

import java.util.ArrayList;
import java.util.List;

/**
 * Light Overlay: marks blocks where hostile mobs can spawn (block light 0 on a solid top face).
 * Red = spawnable at any time, yellow = only at night (sky light reaches it). Handy for
 * spawn-proofing bases and farms. No warning.
 */
public class LightOverlayModule extends Module {
    private final NumberSetting radius = add(new NumberSetting("Radius", "Horizontal scan radius in blocks.", 16, 4, 32, 1));
    private final NumberSetting vertical = add(new NumberSetting("Vertical", "Vertical scan range in blocks.", 8, 2, 24, 1));
    private final NumberSetting maxMarkers = add(new NumberSetting("Max Markers", "Limit for performance.", 600, 50, 3000, 50));
    private final BooleanSetting showNightOnly = add(new BooleanSetting("Night Spawns", "Also show spots that are only dark at night.", true));
    private final ColorSetting alwaysColor = add(new ColorSetting("Always Color", "Spawnable at any time.", 0xFFEF4444));
    private final ColorSetting nightColor = add(new ColorSetting("Night Color", "Spawnable only at night.", 0xFFFACC15));

    private record Marker(int x, int y, int z, boolean night) {
    }

    /** Markers shown on screen (result of the last completed scan). */
    private List<Marker> markers = new ArrayList<>();
    /** Scan in progress, filled a few layers per tick so there is never a big lag spike. */
    private List<Marker> pending = new ArrayList<>();
    private BlockPos scanOrigin;
    private int scanLayer;
    private final BlockPos.Mutable pos = new BlockPos.Mutable();
    private final BlockPos.Mutable below = new BlockPos.Mutable();

    public LightOverlayModule() {
        super("Light Overlay", "Show blocks where mobs can spawn (light level 0).", Category.VISUALS);
    }

    @Override
    public void onTick() {
        int v = vertical.getInt();
        if (scanOrigin == null) {
            scanOrigin = mc.player.getBlockPos();
            scanLayer = -v;
            pending = new ArrayList<>();
        }
        // ~2 horizontal layers per tick: a full scan takes under a second, spread evenly.
        for (int i = 0; i < 2 && scanLayer <= v; i++, scanLayer++) scanLayer(scanLayer);
        if (scanLayer > v) {
            markers = pending;
            scanOrigin = null;
        }
    }

    private void scanLayer(int dy) {
        int r = radius.getInt(), limit = maxMarkers.getInt();
        boolean nightSpots = showNightOnly.get();
        int y = scanOrigin.getY() + dy;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (pending.size() >= limit) return;
                pos.set(scanOrigin.getX() + dx, y, scanOrigin.getZ() + dz);
                // cheapest checks first
                if (!mc.world.getBlockState(pos).isAir()) continue;
                if (mc.world.getLightLevel(LightType.BLOCK, pos) > 0) continue;
                boolean night = mc.world.getLightLevel(LightType.SKY, pos) > 7;
                if (night && !nightSpots) continue;
                below.set(pos.getX(), y - 1, pos.getZ());
                BlockState floor = mc.world.getBlockState(below);
                if (floor.isAir() || !floor.isSideSolidFullSquare(mc.world, below, Direction.UP)) continue;
                pending.add(new Marker(pos.getX(), y, pos.getZ(), night));
            }
        }
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        for (Marker m : markers) {
            int color = m.night ? nightColor.get() : alwaysColor.get();
            double y = m.y + 0.02;
            o.line(m.x + 0.2, y, m.z + 0.2, m.x + 0.8, y, m.z + 0.8, color);
            o.line(m.x + 0.8, y, m.z + 0.2, m.x + 0.2, y, m.z + 0.8, color);
        }
    }

    @Override
    protected void onDisable() {
        markers = new ArrayList<>();
        scanOrigin = null;
    }
}
