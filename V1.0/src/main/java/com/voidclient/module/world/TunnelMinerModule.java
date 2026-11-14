package com.voidclient.module.world;

import com.voidclient.gui.Theme;
import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.Risk;
import com.voidclient.module.setting.BooleanSetting;
import com.voidclient.module.setting.ModeSetting;
import com.voidclient.module.setting.NumberSetting;
import com.voidclient.render.Overlay;
import com.voidclient.util.ChatUtil;
import com.voidclient.util.Digger;
import com.voidclient.util.Lane;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Tunnel Miner: mines a whole wall in front of you, 5×5 by default, any size you choose, and
 * keeps going as you walk: a tunnel for a highway or a strip mine.
 * <p>
 * The direction (8 ways) is locked when you turn it on, so you can look around freely. Blocks
 * that your tool breaks in one hit (netherrack with an Efficiency V pickaxe, or anything with
 * Haste II) go many per tick: the wall vanishes instantly. Harder blocks are mined one by one
 * at normal speed, because the server times those. ⚠ DETECTABLE.
 */
public class TunnelMinerModule extends Module {
    private final NumberSetting width = add(new NumberSetting("Width", "Tunnel width in blocks.", 5, 1, 9, 1));
    private final NumberSetting height = add(new NumberSetting("Height", "Tunnel height in blocks.", 5, 1, 9, 1));
    private final NumberSetting below = add(new NumberSetting("Below Feet", "Rows to mine below your feet level (0 = the floor you stand on stays).", 0, 0, 4, 1));
    private final NumberSetting depth = add(new NumberSetting("Depth", "How many layers ahead of you to mine.", 4, 1, 5, 1));
    private final NumberSetting perTick = add(new NumberSetting("Blocks Per Tick", "Instant-break blocks mined per tick.", 12, 1, 30, 1));
    private final ModeSetting direction = add(new ModeSetting("Direction", "Locked: the way you faced when enabling. Follow: where you look now.", "Locked", "Locked", "Follow"));
    private final BooleanSetting diagonals = add(new BooleanSetting("Diagonals", "Allow diagonal tunnels (for diagonal highways).", true));
    private final BooleanSetting autoTool = add(new BooleanSetting("Auto Tool", "Switch to the fastest tool for each block.", true));
    private final BooleanSetting autoWalk = add(new BooleanSetting("Auto Walk", "Walk forward by itself when the wall in front is gone.", false));
    private final BooleanSetting liquidStop = add(new BooleanSetting("Stop At Liquids", "Turn off when lava or water appears in the tunnel.", true));
    private final BooleanSetting outline = add(new BooleanSetting("Outline", "Show the outline of the tunnel being mined.", true));

    private Lane lane;
    private int baseY;
    private boolean walking;

    public TunnelMinerModule() {
        super("Tunnel Miner", "Mines a 5×5 wall (any size) in front of you instantly and tunnels on as you walk.", Category.WORLD);
        setRisk(Risk.DETECTABLE, "Breaks many blocks per tick without looking at them (nuker).");
    }

    @Override
    public boolean persistsState() {
        return false;
    }

    @Override
    protected void onEnable() {
        if (mc.player == null) return;
        lock();
        ChatUtil.info("Tunnel Miner: " + width.getInt() + "×" + height.getInt() + " heading " + lane.name() + ".");
    }

    private void lock() {
        lane = Lane.fromYaw(mc.player.getYaw(), mc.player.getBlockPos(), diagonals.get());
        baseY = mc.player.getBlockY();
    }

    @Override
    protected void onDisable() {
        Digger.stop();
        if (walking) KeyBinding.updatePressedStates();
        walking = false;
        lane = null;
    }

    private List<BlockPos> targets() {
        List<BlockPos> out = new ArrayList<>();
        int half = lane.half(width.getInt());
        int px = mc.player.getBlockX(), pz = mc.player.getBlockZ();
        int here = lane.along(px, pz);
        int reach = lane.steps(depth.getInt());
        int y0 = baseY - below.getInt(), y1 = y0 + height.getInt() - 1;
        int r = depth.getInt() + width.getInt() + 2;
        for (int x = px - r; x <= px + r; x++) {
            for (int z = pz - r; z <= pz + r; z++) {
                int a = lane.along(x, z) - here;
                if (a < 0 || a > reach || Math.abs(lane.lateral(x, z)) > half) continue;
                for (int y = y0; y <= y1; y++) out.add(new BlockPos(x, y, z));
            }
        }
        return out;
    }

    @Override
    public void onPreTick() {
        if (walking) mc.options.forwardKey.setPressed(true);
    }

    @Override
    public void onTick() {
        if (lane == null || direction.is("Follow")) lock();
        else if (mc.player.isOnGround() && Math.abs(mc.player.getBlockY() - baseY) <= 1) baseY = mc.player.getBlockY();
        List<BlockPos> targets = targets();
        if (liquidStop.get()) {
            for (BlockPos p : targets) {
                if (!mc.world.getFluidState(p).isEmpty() && mc.player.getEyePos().squaredDistanceTo(p.toCenterPos()) < 36) {
                    ChatUtil.error("Tunnel Miner stopped: " + (mc.world.getFluidState(p).isIn(net.minecraft.registry.tag.FluidTags.LAVA) ? "lava" : "water") + " ahead at " + p.toShortString() + ".");
                    setEnabled(false);
                    return;
                }
            }
        }
        boolean busy = Digger.work(targets, perTick.getInt(), autoTool.get(), 5.2);
        boolean wasWalking = walking;
        walking = autoWalk.get() && !busy && mc.currentScreen == null;
        if (walking) mc.player.setYaw(lane.yaw());
        if (wasWalking && !walking) KeyBinding.updatePressedStates();
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        if (!outline.get() || lane == null) return;
        int n = 0;
        for (BlockPos p : targets()) {
            if (!Digger.breakable(p)) continue;
            if (n++ > 80) break;
            o.box(new net.minecraft.util.math.Box(p), com.voidclient.render.Colors.withAlpha(Theme.accent(), 0x90));
        }
    }
}
