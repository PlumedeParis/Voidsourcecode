package com.mytestclient.module.utility;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mytestclient.config.JsonFiles;
import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.render.Overlay;
import com.mytestclient.util.ChatUtil;
import com.mytestclient.waypoint.WaypointManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chest Memory: remembers what is inside every container you open (chests, barrels, shulkers,
 * hoppers...) for each world. Then {@code \find diamond} tells you which containers hold
 * diamonds and highlights them in the world. Saved to {@code config/mytestclient/chests.json}.
 * Only uses what you already saw, no warning.
 */
public class ChestMemoryModule extends Module {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("mytestclient").resolve("chests.json");

    private final NumberSetting highlightSeconds = add(new NumberSetting("Highlight Time", "Seconds that \\find results stay highlighted.", 60, 5, 600, 5));
    private final BooleanSetting showAll = add(new BooleanSetting("Show Remembered", "Faintly outline every remembered container nearby.", false));

    /** One remembered container. {@code items}: item id -> "count|display name". */
    private record Container(String world, String dim, BlockPos pos, Map<String, Integer> counts, Map<String, String> names, long time) {
    }

    private final Map<String, Container> containers = new LinkedHashMap<>();
    private final List<BlockPos> highlights = new ArrayList<>();
    private long highlightUntil;
    private BlockPos openPos;
    private boolean loaded, dirty;

    public ChestMemoryModule() {
        super("Chest Memory", "Remembers the contents of containers you open. Use \\find <item> to locate items.", Category.UTILITY);
    }

    private static String key(String world, String dim, BlockPos pos) {
        return world + "|" + dim + "|" + pos.asLong();
    }

    @Override
    public void onTick() {
        ensureLoaded();
        if (mc.currentScreen instanceof HandledScreen<?> screen
                && !(screen instanceof InventoryScreen) && !(screen instanceof CreativeInventoryScreen)) {
            if (openPos == null && mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                    && (mc.world.getBlockEntity(hit.getBlockPos()) instanceof net.minecraft.inventory.Inventory
                    || mc.world.getBlockEntity(hit.getBlockPos()) instanceof net.minecraft.block.entity.EnderChestBlockEntity)) {
                openPos = hit.getBlockPos().toImmutable();
            }
            if (openPos != null) snapshot(screen);
        } else if (openPos != null) {
            openPos = null;
            if (dirty) save();
        }
    }

