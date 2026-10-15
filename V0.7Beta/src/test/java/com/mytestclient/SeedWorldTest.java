package com.mytestclient;

import com.mytestclient.util.SeedWorld;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the client-side world generator headless to make sure it works outside a server. */
public class SeedWorldTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void generatesBiomesAndStructures() {
        long seed = 12345L;
        long t = System.currentTimeMillis();
        SeedWorld overworld = SeedWorld.get(seed, SeedWorld.Dim.OVERWORLD);
        System.out.println("[seedworld] overworld built in " + (System.currentTimeMillis() - t) + " ms");

        String spawnBiome = SeedWorld.biomeId(overworld.biomeAt(0, 64, 0));
        System.out.println("[seedworld] biome at 0,64,0 = " + spawnBiome + ", surface y = " + overworld.surfaceY(0, 0));
        assertTrue(spawnBiome.startsWith("minecraft:"));

        t = System.currentTimeMillis();
        int samples = 0;
        for (int x = -2048; x < 2048; x += 64) for (int z = -2048; z < 2048; z += 64) {
            overworld.biomeAt(x, 64, z);
            samples++;
        }
        System.out.println("[seedworld] " + samples + " biome samples in " + (System.currentTimeMillis() - t) + " ms");

        assertFalse(overworld.structureSets().isEmpty(), "structure sets");
        for (String s : new String[]{"village_", "pillager_outpost", "monument", "mansion", "stronghold", "ancient_city", "trial_chambers"}) {
            t = System.currentTimeMillis();
            SeedWorld.Found f = overworld.nearest(id -> id.contains(s), 0, 0, 40);
            System.out.println("[seedworld] nearest " + s + " -> " + (f == null ? "none" : f.id() + " @ " + f.x() + " " + f.y() + " " + f.z())
                    + " (" + (System.currentTimeMillis() - t) + " ms)");
            if (!s.equals("mansion")) assertNotNull(f, s);
        }

        SeedWorld nether = SeedWorld.get(seed, SeedWorld.Dim.NETHER);
        System.out.println("[seedworld] nether biome at 0,64,0 = " + SeedWorld.biomeId(nether.biomeAt(0, 64, 0)));
        for (String s : new String[]{"fortress", "bastion_remnant"}) {
            SeedWorld.Found f = nether.nearest(id -> id.endsWith(s), 0, 0, 20);
            System.out.println("[seedworld] nearest " + s + " -> " + (f == null ? "none" : f.x() + " " + f.z()));
            assertNotNull(f, s);
        }

        SeedWorld end = SeedWorld.get(seed, SeedWorld.Dim.END);
        SeedWorld.Found city = end.nearest(id -> id.endsWith("end_city"), 1500, 0, 20);
        System.out.println("[seedworld] nearest end city from 1500,0 -> " + (city == null ? "none" : city.x() + " " + city.z()));
    }
}
