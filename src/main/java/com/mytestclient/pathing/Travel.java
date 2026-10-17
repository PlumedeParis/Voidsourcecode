package com.mytestclient.pathing;

import com.mytestclient.event.KeybindManager;
import com.mytestclient.module.Modules;
import com.mytestclient.module.utility.ChestMemoryModule;
import com.mytestclient.render.Overlay;
import com.mytestclient.util.ChatUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Travel: the tasks built on the pathfinder ({@link Walker}).
 * <ul>
 *     <li>{@code \walk}: walk anywhere (mines and bridges if needed; long trips are planned piece by piece).</li>
 *     <li>{@code \mine diamond 16}: finds the blocks in the loaded chunks, walks to each one, mines it and picks up
 *     the drops. If none are loaded, it goes exploring (at the best height for that ore).</li>
 *     <li>{@code \pickup}: collects the dropped items around you.</li>
 *     <li>{@code \fetch <item>}: walks to a chest Chest Memory remembers holding the item and takes it.</li>
 * </ul>
 * Pressing back/left/right or {@code \stop} cancels.
 */
public final class Travel {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /** Short names for {@code \mine}. */
    public static final Map<String, List<String>> ALIASES = new LinkedHashMap<>();
    /** Best height to explore for an ore (null = surface). */
    private static final Map<String, Integer> BEST_Y = Map.of("diamond", -58, "redstone", -58, "gold", -16, "iron", 16,
            "lapis", 0, "copper", 48, "debris", 15, "ancient_debris", 15, "quartz", 30, "emerald", 100);

    static {
        for (String ore : List.of("diamond", "iron", "gold", "coal", "copper", "emerald", "lapis", "redstone")) {
            ALIASES.put(ore, List.of("minecraft:" + ore + "_ore", "minecraft:deepslate_" + ore + "_ore"));
        }
        ALIASES.put("debris", List.of("minecraft:ancient_debris"));
        ALIASES.put("ancient_debris", List.of("minecraft:ancient_debris"));
        ALIASES.put("quartz", List.of("minecraft:nether_quartz_ore"));
        ALIASES.put("nether_gold", List.of("minecraft:nether_gold_ore"));
        ALIASES.put("wood", List.of("minecraft:oak_log", "minecraft:birch_log", "minecraft:spruce_log", "minecraft:jungle_log",
                "minecraft:acacia_log", "minecraft:dark_oak_log", "minecraft:mangrove_log", "minecraft:cherry_log", "minecraft:pale_oak_log"));
    }

    private interface Task {
        String name();

        /** @return false when finished */
        boolean tick();
    }

    private static Task task;

    private Travel() {
    }

    public static boolean isActive() {
        return task != null;
    }

    public static String status() {
        return task == null ? "idle" : task.name();
    }

    public static void stop(String reason) {
        if (task == null) return;
        String name = task.name();
        task = null;
        Walker.stop();
        ChatUtil.info(name + ": " + reason);
    }

    private static void start(Task t) {
        com.mytestclient.util.PathFollower.stop("replaced");
        com.mytestclient.util.ElytraAutopilot.stop("replaced by walking");
        Walker.stop();
        task = t;
    }

    /** Start of every client tick. */
    public static void tick() {
        if (task == null) return;
        if (mc.player == null || mc.world == null) {
            task = null;
            Walker.stop();
            return;
        }
        if (KeybindManager.isBindingDown(mc.options.backKey) || KeybindManager.isBindingDown(mc.options.leftKey)
                || KeybindManager.isBindingDown(mc.options.rightKey)) {
            stop("cancelled");
            return;
        }
        Walker.tick();
        Task t = task;
        boolean keep;
        try {
            keep = t.tick();
        } catch (Throwable e) {
            com.mytestclient.MyTestClient.LOGGER.warn("Travel task failed", e);
            keep = false;
            ChatUtil.error(t.name() + " failed: " + e);
        }
        if (!keep && task == t) {
            task = null;
            Walker.stop();
        }
    }

    public static void render(Overlay o) {
        Walker.render(o);
    }

    // ------------------------------------------------------------------ walk

