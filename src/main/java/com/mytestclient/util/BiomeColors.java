package com.mytestclient.util;

import java.util.HashMap;
import java.util.Map;

/** Map colours for biomes (in the spirit of Amidst/Chunkbase). Unknown biomes get a stable hashed colour. */
public final class BiomeColors {
    private static final Map<String, Integer> COLORS = new HashMap<>();

    static {
        // water
        put(0x000070, "ocean");
        put(0x000030, "deep_ocean");
        put(0x0000AC, "warm_ocean");
        put(0x000090, "lukewarm_ocean", "deep_lukewarm_ocean");
        put(0x202070, "cold_ocean", "deep_cold_ocean");
        put(0x7070D6, "frozen_ocean");
        put(0x404090, "deep_frozen_ocean");
        put(0x0000FF, "river");
        put(0xA0A0FF, "frozen_river");
        // plains / forests
        put(0x8DB360, "plains");
        put(0xB5DB88, "sunflower_plains");
        put(0xFFFFFF, "snowy_plains");
        put(0xB4DCDC, "ice_spikes");
        put(0x056621, "forest");
        put(0x2D8E49, "flower_forest");
        put(0x307444, "birch_forest");
        put(0x589C6C, "old_growth_birch_forest");
        put(0x40511A, "dark_forest");
        put(0x6B5F3A, "pale_garden");
        put(0x0B6659, "taiga");
        put(0x31554A, "snowy_taiga");
        put(0x596651, "old_growth_pine_taiga");
        put(0x818E79, "old_growth_spruce_taiga");
        put(0x537B09, "jungle");
        put(0x628B17, "sparse_jungle");
        put(0x768E14, "bamboo_jungle");
        put(0x07F9B2, "swamp");
        put(0x2CCC8E, "mangrove_swamp");
        put(0x60A445, "meadow");
        put(0xF4C4D4, "cherry_grove");
        // dry
        put(0xFA9418, "desert");
        put(0xBDB25F, "savanna");
        put(0xA79D64, "savanna_plateau");
        put(0xE5DA87, "windswept_savanna");
        put(0xD94515, "badlands");
        put(0xB09765, "wooded_badlands");
        put(0xFF6D3D, "eroded_badlands");
        // mountains
        put(0x606060, "windswept_hills");
        put(0x789878, "windswept_gravelly_hills");
        put(0x507050, "windswept_forest");
        put(0xD0D0D0, "grove");
        put(0xE0E0F0, "snowy_slopes");
        put(0xC0C0E0, "frozen_peaks");
        put(0xA0C0E0, "jagged_peaks");
        put(0x909090, "stony_peaks");
        put(0xA2A284, "stony_shore");
        put(0xFADE55, "beach");
        put(0xFAF0C0, "snowy_beach");
        put(0xFF00FF, "mushroom_fields");
        // underground
        put(0x4F6E52, "lush_caves");
        put(0x5A4A3A, "dripstone_caves");
        put(0x0B1A24, "deep_dark");
        // nether
        put(0x572526, "nether_wastes");
        put(0x49907B, "soul_sand_valley");
        put(0xAA1A1A, "crimson_forest");
        put(0x167E86, "warped_forest");
        put(0x403636, "basalt_deltas");
        // end
        put(0x8080FF, "the_end");
        put(0x4A3A6A, "small_end_islands");
        put(0xB5B5D0, "end_midlands");
        put(0xC8C8E8, "end_highlands");
        put(0x30304A, "end_barrens");
        put(0x000000, "the_void");
    }

    private BiomeColors() {
    }

    private static void put(int rgb, String... paths) {
        for (String p : paths) COLORS.put(p, rgb | 0xFF000000);
    }

    public static int get(String biomeId) {
        String path = biomeId.substring(biomeId.indexOf(':') + 1);
        Integer c = COLORS.get(path);
        if (c != null) return c;
        int h = path.hashCode();
        return 0xFF000000 | (0x40 + (h & 0x7F)) << 16 | (0x40 + ((h >> 8) & 0x7F)) << 8 | (0x40 + ((h >> 16) & 0x7F));
    }

    /** "dark_forest" -> "Dark Forest" */
    public static String pretty(String biomeId) {
        String path = biomeId.substring(biomeId.indexOf(':') + 1);
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("_")) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
