package com.voidclient.module.world;

import com.voidclient.gui.Theme;
import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.Risk;
import com.voidclient.module.setting.BooleanSetting;
import com.voidclient.module.setting.ModeSetting;
import com.voidclient.module.setting.NumberSetting;
import com.voidclient.render.Colors;
import com.voidclient.render.Overlay;
import com.voidclient.util.ChatUtil;
import com.voidclient.util.CombatUtil;
import com.voidclient.util.Digger;
import com.voidclient.util.Lane;
import com.voidclient.util.Notifications;
import net.minecraft.block.BlockState;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Highway Builder: builds a highway in a straight line (8 directions) and walks along it by
 * itself: digs the tunnel, lays the floor, adds guard rails, then steps forward and repeats.
 * <p>
 * Stand where the highway should run, look along it and turn the module on: direction and
 * floor level are locked. It takes the floor block from your hotbar and refills from your
 * inventory; when you run out it pauses and tells you. ⚠ DETECTABLE.
 */
public class HighwayBuilderModule extends Module {
    private final NumberSetting width = add(new NumberSetting("Width", "Floor width in blocks.", 5, 1, 9, 1));
    private final NumberSetting height = add(new NumberSetting("Height", "Clear height above the floor.", 3, 2, 6, 1));
    private final BooleanSetting rails = add(new BooleanSetting("Rails", "Guard rails (one block high) on both edges.", true));
    private final ModeSetting material = add(new ModeSetting("Material", "Block used for the floor and rails.", "Obsidian", "Obsidian", "Netherrack", "Cobblestone", "Any hotbar block"));
    private final BooleanSetting replaceFloor = add(new BooleanSetting("Replace Floor", "Dig out existing floor blocks that are not the chosen material.", false));
    private final BooleanSetting mine = add(new BooleanSetting("Clear Tunnel", "Mine everything in the way above the floor.", true));
    private final NumberSetting placesPerTick = add(new NumberSetting("Places Per Tick", "Blocks placed per tick.", 2, 1, 8, 1));
    private final NumberSetting breaksPerTick = add(new NumberSetting("Breaks Per Tick", "Instant-break blocks mined per tick.", 8, 1, 30, 1));
    private final BooleanSetting diagonals = add(new BooleanSetting("Diagonals", "Allow diagonal highways.", true));
    private final BooleanSetting autoWalk = add(new BooleanSetting("Auto Walk", "Walk forward when the section in front of you is finished.", true));
    private final BooleanSetting preview = add(new BooleanSetting("Preview", "Outline the blocks still to place.", true));

    private Lane lane;
    private int floorY;
    private boolean walking, edging;
    private int placed, mined, emptyTicks;
    private long startTime;
    private int startAlong;

    public HighwayBuilderModule() {
        super("Highway Builder", "Builds a highway by itself: tunnel, floor and guard rails, in 8 directions.", Category.WORLD);
        setRisk(Risk.DETECTABLE, "Automated mining, placing and walking.");
    }

    @Override
    public boolean persistsState() {
        return false;
    }

    @Override
    protected void onEnable() {
        if (mc.player == null) return;
        lane = Lane.fromYaw(mc.player.getYaw(), mc.player.getBlockPos(), diagonals.get());
        floorY = mc.player.getBlockY() - 1;
        placed = mined = emptyTicks = 0;
        startTime = System.currentTimeMillis();
        startAlong = 0;
        ChatUtil.info("Highway Builder: " + width.getInt() + " wide heading " + lane.name() + ", floor at y " + floorY
                + ". " + Digger.count(matcher()) + " " + material.get().toLowerCase() + " blocks in your inventory.");
    }

    @Override
    protected void onDisable() {
        Digger.stop();
        if (walking || edging) KeyBinding.updatePressedStates();
        walking = edging = false;
        if (lane != null && mc.player != null) {
            int blocks = Math.max(0, lane.along(mc.player.getBlockX(), mc.player.getBlockZ()) - startAlong) / (lane.diagonal() ? 2 : 1);
            long s = Math.max(1, (System.currentTimeMillis() - startTime) / 1000);
            ChatUtil.info(String.format("Highway Builder stopped: %d blocks of highway, %d placed, in %d:%02d.", blocks, placed, s / 60, s % 60));
        }
        lane = null;
    }

    private Predicate<ItemStack> matcher() {
        return switch (material.get()) {
            case "Obsidian" -> s -> s.isOf(Items.OBSIDIAN) || s.isOf(Items.CRYING_OBSIDIAN);
            case "Netherrack" -> s -> s.isOf(Items.NETHERRACK);
            case "Cobblestone" -> s -> s.isOf(Items.COBBLESTONE) || s.isOf(Items.COBBLED_DEEPSLATE);
            default -> s -> s.getItem() instanceof BlockItem b && b.getBlock().getDefaultState().isFullCube(mc.world, BlockPos.ORIGIN)
                    && !(b.getBlock() instanceof net.minecraft.block.FallingBlock);
        };
    }

