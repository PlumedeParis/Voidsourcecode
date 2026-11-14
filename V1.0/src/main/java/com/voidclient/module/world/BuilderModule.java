package com.voidclient.module.world;

import com.voidclient.build.Schematic;
import com.voidclient.gui.Theme;
import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.Risk;
import com.voidclient.module.setting.BooleanSetting;
import com.voidclient.module.setting.ModeSetting;
import com.voidclient.module.setting.NumberSetting;
import com.voidclient.module.setting.StringSetting;
import com.voidclient.render.Colors;
import com.voidclient.render.Overlay;
import com.voidclient.util.ChatUtil;
import com.voidclient.util.CombatUtil;
import com.voidclient.util.Digger;
import com.voidclient.util.Notifications;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Builder: builds a schematic for you. Put a {@code .litematic}, {@code .schem} or {@code .nbt}
 * file in {@code .minecraft/config/voidclient/schematics/}, stand where its corner should be
 * and run {@code \build <file>}. The plan is anchored at your feet (extending the way you
 * look), shown as a ghost outline, and blocks within reach are placed from your inventory,
 * bottom layer first. Walk around the build (or enable Auto Walk) and it fills in.
 * <p>
 * Limits: it places the right block type but not its orientation (stairs, logs, slabs face the
 * way a normal click would make them). ⚠ DETECTABLE.
 */
public class BuilderModule extends Module {
    public final StringSetting file = add(new StringSetting("File", "Schematic file name in config/voidclient/schematics/ (or use \\build <file>).", "", 64));
    private final NumberSetting perTick = add(new NumberSetting("Places Per Tick", "Blocks placed per tick.", 2, 1, 8, 1));
    private final ModeSetting rotation = add(new ModeSetting("Rotation", "Auto: the plan extends in front of you. Or a fixed quarter turn.", "Auto", "Auto", "0°", "90°", "180°", "270°"));
    private final BooleanSetting clear = add(new BooleanSetting("Clear Obstructions", "Mine blocks that are in the way of the plan.", false));
    private final BooleanSetting autoWalk = add(new BooleanSetting("Auto Walk", "Walk to the next block to place with the pathfinder.", false));
    private final BooleanSetting ghost = add(new BooleanSetting("Ghost", "Outline the blocks still missing near you.", true));

    private Schematic plan;
    private BlockPos anchor;
    private int turns;
    private int placed, total;
    private final Map<Block, Integer> missing = new LinkedHashMap<>();
    private int reportTimer, walkTimer;
    private boolean finished;

    public BuilderModule() {
        super("Builder", "Builds a schematic (.litematic, .schem, .nbt) from your inventory. Start it with \\build <file>.", Category.WORLD);
        setRisk(Risk.DETECTABLE, "Automated block placement.");
    }