    public static void walk(Goal goal, String label) {
        start(new Task() {
            public String name() {
                return "Walk to " + label;
            }

            public boolean tick() {
                if (Walker.state() == Walker.State.IDLE) Walker.walkTo(goal);
                if (Walker.state() == Walker.State.ARRIVED) {
                    ChatUtil.success("Arrived at " + label + "!");
                    return false;
                }
                if (Walker.state() == Walker.State.FAILED) {
                    ChatUtil.error("Walk to " + label + ": " + Walker.failReason());
                    return false;
                }
                return true;
            }
        });
        ChatUtil.success("Walking to " + label + " (pathfinding). \\stop or press S/A/D to cancel.");
    }

    // ------------------------------------------------------------------ mine

    /** Resolves "diamond", "iron", "minecraft:obsidian", "ancient_debris"... to block ids. */
    public static List<String> resolveBlocks(String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (ALIASES.containsKey(q)) return ALIASES.get(q);
        Identifier id = Identifier.tryParse(q.contains(":") ? q : "minecraft:" + q);
        if (id != null && Registries.BLOCK.containsId(id)) return List.of(id.toString());
        // e.g. "diamond_ore" without deepslate variant
        if (id != null && Registries.BLOCK.containsId(Identifier.of(id.getNamespace(), q.replace("minecraft:", "") + "_ore"))) {
            return List.of(id.getNamespace() + ":" + q.replace("minecraft:", "") + "_ore");
        }
        return List.of();
    }

