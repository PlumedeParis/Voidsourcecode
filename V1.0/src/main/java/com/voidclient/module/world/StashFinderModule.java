package com.voidclient.module.world;

import com.voidclient.VoidClient;
import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.setting.BooleanSetting;
import com.voidclient.module.setting.NumberSetting;
import com.voidclient.util.ChatUtil;
import com.voidclient.util.Notifications;
import com.voidclient.waypoint.Waypoint;
import com.voidclient.waypoint.WaypointManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

/**
 * Stash Finder: while you travel, every chunk that loads is checked for storage (chests,
 * barrels, shulker boxes, hoppers...). A chunk with many of them is someone's stash or base:
 * you get a notification, a waypoint, and a line in config/voidclient/stashes.txt.
 */
public class StashFinderModule extends Module {
    private final NumberSetting minimum = add(new NumberSetting("Minimum", "Storage blocks in one chunk to count as a stash.", 12, 2, 100, 1));
    private final NumberSetting spawnRadius = add(new NumberSetting("Ignore Spawn", "Ignore chunks closer than this to 0 0 (blocks).", 1000, 0, 50000, 500));
    private final BooleanSetting waypoint = add(new BooleanSetting("Waypoint", "Add a waypoint at each stash.", true));
    private final BooleanSetting shulkersCount = add(new BooleanSetting("Shulkers ×4", "Count each shulker box as four (they hold the valuables).", true));

    private final Set<Long> reported = new HashSet<>();

    public StashFinderModule() {
        super("Stash Finder", "Finds chunks full of chests and shulker boxes while you travel, and logs them.", Category.WORLD);
    }

    @Override
    public void onWorldLeave() {
        reported.clear();
    }

    /** From the chunk load event (client thread). */
    public void onChunkLoad(WorldChunk chunk) {
        if (mc.player == null) return;
        ChunkPos cp = chunk.getPos();
        int cx = cp.getCenterX(), cz = cp.getCenterZ();
        if (Math.hypot(cx, cz) < spawnRadius.get() || reported.contains(cp.toLong())) return;
        int chests = 0, shulkers = 0, other = 0, sumY = 0, n = 0;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof ShulkerBoxBlockEntity) shulkers++;
            else if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) chests++;
            else if (be instanceof HopperBlockEntity || be instanceof DispenserBlockEntity || be instanceof EnderChestBlockEntity) other++;
            else continue;
            sumY += be.getPos().getY();
            n++;
        }
        int score = chests + other + shulkers * (shulkersCount.get() ? 4 : 1);
        if (score < minimum.getInt()) return;
        reported.add(cp.toLong());
        int y = sumY / Math.max(1, n);
        String dim = WaypointManager.currentDimension();
        String summary = chests + " chests, " + shulkers + " shulkers, " + other + " other";
        Notifications.warn("Stash found: " + summary);
        ChatUtil.success("Stash Finder: " + summary + " at " + cx + " " + y + " " + cz + ".");
        if (waypoint.get()) {
            VoidClient.get().waypoints().add(new Waypoint("Stash (" + (chests + shulkers) + ")", cx, y, cz, dim, WaypointManager.currentWorldKey(), 0xFFFBBF24));
        }
        try {
            Path file = FabricLoader.getInstance().getConfigDir().resolve("voidclient").resolve("stashes.txt");
            Files.createDirectories(file.getParent());
            String line = String.format("%s  %s  %s  %d %d %d  %s%n", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                    WaypointManager.currentWorldKey(), dim, cx, y, cz, summary);
            Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            VoidClient.LOGGER.warn("Could not write stashes.txt", e);
        }
    }
}
