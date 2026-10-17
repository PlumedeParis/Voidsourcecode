package com.mytestclient.module.utility;

import com.mytestclient.gui.Theme;
import com.mytestclient.hud.HudModule;
import com.mytestclient.module.Category;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.render.Colors;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Target Info: HUD panel describing what your crosshair points at — block name, id, position,
 * hardness and whether you can harvest it, or an entity's name, health and distance.
 * Only uses what is already on your screen, so no warning.
 */
public class TargetInfoModule extends HudModule {
    private final BooleanSetting showId = add(new BooleanSetting("Show ID", "Show the registry id.", true));
    private final BooleanSetting showHardness = add(new BooleanSetting("Hardness", "Show block hardness and harvestability.", true));
    private final BooleanSetting showPos = add(new BooleanSetting("Position", "Show the block position.", true));

    public TargetInfoModule() {
        super("Target Info", "Info about the block or entity under your crosshair.", Category.UTILITY, 50, 2);
    }

    @Override
    protected void render(DrawContext ctx, int x, int y, float tickDelta) {
        List<Line> lines = new ArrayList<>();
        HitResult hit = mc.crosshairTarget;
        if (hit instanceof EntityHitResult entityHit) {
            Entity e = entityHit.getEntity();
            lines.add(new Line("", e.getName().getString()));
            if (showId.get()) lines.add(new Line("ID", Registries.ENTITY_TYPE.getId(e.getType()).toString(), Theme.TEXT_DIM));
            if (e instanceof LivingEntity living) {
                float hp = living.getHealth(), max = living.getMaxHealth();
                lines.add(new Line("Health", String.format("%.1f / %.1f", hp, max), Colors.health(max > 0 ? hp / max : 0)));
            }
            lines.add(new Line("Distance", String.format("%.1fm", Math.sqrt(mc.player.squaredDistanceTo(e)))));
        } else if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = mc.world.getBlockState(pos);
            lines.add(new Line("", state.getBlock().getName().getString()));
            if (showId.get()) lines.add(new Line("ID", Registries.BLOCK.getId(state.getBlock()).toString(), Theme.TEXT_DIM));
            if (showPos.get()) lines.add(new Line("Pos", pos.getX() + " " + pos.getY() + " " + pos.getZ()));
            if (showHardness.get()) {
                float hardness = state.getHardness(mc.world, pos);
                lines.add(new Line("Hardness", hardness < 0 ? "unbreakable" : String.format("%.1f", hardness)));
                boolean canHarvest = mc.player.canHarvest(state);
                lines.add(new Line("Harvest", canHarvest ? "yes" : "wrong tool", canHarvest ? Theme.GOOD : Theme.BAD));
            }
        } else {
            setSize(1, 1);
            return;
        }
        drawLines(ctx, x, y, lines);
    }
}
