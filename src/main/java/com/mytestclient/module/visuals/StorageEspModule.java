package com.mytestclient.module.visuals;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.ColorSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.render.Overlay;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Storage ESP: outlines containers (chests, barrels, shulkers...) in loaded chunks.
 * Scans block entities every half second rather than every frame.
 * <p>
 * ⚠ CLIENT_SIDE: visual only, but an information advantage many servers forbid.
 */
public class StorageEspModule extends Module {
    private final NumberSetting radius = add(new NumberSetting("Chunk Radius", "How many chunks around you to scan.", 6, 1, 16, 1));
    private final BooleanSetting chests = add(new BooleanSetting("Chests", "Chests and trapped chests.", true));
    private final BooleanSetting barrels = add(new BooleanSetting("Barrels", "Barrels.", true));
    private final BooleanSetting shulkers = add(new BooleanSetting("Shulkers", "Shulker boxes.", true));
    private final BooleanSetting enderChests = add(new BooleanSetting("Ender Chests", "Ender chests.", true));
    private final BooleanSetting utility = add(new BooleanSetting("Furnaces/Hoppers", "Furnaces, hoppers, droppers and dispensers.", false));
    private final BooleanSetting tracers = add(new BooleanSetting("Tracers", "Draw a line to each container.", false));
    private final ColorSetting chestColor = add(new ColorSetting("Chest Color", "Colour for chests and barrels.", 0xFFF59E0B));
    private final ColorSetting shulkerColor = add(new ColorSetting("Shulker Color", "Colour for shulker boxes.", 0xFFEC4899));
    private final ColorSetting enderColor = add(new ColorSetting("Ender Color", "Colour for ender chests.", 0xFFA855F7));
    private final ColorSetting utilityColor = add(new ColorSetting("Utility Color", "Colour for furnaces/hoppers/etc.", 0xFF94A3B8));

    private record Target(BlockPos pos, Box box, int color) {
    }

    private List<Target> targets = new ArrayList<>();
    private int timer;

    public StorageEspModule() {
        super("Storage ESP", "Outline chests, barrels, shulkers and other containers through walls.", Category.VISUALS);
        setRisk(Risk.CLIENT_SIDE, "Visual only — servers cannot see it — but an information advantage many servers forbid.");
    }

    @Override
    public void onTick() {
        if (timer-- > 0) return;
        timer = 10;
        List<Target> found = new ArrayList<>();
        int cx = mc.player.getBlockX() >> 4, cz = mc.player.getBlockZ() >> 4, r = radius.getInt();
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (!mc.world.getChunkManager().isChunkLoaded(x, z)) continue;
                WorldChunk chunk = mc.world.getChunk(x, z);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    Integer color = colorFor(be);
                    if (color == null) continue;
                    BlockPos p = be.getPos();
                    boolean small = be instanceof ChestBlockEntity || be instanceof EnderChestBlockEntity;
                    double in = small ? 0.0625 : 0.0;
                    Box box = new Box(p.getX() + in, p.getY(), p.getZ() + in, p.getX() + 1 - in, p.getY() + (small ? 0.875 : 1), p.getZ() + 1 - in);
                    // double chest: one box for both halves (drawn by the "left" half only)
                    BlockState state = be.getCachedState();
                    if (be instanceof ChestBlockEntity && state.contains(ChestBlock.CHEST_TYPE) && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
                        if (state.get(ChestBlock.CHEST_TYPE) == ChestType.RIGHT) continue;
                        BlockPos other = p.offset(ChestBlock.getFacing(state));
                        box = box.union(new Box(other.getX() + in, other.getY(), other.getZ() + in, other.getX() + 1 - in, other.getY() + 0.875, other.getZ() + 1 - in));
                    }
                    found.add(new Target(p, box, color));
                }
            }
        }
        // nearest first, and not thousands of boxes in storage halls
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        found.sort(java.util.Comparator.comparingDouble(t -> t.pos.getSquaredDistance(px, py, pz)));
        targets = found.size() > MAX ? new ArrayList<>(found.subList(0, MAX)) : found;
    }

    private static final int MAX = 400;

    private Integer colorFor(BlockEntity be) {
        if (be instanceof ChestBlockEntity) return chests.get() ? chestColor.get() : null;
        if (be instanceof BarrelBlockEntity) return barrels.get() ? chestColor.get() : null;
        if (be instanceof ShulkerBoxBlockEntity) return shulkers.get() ? shulkerColor.get() : null;
        if (be instanceof EnderChestBlockEntity) return enderChests.get() ? enderColor.get() : null;
        if (be instanceof AbstractFurnaceBlockEntity || be instanceof HopperBlockEntity || be instanceof DispenserBlockEntity) {
            return utility.get() ? utilityColor.get() : null;
        }
        return null;
    }

    @Override
    public void onRenderOverlay(Overlay overlay) {
        for (Target target : targets) {
            overlay.box(target.box, target.color);
            if (tracers.get()) overlay.tracer(target.pos.getX() + 0.5, target.pos.getY() + 0.5, target.pos.getZ() + 0.5, target.color);
        }
    }

    @Override
    protected void onDisable() {
        targets = new ArrayList<>();
    }

    @Override
    public void onWorldLeave() {
        targets = new ArrayList<>();
    }
}
