package com.mytestclient.module.visuals;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.ColorSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.render.Overlay;
import com.mytestclient.render.Projection;

/**
 * Chunk Borders: draws the borders of the chunk you stand in, like vanilla F3+G but
 * configurable. A debug overlay, no warning.
 */
public class ChunkBordersModule extends Module {
    private final NumberSetting height = add(new NumberSetting("Height", "Blocks drawn above and below the camera.", 16, 4, 64, 1));
    private final NumberSetting grid = add(new NumberSetting("Grid Step", "Spacing of the grid lines on the chunk walls.", 4, 1, 16, 1));
    private final BooleanSetting neighbours = add(new BooleanSetting("Neighbours", "Also mark the corners of the surrounding chunks.", true));
    private final ColorSetting cornerColor = add(new ColorSetting("Corner Color", "Corner lines of the current chunk.", 0xFFFACC15));
    private final ColorSetting gridColor = add(new ColorSetting("Grid Color", "Grid lines on the chunk walls.", 0x80FACC15));
    private final ColorSetting neighbourColor = add(new ColorSetting("Neighbour Color", "Corners of surrounding chunks.", 0xA0EF4444));

    public ChunkBordersModule() {
        super("Chunk Borders", "Show chunk boundaries with a configurable grid (like F3+G).", Category.VISUALS);
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        int cx = ((int) Math.floor(Projection.camX())) >> 4 << 4;
        int cz = ((int) Math.floor(Projection.camZ())) >> 4 << 4;
        double camY = Projection.camY();
        int step = grid.getInt();
        double minY = Math.floor(camY - height.get());
        double maxY = Math.ceil(camY + height.get());

        // vertical lines along the walls of the current chunk
        for (int i = 0; i <= 16; i += step) {
            int color = (i == 0 || i == 16) ? cornerColor.get() : gridColor.get();
            o.line(cx + i, minY, cz, cx + i, maxY, cz, color);
            o.line(cx + i, minY, cz + 16, cx + i, maxY, cz + 16, color);
            if (i != 0 && i != 16) {
                o.line(cx, minY, cz + i, cx, maxY, cz + i, color);
                o.line(cx + 16, minY, cz + i, cx + 16, maxY, cz + i, color);
            }
        }
        // horizontal rings
        double firstY = Math.ceil(minY / step) * step;
        for (double y = firstY; y <= maxY; y += step) o.square(cx, cz, cx + 16, cz + 16, y, gridColor.get());

        if (neighbours.get()) {
            for (int dx = -16; dx <= 32; dx += 16) {
                for (int dz = -16; dz <= 32; dz += 16) {
                    boolean own = dx >= 0 && dx <= 16 && dz >= 0 && dz <= 16;
                    if (!own) o.line(cx + dx, minY, cz + dz, cx + dx, maxY, cz + dz, neighbourColor.get());
                }
            }
        }
    }
}