    private void snapshot(HandledScreen<?> screen) {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot.inventory instanceof PlayerInventory) continue;
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;
            String id = Registries.ITEM.getId(stack.getItem()).toString();
            counts.merge(id, stack.getCount(), Integer::sum);
            names.putIfAbsent(id, stack.getName().getString());
        }
        String world = WaypointManager.currentWorldKey(), dim = WaypointManager.currentDimension();
        String key = key(world, dim, openPos);
        Container old = containers.get(key);
        if (old != null && old.counts.equals(counts)) return;
        containers.put(key, new Container(world, dim, openPos, counts, names, System.currentTimeMillis()));
        dirty = true;
    }

    /** {@code \find}: searches remembered containers in this world, prints and highlights results. */
    public void find(String query) {
        ensureLoaded();
        String q = query.toLowerCase(Locale.ROOT).trim();
        String world = WaypointManager.currentWorldKey(), dim = WaypointManager.currentDimension();
        record Result(Container c, int count, String name, double dist) {
        }
        List<Result> results = new ArrayList<>();
        for (Container c : containers.values()) {
            if (!c.world.equals(world)) continue;
            int total = 0;
            String name = null;
            for (Map.Entry<String, Integer> e : c.counts.entrySet()) {
                String itemName = c.names.getOrDefault(e.getKey(), e.getKey());
                if (e.getKey().contains(q) || itemName.toLowerCase(Locale.ROOT).contains(q)) {
                    total += e.getValue();
                    name = itemName;
                }
            }
            if (total == 0) continue;
            double dist = c.dim.equals(dim) && mc.player != null
                    ? Math.sqrt(mc.player.squaredDistanceTo(c.pos.getX() + 0.5, c.pos.getY() + 0.5, c.pos.getZ() + 0.5)) : Double.MAX_VALUE;
            results.add(new Result(c, total, name, dist));
        }
        if (results.isEmpty()) {
            ChatUtil.error("No remembered container holds '" + query + "'. Open your chests once so they are remembered.");
            return;
        }
        results.sort(Comparator.comparingDouble(Result::dist));
        highlights.clear();
        ChatUtil.success("Found '" + query + "' in " + results.size() + " container(s):");
        for (int i = 0; i < Math.min(10, results.size()); i++) {
            Result r = results.get(i);
            String where = r.c.pos.getX() + " " + r.c.pos.getY() + " " + r.c.pos.getZ();
            String dist = r.dist == Double.MAX_VALUE ? r.c.dim : String.format("%.0fm", r.dist);
            ChatUtil.info(" " + r.count + "x " + r.name + " @ " + where + " (" + dist + ")");
            if (r.c.dim.equals(dim)) highlights.add(r.c.pos);
        }
        highlightUntil = System.currentTimeMillis() + highlightSeconds.getInt() * 1000L;
    }

    /** Remembered containers in this world and dimension holding {@code query}, closest first. */
    public java.util.List<BlockPos> containersWith(String query) {
        ensureLoaded();
        String q = query.toLowerCase(Locale.ROOT).trim();
        String world = WaypointManager.currentWorldKey(), dim = WaypointManager.currentDimension();
        List<BlockPos> result = new ArrayList<>();
        for (Container c : containers.values()) {
            if (!c.world.equals(world) || !c.dim.equals(dim)) continue;
            for (Map.Entry<String, Integer> e : c.counts.entrySet()) {
                String itemName = c.names.getOrDefault(e.getKey(), e.getKey());
                if (e.getKey().contains(q) || itemName.toLowerCase(Locale.ROOT).contains(q)) {
                    result.add(c.pos);
                    break;
                }
            }
        }
        if (mc.player != null) result.sort(Comparator.comparingDouble(pos -> mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)));
        return result;
    }

    public int rememberedCount() {
        ensureLoaded();
        return containers.size();
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        if (System.currentTimeMillis() < highlightUntil) {
            for (BlockPos p : highlights) {
                o.box(new Box(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1), 0xFF4ADE80);
                o.tracer(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 0x904ADE80);
            }
        }
        if (showAll.get()) {
            String world = WaypointManager.currentWorldKey(), dim = WaypointManager.currentDimension();
            for (Container c : containers.values()) {
                if (!c.world.equals(world) || !c.dim.equals(dim)) continue;
                if (mc.player.squaredDistanceTo(c.pos.getX(), c.pos.getY(), c.pos.getZ()) > 48 * 48) continue;
                BlockPos p = c.pos;
                o.box(new Box(p.getX() + 0.1, p.getY() + 0.1, p.getZ() + 0.1, p.getX() + 0.9, p.getY() + 0.9, p.getZ() + 0.9), 0x504ADE80);
            }
        }
    }

    // ------------------------------------------------------------------ persistence

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        JsonElement json = JsonFiles.read(FILE);
        if (json == null || !json.isJsonArray()) return;
        for (JsonElement e : json.getAsJsonArray()) {
            try {
                JsonObject o = e.getAsJsonObject();
                Map<String, Integer> counts = new HashMap<>();
                Map<String, String> names = new HashMap<>();
                for (Map.Entry<String, JsonElement> item : o.getAsJsonObject("items").entrySet()) {
                    JsonObject v = item.getValue().getAsJsonObject();
                    counts.put(item.getKey(), v.get("count").getAsInt());
                    names.put(item.getKey(), v.get("name").getAsString());
                }
                BlockPos pos = BlockPos.fromLong(o.get("pos").getAsLong());
                String world = o.get("world").getAsString(), dim = o.get("dim").getAsString();
                containers.put(key(world, dim, pos), new Container(world, dim, pos, counts, names, o.get("time").getAsLong()));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void save() {
        dirty = false;
        JsonArray array = new JsonArray();
        for (Container c : containers.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("world", c.world);
            o.addProperty("dim", c.dim);
            o.addProperty("pos", c.pos.asLong());
            o.addProperty("time", c.time);
            JsonObject items = new JsonObject();
            for (Map.Entry<String, Integer> e : c.counts.entrySet()) {
                JsonObject v = new JsonObject();
                v.addProperty("count", e.getValue());
                v.addProperty("name", c.names.getOrDefault(e.getKey(), e.getKey()));
                items.add(e.getKey(), v);
            }
            o.add("items", items);
            array.add(o);
        }
        JsonFiles.write(FILE, array);
    }

    @Override
    public void onShutdown() {
        if (dirty) save();
    }
}
