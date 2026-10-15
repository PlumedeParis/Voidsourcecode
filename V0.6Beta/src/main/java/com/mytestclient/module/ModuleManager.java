package com.mytestclient.module;

import com.mytestclient.module.combat.AutoCrystalModule;
import com.mytestclient.module.combat.AutoTotemModule;
import com.mytestclient.module.hud.ArmorHud;
import com.mytestclient.module.hud.CoordinatesHud;
import com.mytestclient.module.hud.InfoHud;
import com.mytestclient.module.hud.KeystrokesHud;
import com.mytestclient.module.hud.ModuleListHud;
import com.mytestclient.module.hud.PotionHud;
import com.mytestclient.module.misc.ClickGuiModule;
import com.mytestclient.module.misc.HudEditorModule;
import com.mytestclient.module.movement.FlyModule;
import com.mytestclient.module.movement.FreecamModule;
import com.mytestclient.module.movement.SpeedModule;
import com.mytestclient.module.movement.SprintModule;
import com.mytestclient.module.utility.ItemInfoModule;
import com.mytestclient.module.utility.RadarModule;
import com.mytestclient.module.utility.TargetInfoModule;
import com.mytestclient.module.visuals.ChunkBordersModule;
import com.mytestclient.module.visuals.EspModule;
import com.mytestclient.module.visuals.FullbrightModule;
import com.mytestclient.module.visuals.LightOverlayModule;
import com.mytestclient.module.visuals.StorageEspModule;
import com.mytestclient.module.visuals.ZoomModule;
import com.mytestclient.module.world.NewChunksModule;
import com.mytestclient.module.world.QuickWaypointModule;
import com.mytestclient.module.world.SeedInfoModule;
import com.mytestclient.module.world.SeedMapModule;
import com.mytestclient.module.world.TimeChangerModule;
import com.mytestclient.module.world.WaypointsModule;
import com.mytestclient.module.world.WeatherChangerModule;

import com.mytestclient.module.hud.CompassHud;
import com.mytestclient.module.hud.InventoryHud;
import com.mytestclient.module.misc.AntiAfkModule;
import com.mytestclient.module.misc.AutoRespawnModule;
import com.mytestclient.module.movement.AirJumpModule;
import com.mytestclient.module.movement.AutoWalkModule;
import com.mytestclient.module.movement.ElytraFlyModule;
import com.mytestclient.module.movement.EntityControlModule;
import com.mytestclient.module.movement.NoFallModule;
import com.mytestclient.module.movement.SafeWalkModule;
import com.mytestclient.module.movement.ScaffoldModule;
import com.mytestclient.module.movement.StepModule;
import com.mytestclient.module.utility.AutoEatModule;
import com.mytestclient.module.utility.AutoToolModule;
import com.mytestclient.module.utility.CopyCoordsModule;
import com.mytestclient.module.utility.DurabilityAlertModule;
import com.mytestclient.module.visuals.BlockSearchModule;
import com.mytestclient.module.visuals.BreadcrumbsModule;
import com.mytestclient.module.visuals.NoHurtCamModule;
import com.mytestclient.module.world.FastPlaceModule;

import com.mytestclient.module.combat.KillAuraModule;
import com.mytestclient.module.movement.SpiderModule;
import com.mytestclient.module.utility.ChestMemoryModule;
import com.mytestclient.module.utility.PlayerTrackerModule;
import com.mytestclient.module.visuals.TrajectoriesModule;
import com.mytestclient.module.world.EventLocatorModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns every module. To add or remove a feature, edit {@link #registerDefaults()} — nothing
 * else needs to change.
 */
public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();
    private final Map<Class<?>, Module> byClass = new HashMap<>();

    public ModuleManager() {
        registerDefaults();
    }

    private void registerDefaults() {
        // Movement
        register(new FlyModule());
        register(new SpeedModule());
        register(new FreecamModule());
        register(new SprintModule());
        register(new AirJumpModule());
        register(new ScaffoldModule());
        register(new ElytraFlyModule());
        register(new EntityControlModule());
        register(new StepModule());
        register(new SafeWalkModule());
        register(new AutoWalkModule());
        register(new NoFallModule());
        register(new SpiderModule());
        register(new com.mytestclient.module.movement.WaterWalkModule());

        // Visuals
        register(new FullbrightModule());
        register(new EspModule());
        register(new StorageEspModule());
        register(new ChunkBordersModule());
        register(new LightOverlayModule());
        register(new ZoomModule());
        register(new BlockSearchModule());
        register(new BreadcrumbsModule());
        register(new NoHurtCamModule());
        register(new TrajectoriesModule());

        // World
        register(new TimeChangerModule());
        register(new WeatherChangerModule());
        register(new NewChunksModule());
        register(new SeedInfoModule());
        register(new SeedMapModule());
        register(new WaypointsModule());
        register(new QuickWaypointModule());
        register(new FastPlaceModule());
        register(new EventLocatorModule());

        // Combat
        register(new AutoCrystalModule());
        register(new AutoTotemModule());
        register(new KillAuraModule());
        register(new com.mytestclient.module.combat.CriticalsModule());
        register(new com.mytestclient.module.combat.TriggerBotModule());
        register(new com.mytestclient.module.combat.AutoClickerModule());
        register(new com.mytestclient.module.combat.AutoWeaponModule());
        register(new com.mytestclient.module.combat.VelocityModule());
        register(new com.mytestclient.module.combat.SurroundModule());
        register(new com.mytestclient.module.combat.AutoArmorModule());
        register(new com.mytestclient.module.combat.AutoGappleModule());
        register(new com.mytestclient.module.combat.AutoPotModule());
        register(new com.mytestclient.module.combat.BowAimbotModule());
        register(new com.mytestclient.module.combat.AutoLogModule());

        // Utility
        register(new RadarModule());
        register(new TargetInfoModule());
        register(new ItemInfoModule());
        register(new AutoToolModule());
        register(new AutoEatModule());
        register(new DurabilityAlertModule());
        register(new CopyCoordsModule());
        register(new ChestMemoryModule());
        register(new PlayerTrackerModule());

        // HUD
        register(new CoordinatesHud());
        register(new ModuleListHud());
        register(new InfoHud());
        register(new ArmorHud());
        register(new PotionHud());
        register(new KeystrokesHud());
        register(new CompassHud());
        register(new InventoryHud());

        // Misc
        register(new ClickGuiModule());
        register(new HudEditorModule());
        register(new AutoRespawnModule());
        register(new AntiAfkModule());
    }

    public void register(Module module) {
        if (byClass.containsKey(module.getClass())) {
            throw new IllegalStateException("Module registered twice: " + module.getName());
        }
        modules.add(module);
        byClass.put(module.getClass(), module);
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> type) {
        return (T) byClass.get(type);
    }

    public Module byName(String name) {
        for (Module module : modules) {
            if (module.getName().equalsIgnoreCase(name)) return module;
        }
        return null;
    }

    public List<Module> all() {
        return Collections.unmodifiableList(modules);
    }

    public List<Module> byCategory(Category category) {
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            if (module.getCategory() == category) result.add(module);
        }
        return result;
    }
}
