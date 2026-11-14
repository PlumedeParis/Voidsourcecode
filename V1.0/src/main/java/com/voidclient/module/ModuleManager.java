package com.voidclient.module;

import com.voidclient.module.combat.AutoCrystalModule;
import com.voidclient.module.combat.AutoTotemModule;
import com.voidclient.module.hud.ArmorHud;
import com.voidclient.module.hud.CoordinatesHud;
import com.voidclient.module.hud.InfoHud;
import com.voidclient.module.hud.KeystrokesHud;
import com.voidclient.module.hud.ModuleListHud;
import com.voidclient.module.hud.PotionHud;
import com.voidclient.module.misc.ClickGuiModule;
import com.voidclient.module.misc.HudEditorModule;
import com.voidclient.module.movement.FlyModule;
import com.voidclient.module.movement.FreecamModule;
import com.voidclient.module.movement.SpeedModule;
import com.voidclient.module.movement.SprintModule;
import com.voidclient.module.utility.ItemInfoModule;
import com.voidclient.module.utility.RadarModule;
import com.voidclient.module.utility.TargetInfoModule;
import com.voidclient.module.visuals.EspModule;
import com.voidclient.module.visuals.FullbrightModule;
import com.voidclient.module.visuals.StorageEspModule;
import com.voidclient.module.world.NewChunksModule;
import com.voidclient.module.world.QuickWaypointModule;
import com.voidclient.module.world.SeedInfoModule;
import com.voidclient.module.world.SeedMapModule;
import com.voidclient.module.world.TimeChangerModule;
import com.voidclient.module.world.WaypointsModule;
import com.voidclient.module.world.WeatherChangerModule;

import com.voidclient.module.hud.CompassHud;
import com.voidclient.module.hud.InventoryHud;
import com.voidclient.module.misc.AntiAfkModule;
import com.voidclient.module.misc.AutoRespawnModule;
import com.voidclient.module.movement.AirJumpModule;
import com.voidclient.module.movement.AutoWalkModule;
import com.voidclient.module.movement.ElytraFlyModule;
import com.voidclient.module.movement.EntityControlModule;
import com.voidclient.module.movement.NoFallModule;
import com.voidclient.module.movement.SafeWalkModule;
import com.voidclient.module.movement.ScaffoldModule;
import com.voidclient.module.movement.StepModule;
import com.voidclient.module.utility.AutoEatModule;
import com.voidclient.module.utility.AutoToolModule;
import com.voidclient.module.utility.CopyCoordsModule;
import com.voidclient.module.utility.DurabilityAlertModule;
import com.voidclient.module.visuals.BlockSearchModule;
import com.voidclient.module.visuals.BreadcrumbsModule;
import com.voidclient.module.visuals.NoHurtCamModule;
import com.voidclient.module.world.FastPlaceModule;

import com.voidclient.module.combat.KillAuraModule;
import com.voidclient.module.movement.SpiderModule;
import com.voidclient.module.utility.ChestMemoryModule;
import com.voidclient.module.utility.PlayerTrackerModule;
import com.voidclient.module.visuals.TrajectoriesModule;
import com.voidclient.module.world.EventLocatorModule;

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
        register(new com.voidclient.module.movement.WaterWalkModule());
        register(new com.voidclient.module.movement.PathfinderModule());
        register(new com.voidclient.module.movement.ElytraRangeModule());
        register(new com.voidclient.module.movement.NoSlowModule());
        register(new com.voidclient.module.movement.BlinkModule());
        register(new com.voidclient.module.movement.ElytraReplaceModule());
        register(new com.voidclient.module.movement.ParkourModule());
        register(new com.voidclient.module.movement.InventoryMoveModule());

        // Visuals
        register(new FullbrightModule());
        register(new EspModule());
        register(new StorageEspModule());
        register(new com.voidclient.module.visuals.ZoomModule());
        register(new BlockSearchModule());
        register(new BreadcrumbsModule());
        register(new NoHurtCamModule());
        register(new TrajectoriesModule());
        register(new com.voidclient.module.visuals.LightOverlayModule());
        register(new com.voidclient.module.visuals.LogoutSpotsModule());
        register(new com.voidclient.module.visuals.NametagsModule());
        register(new com.voidclient.module.visuals.HoleEspModule());

        // World
        register(new TimeChangerModule());
        register(new WeatherChangerModule());
        register(new NewChunksModule());
        register(new SeedInfoModule());
        register(new SeedMapModule());
        register(new com.voidclient.module.world.SeedFinderModule());
        register(new com.voidclient.module.world.AutoMineModule());
        register(new com.voidclient.module.world.ItemCollectorModule());
        register(new WaypointsModule());
        register(new QuickWaypointModule());
        register(new FastPlaceModule());
        register(new EventLocatorModule());
        register(new com.voidclient.module.world.HighwayCompassModule());
        register(new com.voidclient.module.world.TunnelMinerModule());
        register(new com.voidclient.module.world.HighwayBuilderModule());
        register(new com.voidclient.module.world.BuilderModule());
        register(new com.voidclient.module.world.StashFinderModule());

        // Combat
        register(new AutoCrystalModule());
        register(new AutoTotemModule());
        register(new KillAuraModule());
        register(new com.voidclient.module.combat.CriticalsModule());
        register(new com.voidclient.module.combat.TriggerBotModule());
        register(new com.voidclient.module.combat.AutoClickerModule());
        register(new com.voidclient.module.combat.AutoWeaponModule());
        register(new com.voidclient.module.combat.VelocityModule());
        register(new com.voidclient.module.combat.SurroundModule());
        register(new com.voidclient.module.combat.AutoArmorModule());
        register(new com.voidclient.module.combat.AutoGappleModule());
        register(new com.voidclient.module.combat.AutoPotModule());
        register(new com.voidclient.module.combat.BowAimbotModule());
        register(new com.voidclient.module.combat.AutoLogModule());
        register(new com.voidclient.module.combat.DeathRecapModule());
        register(new com.voidclient.module.combat.AutoXpModule());
        register(new com.voidclient.module.combat.AutoTrapModule());
        register(new com.voidclient.module.combat.HoleFillerModule());

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
        register(new com.voidclient.module.utility.PlayerFinderModule());
        register(new com.voidclient.module.utility.TailAlarmModule());
        register(new com.voidclient.module.utility.NotifierModule());
        register(new com.voidclient.module.utility.AutoFishModule());
        register(new com.voidclient.module.utility.MiddleClickModule());
        register(new com.voidclient.module.utility.AutoReplenishModule());
        register(new com.voidclient.module.utility.ChestStealerModule());
        register(new com.voidclient.module.utility.ChestSwapModule());

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
        register(new com.voidclient.module.misc.PlayerProgrammingModule());
        register(new AntiAfkModule());
        register(new com.voidclient.module.misc.LeakGuardModule());
        register(new com.voidclient.module.misc.BypassModule());
        register(new com.voidclient.module.misc.ChatTimestampsModule());
        register(new com.voidclient.module.misc.FakePlayerModule());
        register(new com.voidclient.module.misc.SpammerModule());
        register(new com.voidclient.module.misc.AutoReconnectModule());
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
