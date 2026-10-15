package com.mytestclient.module.world;

import com.mytestclient.gui.Theme;
import com.mytestclient.hud.HudModule;
import com.mytestclient.module.Category;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.ColorSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.module.setting.StringSetting;
import com.mytestclient.render.Overlay;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seed Info: helps understand the world from its seed. Shows the seed, whether you are in a
 * slime chunk, the nearest slime chunk, and outlines slime chunks around you.
 * <p>
 * In singleplayer the seed is read from the integrated server. Multiplayer servers never send
 * the seed to clients, so you must type a seed you know (e.g. from /seed) into "Manual Seed".
 * <p>
 * ⚠ CLIENT_SIDE: needs a manually entered seed on multiplayer.
 */
public class SeedInfoModule extends HudModule {
    private final StringSetting manualSeed = add(new StringSetting("Manual Seed", "Seed to use on multiplayer (number or text, like the world creation screen).", "", 64));
    private final BooleanSetting slimeOverlay = add(new BooleanSetting("Slime Chunks", "Outline slime chunks around you.", true));
    private final NumberSetting slimeRadius = add(new NumberSetting("Slime Radius", "Chunks around you to check.", 6, 1, 16, 1)
            .visibleWhen(slimeOverlay::get));
    private final NumberSetting slimeY = add(new NumberSetting("Slime Y", "Height of the slime chunk outlines (slimes spawn below 40).", 30, -64, 320, 1)
            .visibleWhen(slimeOverlay::get));
    private final ColorSetting slimeColor = add(new ColorSetting("Slime Color", "Outline colour.", 0xFF4ADE80));

    // Cache: slime chunk lookups are only recomputed when the chunk, seed or radius changes.
    private long cachedSeed;
    private int cachedChunkX = Integer.MIN_VALUE, cachedChunkZ, cachedRadius;
    private boolean cachedInSlime;
    private int[] cachedNearest;
    private final List<int[]> cachedSlimeChunks = new ArrayList<>();

    public SeedInfoModule() {
        super("Seed Info", "Seed tools: seed display, slime chunk finder and overlay.", Category.WORLD, 0, 60);
        setRisk(Risk.CLIENT_SIDE, "Servers do not send the world seed. On multiplayer, enter a seed you know in the settings.");
    }

    /** Used by {@code \setseed}. */
    public void setManualSeed(String seed) {
        manualSeed.set(seed);
    }

    /** The world seed, or null if unknown. */
    public Long getSeed() {
        if (mc.isInSingleplayer() && mc.getServer() != null) return mc.getServer().getOverworld().getSeed();
        String text = manualSeed.get().trim();
        if (text.isEmpty()) return null;
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return (long) text.hashCode(); // same as vanilla for text seeds
        }
    }

    /** The vanilla slime chunk formula. */
    public static boolean isSlimeChunk(long seed, int chunkX, int chunkZ) {
        Random random = new Random(seed
                + (long) (chunkX * chunkX * 0x4c1906)
                + (long) (chunkX * 0x5ac0db)
                + (long) (chunkZ * chunkZ) * 0x4307a7L
                + (long) (chunkZ * 0x5f24f) ^ 0x3ad8025fL);
        return random.nextInt(10) == 0;
    }

    private boolean inOverworld() {
        return mc.world.getRegistryKey() == World.OVERWORLD;
    }

    @Override
    protected void render(DrawContext ctx, int x, int y, float tickDelta) {
        List<Line> lines = new ArrayList<>();
        Long seed = getSeed();
        if (seed == null) {
            lines.add(new Line("Seed", "unknown (set Manual Seed)", Theme.WARN));
        } else {
            lines.add(new Line("Seed", Long.toString(seed)));
            if (inOverworld()) {
                refreshCache(seed);
                boolean slime = cachedInSlime;
                lines.add(new Line("Slime chunk", slime ? "yes" : "no", slime ? Theme.GOOD : Theme.TEXT_DIM));
                int[] nearest = cachedNearest;
                if (nearest != null) {
                    lines.add(new Line("Nearest slime", (nearest[0] * 16 + 8) + ", " + (nearest[1] * 16 + 8)));
                }
            }
        }
        drawLines(ctx, x, y, lines);
    }

    private void refreshCache(long seed) {
        int cx = mc.player.getBlockX() >> 4, cz = mc.player.getBlockZ() >> 4, r = slimeRadius.getInt();
        if (seed == cachedSeed && cx == cachedChunkX && cz == cachedChunkZ && r == cachedRadius) return;
        cachedSeed = seed;
        cachedChunkX = cx;
        cachedChunkZ = cz;
        cachedRadius = r;
        cachedInSlime = isSlimeChunk(seed, cx, cz);
        cachedNearest = nearestSlimeChunk(seed, cx, cz, 16);
        cachedSlimeChunks.clear();
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (isSlimeChunk(seed, x, z)) cachedSlimeChunks.add(new int[]{x, z});
            }
        }
    }

    private static int[] nearestSlimeChunk(long seed, int cx, int cz, int maxRadius) {
        for (int r = 0; r <= maxRadius; r++) {
            int[] best = null;
            double bestDist = Double.MAX_VALUE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    if (isSlimeChunk(seed, cx + dx, cz + dz)) {
                        double d = dx * dx + dz * dz;
                        if (d < bestDist) {
                            bestDist = d;
                            best = new int[]{cx + dx, cz + dz};
                        }
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        if (!slimeOverlay.get() || !inOverworld()) return;
        Long seed = getSeed();
        if (seed == null) return;
        refreshCache(seed);
        for (int[] chunk : cachedSlimeChunks) {
            double x1 = chunk[0] * 16 + 0.25, z1 = chunk[1] * 16 + 0.25;
            o.square(x1, z1, x1 + 15.5, z1 + 15.5, slimeY.get(), slimeColor.get());
        }
    }
}
