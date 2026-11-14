package com.voidclient;

import com.voidclient.config.ConfigManager;
import com.voidclient.config.ProfileManager;
import com.voidclient.event.ClientEvents;
import com.voidclient.event.KeybindManager;
import com.voidclient.hud.HudRenderer;
import com.voidclient.module.ModuleManager;
import com.voidclient.waypoint.WaypointManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Main entry point of VoidClient.
 * <p>
 * Wires together the managers (modules, config, profiles, waypoints, keybinds) and registers
 * all Fabric callbacks. Everything else hangs off {@link #get()}.
 */
public class VoidClient implements ClientModInitializer {
    public static final String MOD_ID = "voidclient";
    public static final String NAME = "VoidClient";
    /** Display version such as "V0.6 Beta": {@code release_label} in gradle.properties, via fabric.mod.json. */
    public static final String VERSION = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(MOD_ID)
            .map(c -> c.getMetadata().containsCustomValue("voidclient:label")
                    ? c.getMetadata().getCustomValue("voidclient:label").getAsString()
                    : c.getMetadata().getVersion().getFriendlyString())
            .orElse("dev");
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    private static VoidClient instance;

    private ModuleManager moduleManager;
    private ConfigManager configManager;
    private ProfileManager profileManager;
    private WaypointManager waypointManager;
    private KeybindManager keybindManager;

    @Override
    public void onInitializeClient() {
        instance = this;

        Path dir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        migrateOldConfig(dir);
        moduleManager = new ModuleManager();
        configManager = new ConfigManager(dir.resolve("config.json"), moduleManager);
        profileManager = new ProfileManager(dir.resolve("profiles"), moduleManager);
        waypointManager = new WaypointManager(dir.resolve("waypoints.json"));
        keybindManager = new KeybindManager(moduleManager);

        ClientEvents.register(this);
        HudRenderer.register();

        LOGGER.info("{} {} initialised with {} modules", NAME, VERSION, moduleManager.all().size());
    }

    /** Before V1 the mod id (and config folder) was "mytestclient": move that folder over once. */
    private static void migrateOldConfig(Path dir) {
        Path old = dir.resolveSibling("mytestclient");
        try {
            if (!java.nio.file.Files.exists(dir) && java.nio.file.Files.isDirectory(old)) {
                java.nio.file.Files.move(old, dir);
                LOGGER.info("Moved the old config folder {} to {}", old, dir);
            }
        } catch (Exception e) {
            LOGGER.error("Could not move the old config folder {}; starting with default settings", old, e);
        }
    }

    public static VoidClient get() {
        return instance;
    }

    public ModuleManager modules() {
        return moduleManager;
    }

    public ConfigManager config() {
        return configManager;
    }

    public ProfileManager profiles() {
        return profileManager;
    }

    public WaypointManager waypoints() {
        return waypointManager;
    }

    public KeybindManager keybinds() {
        return keybindManager;
    }
}
