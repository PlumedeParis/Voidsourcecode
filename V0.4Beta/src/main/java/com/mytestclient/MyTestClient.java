package com.mytestclient;

import com.mytestclient.config.ConfigManager;
import com.mytestclient.config.ProfileManager;
import com.mytestclient.event.ClientEvents;
import com.mytestclient.event.KeybindManager;
import com.mytestclient.hud.HudRenderer;
import com.mytestclient.module.ModuleManager;
import com.mytestclient.waypoint.WaypointManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Main entry point of MyTestClient.
 * <p>
 * Wires together the managers (modules, config, profiles, waypoints, keybinds) and registers
 * all Fabric callbacks. Everything else hangs off {@link #get()}.
 */
public class MyTestClient implements ClientModInitializer {
    public static final String MOD_ID = "mytestclient";
    public static final String NAME = "VoidClient";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    private static MyTestClient instance;

    private ModuleManager moduleManager;
    private ConfigManager configManager;
    private ProfileManager profileManager;
    private WaypointManager waypointManager;
    private KeybindManager keybindManager;

    @Override
    public void onInitializeClient() {
        instance = this;

        Path dir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        moduleManager = new ModuleManager();
        configManager = new ConfigManager(dir.resolve("config.json"), moduleManager);
        profileManager = new ProfileManager(dir.resolve("profiles"), moduleManager);
        waypointManager = new WaypointManager(dir.resolve("waypoints.json"));
        keybindManager = new KeybindManager(moduleManager);

        ClientEvents.register(this);
        HudRenderer.register();

        LOGGER.info("{} {} initialised with {} modules", NAME, VERSION, moduleManager.all().size());
    }

    public static MyTestClient get() {
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
