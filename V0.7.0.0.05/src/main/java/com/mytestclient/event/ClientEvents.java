package com.mytestclient.event;

import com.mytestclient.MyTestClient;
import com.mytestclient.command.CommandManager;
import com.mytestclient.util.PathFollower;
import com.mytestclient.util.ElytraAutopilot;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import com.mytestclient.mixin.SimpleOptionAccessor;
import com.mytestclient.module.Module;
import com.mytestclient.module.Modules;
import com.mytestclient.module.SafeMode;
import com.mytestclient.module.utility.ItemInfoModule;
import com.mytestclient.module.world.NewChunksModule;
import com.mytestclient.util.Notifications;
import com.mytestclient.util.TickRate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;

import java.util.HashSet;
import java.util.Set;

/**
 * Registers every Fabric callback the client uses: lifecycle, ticks, connection, chunk loading
 * and item tooltips. Module hooks are wrapped so one faulty module can never crash the game.
 */
public final class ClientEvents {
    private static net.minecraft.client.world.ClientWorld lastWorld;
    private static final int AUTOSAVE_TICKS = 20 * 60 * 5;
    private static final Set<Module> REPORTED = new HashSet<>();
    private static int autosaveTimer;

    private ClientEvents() {
    }

    public static void register(MyTestClient client) {
        // Config is loaded once the game is fully started so game options exist.
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            repairOptions(mc);
            client.config().load();
            client.waypoints().load();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            for (Module module : client.modules().all()) safely(module, "shutdown", module::onShutdown);
            client.config().save();
            client.waypoints().save();
        });

        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            if (!inWorld(mc)) return;
            if (lastWorld != null && mc.world != lastWorld) {
                // went through a portal (new world instance): per-world data is stale
                for (Module module : client.modules().all()) safely(module, "leave", module::onWorldLeave);
                com.mytestclient.pathing.Travel.stop("changed dimension");
            }
            lastWorld = mc.world;
            PathFollower.tick();
            com.mytestclient.pathing.Travel.tick();
            for (Module module : client.modules().all()) {
                if (module.isEnabled()) safely(module, "pre-tick", module::onPreTick);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            client.keybinds().tick(mc);
            if (inWorld(mc)) {
                for (Module module : client.modules().all()) {
                    if (module.isEnabled()) safely(module, "tick", module::onTick);
                }
            }
            if (inWorld(mc)) ElytraAutopilot.tick();
            Notifications.tick();
            if (++autosaveTimer >= AUTOSAVE_TICKS) {
                autosaveTimer = 0;
                client.config().save();
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> mc.execute(() -> {
            SafeMode.enforce();
            for (Module module : client.modules().all()) safely(module, "join", module::onWorldJoin);
        }));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(() -> {
            for (Module module : client.modules().all()) safely(module, "leave", module::onWorldLeave);
            TickRate.reset();
            PathFollower.stop("left the world");
            ElytraAutopilot.stop("left the world");
            com.mytestclient.pathing.Travel.stop("left the world");
            lastWorld = null;
            client.config().save();
            client.waypoints().save();
        }));

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            NewChunksModule newChunks = Modules.get(NewChunksModule.class);
            if (newChunks != null) newChunks.onChunkLoad(chunk.getPos());
            com.mytestclient.module.utility.PlayerFinderModule finder = Modules.get(com.mytestclient.module.utility.PlayerFinderModule.class);
            if (finder != null && finder.isEnabled()) {
                try {
                    finder.onChunkLoad(chunk);
                } catch (Throwable t) {
                    com.mytestclient.MyTestClient.LOGGER.warn("Base scan failed", t);
                }
            }
        });

        // "\" chat commands are handled locally and never sent to the server
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> !CommandManager.handle(message));

        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            ItemInfoModule info = Modules.get(ItemInfoModule.class);
            if (info != null && info.isEnabled()) info.appendTooltip(stack, lines);
        });
    }

    /** Older versions of Zoom/Fullbright could leave an out-of-range FOV or gamma in options.txt. */
    private static void repairOptions(MinecraftClient mc) {
        int fov = mc.options.getFov().getValue();
        if (fov < 30 || fov > 110) {
            ((SimpleOptionAccessor) (Object) mc.options.getFov()).mytestclient$setValueDirect(70);
            MyTestClient.LOGGER.info("Repaired invalid FOV option ({} -> 70)", fov);
        }
        double gamma = mc.options.getGamma().getValue();
        if (gamma < 0 || gamma > 1) {
            ((SimpleOptionAccessor) (Object) mc.options.getGamma()).mytestclient$setValueDirect(1.0);
            MyTestClient.LOGGER.info("Repaired invalid gamma option ({} -> 1.0)", gamma);
        }
    }

    private static boolean inWorld(MinecraftClient mc) {
        return mc.player != null && mc.world != null;
    }

    /**
     * Runs a module hook; if it throws, the module is disabled and the error logged once,
     * instead of crashing the game.
     */
    public static void safely(Module module, String hook, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            if (REPORTED.add(module)) MyTestClient.LOGGER.error("Module {} failed in {}", module.getName(), hook, t);
            if (module.isEnabled()) {
                module.setEnabledQuietly(false);
                Notifications.warn(module.getName() + " crashed and was disabled (see log)");
            }
        }
    }
}