    private boolean isMaterial(BlockState state) {
        return matcher().test(new ItemStack(state.getBlock().asItem()));
    }

    /** What a position should be: 0 = not part of the highway, 1 = solid (floor/rail), 2 = air. */
    private int role(int x, int y, int z) {
        int lat = Math.abs(lane.lateral(x, z));
        int half = lane.half(width.getInt());
        int railLat = half + 1;
        if (lat <= half) {
            if (y == floorY) return 1;
            if (y > floorY && y <= floorY + height.getInt()) return 2;
        } else if (rails.get() && lat == railLat) {
            if (y == floorY || y == floorY + 1) return 1;
            if (y > floorY + 1 && y <= floorY + height.getInt()) return 2;
        }
        return 0;
    }

    private void collect(List<BlockPos> toBreak, List<BlockPos> toPlace, int ahead) {
        int px = mc.player.getBlockX(), pz = mc.player.getBlockZ();
        int here = lane.along(px, pz);
        int r = 6 + width.getInt() / 2;
        for (int x = px - r; x <= px + r; x++) {
            for (int z = pz - r; z <= pz + r; z++) {
                int a = lane.along(x, z) - here;
                if (a < -lane.steps(1) || a > lane.steps(ahead)) continue;
                for (int y = floorY; y <= floorY + height.getInt(); y++) {
                    int role = role(x, y, z);
                    if (role == 0) continue;
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);
                    if (role == 2) {
                        if (mine.get() && Digger.breakable(pos)) toBreak.add(pos);
                    } else if (state.isReplaceable()) {
                        toPlace.add(pos);
                    } else if (replaceFloor.get() && !isMaterial(state) && Digger.breakable(pos)) {
                        toBreak.add(pos);
                    }
                }
            }
        }
    }

    @Override
    public void onPreTick() {
        if (walking || edging) mc.options.forwardKey.setPressed(true);
        if (edging) mc.options.sneakKey.setPressed(true); // sneaking: never walks off the edge
    }

    @Override
    public void onTick() {
        if (lane == null) {
            onEnable();
            if (lane == null) return;
        }
        List<BlockPos> toBreak = new ArrayList<>(), toPlace = new ArrayList<>();
        collect(toBreak, toPlace, 4);
        int before = toBreak.size();
        boolean digging = Digger.work(toBreak, breaksPerTick.getInt(), true, 5.0);
        mined += Math.max(0, before - toBreak.size());

        // place nearest first, so every block has a neighbour to click
        Vec3d feet = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        toPlace.sort((a, b) -> Double.compare(feet.squaredDistanceTo(Vec3d.ofCenter(a)), feet.squaredDistanceTo(Vec3d.ofCenter(b))));
        boolean outOfBlocks = false;
        int done = 0;
        if (!toPlace.isEmpty()) {
            int slot = Digger.blockSlot(matcher());
            if (slot < 0) outOfBlocks = true;
            else {
                Box me = mc.player.getBoundingBox();
                for (BlockPos pos : toPlace) {
                    if (done >= placesPerTick.getInt()) break;
                    if (me.intersects(new Box(pos))) continue;
                    if (CombatUtil.place(pos, slot, true)) {
                        done++;
                        placed++;
                    }
                }
            }
        }
        if (outOfBlocks) {
            if (emptyTicks++ == 0) {
                Notifications.warn("Highway Builder: out of " + material.get().toLowerCase());
                ChatUtil.error("Highway Builder paused: no " + material.get().toLowerCase() + " left. Refill your inventory and it continues.");
            }
        } else emptyTicks = 0;

        // walk on when the next two blocks in front are finished
        List<BlockPos> nearBreak = new ArrayList<>(), nearPlace = new ArrayList<>();
        collect(nearBreak, nearPlace, 2);
        boolean wasWalking = walking || edging;
        walking = autoWalk.get() && !outOfBlocks && nearBreak.isEmpty() && nearPlace.isEmpty() && mc.currentScreen == null;
        // Blocks to place but none could be clicked from here (Strict Direction: the face must be
        // visible): sneak forward to the edge of the floor, like bridging by hand, and place from there.
        edging = autoWalk.get() && !walking && !outOfBlocks && !digging && done == 0 && !toPlace.isEmpty() && mc.currentScreen == null;
        if (walking || edging) {
            // steer back to the centre line
            double off = lane.offset(mc.player.getX(), mc.player.getZ());
            mc.player.setYaw(lane.yaw() + (float) Math.max(-25, Math.min(25, off * 25)));
        }
        if (wasWalking && !walking && !edging) KeyBinding.updatePressedStates();
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        if (!preview.get() || lane == null) return;
        List<BlockPos> toBreak = new ArrayList<>(), toPlace = new ArrayList<>();
        collect(toBreak, toPlace, 4);
        int n = 0;
        for (BlockPos p : toPlace) {
            if (n++ > 60) break;
            o.box(new Box(p), Colors.withAlpha(Theme.GOOD, 0xA0));
        }
    }
}