    public static void mine(List<String> blockIds, int count, String label) {
        Set<Block> blocks = new HashSet<>();
        for (String id : blockIds) {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier != null && Registries.BLOCK.containsId(identifier)) blocks.add(Registries.BLOCK.get(identifier));
        }
        if (blocks.isEmpty()) {
            ChatUtil.error("Unknown block: " + label);
            return;
        }
        Integer bestY = null;
        for (Map.Entry<String, Integer> e : BEST_Y.entrySet()) if (label.toLowerCase(Locale.ROOT).contains(e.getKey())) bestY = e.getValue();
        start(new MineTask(blocks, count, label, bestY));
        ChatUtil.success("Mining " + count + "x " + label + ". \\stop or press S/A/D to cancel.");
    }

    private static final class MineTask implements Task {
        enum Step {FIND, GO, BREAK, COLLECT, EXPLORE}

        final Set<Block> blocks;
        final int count;
        final String label;
        final Integer bestY;
        final Set<BlockPos> blacklist = new HashSet<>();
        Step step = Step.FIND;
        BlockPos target;
        int mined, timer, explores;
        float exploreYaw = Float.NaN;

        MineTask(Set<Block> blocks, int count, String label, Integer bestY) {
            this.blocks = blocks;
            this.count = count;
            this.label = label;
            this.bestY = bestY;
        }

        public String name() {
            return "Mine " + label + " (" + mined + "/" + count + ")";
        }

        public boolean tick() {
            if (mc.player.getInventory().getEmptySlot() < 0) {
                ChatUtil.info("Mine: inventory full, stopping (" + mined + " mined).");
                return false;
            }
            switch (step) {
                case FIND -> {
                    target = nearest(blocks::contains, blacklist, 200);
                    if (target == null) {
                        if (++explores > 12) {
                            ChatUtil.error("Mine: no " + label + " found after exploring (anti-xray server?).");
                            return false;
                        }
                        explore();
                        return true;
                    }
                    Walker.walkTo(Goal.reach(target));
                    step = Step.GO;
                    timer = 0;
                }
                case GO -> {
                    if (!blocks.contains(BlockInfo.state(target).getBlock())) {
                        step = Step.FIND; // someone else mined it
                        Walker.stop();
                    } else if (Walker.state() == Walker.State.ARRIVED) {
                        step = Step.BREAK;
                        timer = 0;
                    } else if (Walker.state() == Walker.State.FAILED) {
                        blacklist.add(target);
                        step = Step.FIND;
                    }
                }
                case BREAK -> {
                    if (!blocks.contains(BlockInfo.state(target).getBlock())) {
                        Walker.stopBreaking();
                        mined++;
                        if (mined >= count) {
                            ChatUtil.success("Mine: done, " + mined + "x " + label + ".");
                            collectAround(target);
                            return false;
                        }
                        step = Step.COLLECT;
                        timer = 0;
                        return true;
                    }
                    if (++timer > 400 || !BlockInfo.breakable(target)) {
                        blacklist.add(target);
                        Walker.stopBreaking();
                        step = Step.FIND;
                        return true;
                    }
                    Walker.mine(target);
                }
                case COLLECT -> {
                    timer++;
                    if (timer < 6) return true; // let the drops spawn
                    ItemEntity item = nearestItem(Vec3d.ofCenter(target), 6, e -> true);
                    if (item == null || timer > 120) {
                        step = Step.FIND;
                        Walker.stop();
                        return true;
                    }
                    if (Walker.state() != Walker.State.WALKING && Walker.state() != Walker.State.SEARCHING) {
                        Walker.walkTo(Goal.near(item.getBlockPos(), 0.9));
                    }
                }
                case EXPLORE -> {
                    if (Walker.state() == Walker.State.ARRIVED || Walker.state() == Walker.State.FAILED) step = Step.FIND;
                    else if (++timer % 100 == 0 && nearest(blocks::contains, blacklist, 200) != null) {
                        Walker.stop();
                        step = Step.FIND;
                    }
                }
            }
            return true;
        }

        private void explore() {
            ClientPlayerEntity p = mc.player;
            if (Float.isNaN(exploreYaw)) exploreYaw = p.getYaw();
            else exploreYaw += 30; // vary the direction a little each time
            double rad = Math.toRadians(exploreYaw + 90);
            int x = (int) (p.getX() + Math.cos(rad) * 64), z = (int) (p.getZ() + Math.sin(rad) * 64);
            if (bestY != null && mc.world.getDimension().minY() <= bestY) {
                ChatUtil.info("Mine: no " + label + " loaded nearby, tunnelling at y=" + bestY + " towards " + x + " " + z + ".");
                Walker.walkTo(Goal.near(new BlockPos(x, bestY, z), 3));
            } else {
                ChatUtil.info("Mine: no " + label + " loaded nearby, exploring towards " + x + " " + z + ".");
                Walker.walkTo(Goal.xz(x, z));
            }
            step = Step.EXPLORE;
            timer = 0;
        }
    }

    /** Collect drops around a spot (after the last block). */
    private static void collectAround(BlockPos pos) {
        ItemEntity item = nearestItem(Vec3d.ofCenter(pos), 6, e -> true);
        if (item != null) pickup(null, 8);
    }

    /** Nearest loaded block matching, by distance with vertical distance weighted (digging is slow). */
    public static BlockPos nearest(Predicate<Block> match, Set<BlockPos> skip, int maxResults) {
        ClientPlayerEntity p = mc.player;
        int view = mc.options.getClampedViewDistance() + 1;
        int pcx = p.getChunkPos().x, pcz = p.getChunkPos().z;
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        Predicate<BlockState> stateMatch = s -> match.test(s.getBlock());
        for (int cx = pcx - view; cx <= pcx + view; cx++) {
            for (int cz = pcz - view; cz <= pcz + view; cz++) {
                if (!mc.world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                WorldChunk chunk = mc.world.getChunk(cx, cz);
                ChunkSection[] sections = chunk.getSectionArray();
                for (int i = 0; i < sections.length; i++) {
                    ChunkSection section = sections[i];
                    if (section == null || section.isEmpty() || !section.hasAny(stateMatch)) continue;
                    int baseY = chunk.sectionIndexToCoord(i) << 4;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                if (!stateMatch.test(section.getBlockState(x, y, z))) continue;
                                BlockPos pos = new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z);
                                if (skip.contains(pos)) continue;
                                double dx = pos.getX() - p.getX(), dy = pos.getY() - p.getY(), dz = pos.getZ() - p.getZ();
                                double score = Math.sqrt(dx * dx + dz * dz) + Math.abs(dy) * 1.5;
                                if (score < bestScore) {
                                    bestScore = score;
                                    best = pos;
                                }
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ pickup

    private static ItemEntity nearestItem(Vec3d around, double radius, Predicate<ItemEntity> filter) {
        ItemEntity best = null;
        double bestD = radius * radius;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ItemEntity item) || !item.isAlive() || !filter.test(item)) continue;
            double d = e.squaredDistanceTo(around);
            if (d < bestD) {
                bestD = d;
                best = item;
            }
        }
        return best;
    }

    /** Walks to dropped items (matching {@code query}, or all) within {@code radius} until none are left. */
    public static void pickup(String query, double radius) {
        String q = query == null ? null : query.toLowerCase(Locale.ROOT);
        Predicate<ItemEntity> filter = item -> q == null
                || Registries.ITEM.getId(item.getStack().getItem()).toString().contains(q)
                || item.getStack().getName().getString().toLowerCase(Locale.ROOT).contains(q);
        Set<Integer> unreachable = new HashSet<>();
        Predicate<ItemEntity> reachable = item -> !unreachable.contains(item.getId()) && filter.test(item);
        start(new Task() {
            int picked, timer;
            ItemEntity current;

            public String name() {
                return "Pick up" + (q == null ? "" : " " + q);
            }

            public boolean tick() {
                if (mc.player.getInventory().getEmptySlot() < 0) {
                    ChatUtil.info("Pick up: inventory full.");
                    return false;
                }
                if (current == null || !current.isAlive() || ++timer > 200) {
                    if (current != null && !current.isAlive()) picked++;
                    current = nearestItem(mc.player.getEyePos(), radius, reachable);
                    timer = 0;
                    if (current == null) {
                        ChatUtil.success("Pick up: done (" + picked + " stacks).");
                        return false;
                    }
                    Walker.walkTo(Goal.near(current.getBlockPos(), 0.9));
                }
                if (Walker.state() == Walker.State.FAILED || Walker.state() == Walker.State.ARRIVED) {
                    if (current.squaredDistanceTo(mc.player) > 4) {
                        if (Walker.state() == Walker.State.FAILED) unreachable.add(current.getId()); // don't retry it forever
                        current = null;
                    }
                }
                return true;
            }
        });
        ChatUtil.success("Picking up items" + (q == null ? "" : " matching '" + q + "'") + " within " + (int) radius + " blocks.");
    }

    // ------------------------------------------------------------------ fetch

    /** Walks to remembered containers holding {@code query} and takes it out. */
    public static void fetch(String query) {
        ChestMemoryModule memory = Modules.get(ChestMemoryModule.class);
        List<BlockPos> chests = memory == null ? List.of() : memory.containersWith(query);
        if (chests.isEmpty()) {
            ChatUtil.error("No remembered container in this dimension holds '" + query + "'. Enable Chest Memory and open your chests once.");
            return;
        }
        String q = query.toLowerCase(Locale.ROOT);
        List<BlockPos> queue = new ArrayList<>(chests);
        start(new Task() {
            BlockPos chest;
            int timer, taken;
            boolean opened;

            public String name() {
                return "Fetch " + query;
            }

            public boolean tick() {
                if (chest == null) {
                    if (queue.isEmpty()) {
                        ChatUtil.success("Fetch: done, took " + taken + " stack(s) of " + query + ".");
                        return false;
                    }
                    chest = queue.remove(0);
                    opened = false;
                    timer = 0;
                    Walker.walkTo(Goal.reach(chest));
                    return true;
                }
                if (Walker.state() == Walker.State.FAILED) {
                    ChatUtil.error("Fetch: can't reach the container at " + chest.toShortString());
                    chest = null;
                    return true;
                }
                if (Walker.state() != Walker.State.ARRIVED) return true;
                ScreenHandler handler = mc.player.currentScreenHandler;
                if (!opened) {
                    Vec3d hit = Vec3d.ofCenter(chest);
                    com.mytestclient.util.CombatUtil.lookAt(hit);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, chest, false));
                    opened = true;
                    timer = 0;
                    return true;
                }
                if (handler == mc.player.playerScreenHandler) {
                    if (++timer > 40) {
                        ChatUtil.error("Fetch: the container at " + chest.toShortString() + " didn't open.");
                        chest = null;
                    }
                    return true;
                }
                if (++timer < 5) return true; // let the contents arrive
                int containerSlots = handler.slots.size() - 36;
                for (int i = 0; i < containerSlots; i++) {
                    var stack = handler.slots.get(i).getStack();
                    if (stack.isEmpty()) continue;
                    String id = Registries.ITEM.getId(stack.getItem()).toString();
                    if (id.contains(q) || stack.getName().getString().toLowerCase(Locale.ROOT).contains(q)) {
                        mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                        taken++;
                    }
                }
                mc.player.closeHandledScreen();
                chest = null;
                return true;
            }
        });
        ChatUtil.success("Fetching '" + query + "' from " + chests.size() + " remembered container(s).");
    }

    /** Clamped helper for commands. */
    public static int clampCount(int n) {
        return MathHelper.clamp(n, 1, 2304);
    }
}