    @Override
    public boolean persistsState() {
        return false;
    }

    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve("voidclient").resolve("schematics");
    }

    public static List<String> files() {
        List<String> out = new ArrayList<>();
        try {
            Files.createDirectories(folder());
            try (Stream<Path> s = Files.list(folder())) {
                s.forEach(p -> {
                    String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (n.endsWith(".litematic") || n.endsWith(".schem") || n.endsWith(".nbt")) out.add(p.getFileName().toString());
                });
            }
        } catch (Exception ignored) {
        }
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    @Override
    protected void onEnable() {
        if (mc.player == null) return;
        plan = null;
        String name = file.get().trim();
        if (name.isEmpty()) {
            ChatUtil.error("Builder: no file chosen. Put a schematic in " + folder() + " and use \\build <file>.");
            setEnabled(false);
            return;
        }
        try {
            Path path = folder().resolve(name).normalize();
            if (!path.startsWith(folder())) throw new IllegalArgumentException("File must be inside the schematics folder");
            if (!Files.exists(path)) {
                for (String f : files()) if (f.toLowerCase(Locale.ROOT).startsWith(name.toLowerCase(Locale.ROOT))) path = folder().resolve(f);
            }
            plan = Schematic.load(path);
        } catch (Exception e) {
            ChatUtil.error("Builder: could not load '" + name + "': " + e.getMessage());
            setEnabled(false);
            return;
        }
        anchor = mc.player.getBlockPos();
        turns = switch (rotation.get()) {
            case "90°" -> 1;
            case "180°" -> 2;
            case "270°" -> 3;
            case "0°" -> 0;
            default -> switch (mc.player.getHorizontalFacing()) { // plan's +X/+Z quadrant in front of you
                case SOUTH -> 0;
                case WEST -> 1;
                case NORTH -> 2;
                default -> 3;
            };
        };
        total = plan.total();
        placed = 0;
        finished = false;
        ChatUtil.success(String.format("Builder: %s loaded, %d×%d×%d, %,d blocks. Anchored at %s.", plan.name, plan.sizeX, plan.sizeY, plan.sizeZ, total, anchor.toShortString()));
    }

    @Override
    protected void onDisable() {
        Digger.stop();
        if (plan != null && autoWalk.get()) com.voidclient.pathing.Travel.stop("builder off");
        plan = null;
    }

    /** World position of a plan cell (after the quarter turns). */
    private BlockPos world(int x, int y, int z) {
        int rx = x, rz = z;
        for (int i = 0; i < turns; i++) {
            int t = rx;
            rx = -rz;
            rz = t;
        }
        return anchor.add(rx, y, rz);
    }

    /** Plan block wanted at a world position, or null. */
    private Block wanted(BlockPos pos) {
        int rx = pos.getX() - anchor.getX(), rz = pos.getZ() - anchor.getZ();
        for (int i = 0; i < turns; i++) { // inverse turn
            int t = rx;
            rx = rz;
            rz = -t;
        }
        return plan.get(rx, pos.getY() - anchor.getY(), rz);
    }

    public void printMaterials() {
        if (plan == null) {
            ChatUtil.error("Builder is not running.");
            return;
        }
        ChatUtil.info("Materials for " + plan.name + " (needed / in your inventory):");
        plan.materials().entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(25).forEach(e -> {
            int have = Digger.count(s -> s.getItem() == e.getKey().asItem());
            ChatUtil.info("  " + e.getKey().getName().getString() + ": " + e.getValue() + " / " + have);
        });
    }

    @Override
    public void onTick() {
        if (plan == null) return;
        Vec3d eye = mc.player.getEyePos();
        int r = 5;
        BlockPos me = mc.player.getBlockPos();
        List<BlockPos> toPlace = new ArrayList<>(), toBreak = new ArrayList<>();
        for (int y = -r; y <= r; y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = me.add(x, y, z);
                    Block want = wanted(pos);
                    BlockState state = mc.world.getBlockState(pos);
                    if (want == null || state.getBlock() == want) continue;
                    if (state.isReplaceable()) toPlace.add(pos);
                    else if (clear.get() && Digger.breakable(pos)) toBreak.add(pos);
                }
            }
        }
        if (!toBreak.isEmpty()) Digger.work(toBreak, 6, true, 4.8);
        // lowest layer first, then nearest
        toPlace.sort((a, b) -> a.getY() != b.getY() ? Integer.compare(a.getY(), b.getY())
                : Double.compare(eye.squaredDistanceTo(Vec3d.ofCenter(a)), eye.squaredDistanceTo(Vec3d.ofCenter(b))));
        missing.clear();
        int done = 0;
        Box body = mc.player.getBoundingBox();
        for (BlockPos pos : toPlace) {
            if (done >= perTick.getInt()) break;
            if (body.intersects(new Box(pos))) continue;
            Block want = wanted(pos);
            int slot = Digger.blockSlot(s -> s.getItem() == want.asItem());
            if (slot < 0) {
                missing.merge(want, 1, Integer::sum);
                continue;
            }
            if (CombatUtil.place(pos, slot, true)) {
                done++;
                placed++;
            }
        }
        if (++reportTimer >= 100) {
            reportTimer = 0;
            if (!missing.isEmpty()) {
                Block first = missing.keySet().iterator().next();
                Notifications.warn("Builder needs " + first.getName().getString() + (missing.size() > 1 ? " (+" + (missing.size() - 1) + " more)" : ""));
            }
            checkFinished();
        }
        if (autoWalk.get() && toPlace.isEmpty() && toBreak.isEmpty() && ++walkTimer >= 20) {
            walkTimer = 0;
            walkToNext();
        }
    }

    /** Scans the whole plan (cheap: only loaded chunks are checked) for what is left. */
    private BlockPos nextMissing() {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        Vec3d me = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        for (int y = 0; y < plan.sizeY; y++) {
            for (int z = 0; z < plan.sizeZ; z++) {
                for (int x = 0; x < plan.sizeX; x++) {
                    Block want = plan.get(x, y, z);
                    if (want == null) continue;
                    BlockPos pos = world(x, y, z);
                    if (!mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                    if (mc.world.getBlockState(pos).getBlock() == want) continue;
                    if (Digger.count(s -> s.getItem() == want.asItem()) == 0) continue;
                    double d = me.squaredDistanceTo(Vec3d.ofCenter(pos)) + (pos.getY() - anchor.getY()) * 400.0; // lower layers first
                    if (d < bestD) {
                        bestD = d;
                        best = pos;
                    }
                }
            }
            if (best != null) return best; // finish this layer before going up
        }
        return best;
    }

    private void walkToNext() {
        if (com.voidclient.pathing.Travel.isActive()) return;
        BlockPos next = nextMissing();
        if (next != null) com.voidclient.pathing.Travel.walk(com.voidclient.pathing.Goal.near(next, 3), "builder");
    }

    private void checkFinished() {
        if (finished) return;
        for (int y = 0; y < plan.sizeY; y++) {
            for (int z = 0; z < plan.sizeZ; z++) {
                for (int x = 0; x < plan.sizeX; x++) {
                    Block want = plan.get(x, y, z);
                    if (want == null) continue;
                    BlockPos pos = world(x, y, z);
                    if (!mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4) || mc.world.getBlockState(pos).getBlock() != want) return;
                }
            }
        }
        finished = true;
        ChatUtil.success("Builder: " + plan.name + " is finished (" + placed + " blocks placed this session).");
        Notifications.info("Build finished");
        setEnabled(false);
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        if (plan == null || !ghost.get()) return;
        BlockPos me = mc.player.getBlockPos();
        int r = 7, n = 0;
        for (int y = -r; y <= r && n < 120; y++) {
            for (int x = -r; x <= r && n < 120; x++) {
                for (int z = -r; z <= r && n < 120; z++) {
                    BlockPos pos = me.add(x, y, z);
                    Block want = wanted(pos);
                    if (want == null) continue;
                    BlockState state = mc.world.getBlockState(pos);
                    if (state.getBlock() == want) continue;
                    n++;
                    o.box(new Box(pos), state.isReplaceable() ? Colors.withAlpha(Theme.accent2(), 0x80) : Colors.withAlpha(Theme.BAD, 0xA0));
                }
            }
        }
    }
}
