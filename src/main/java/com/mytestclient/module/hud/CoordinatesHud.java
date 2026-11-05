package com.mytestclient.module.hud;

import com.mytestclient.gui.Theme;
import com.mytestclient.hud.HudModule;
import com.mytestclient.module.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** Coordinates HUD: XYZ, the matching Nether/Overworld coordinates, facing and chunk. */
public class CoordinatesHud extends HudModule {
    private final BooleanSetting decimals = add(new BooleanSetting("Decimals", "Show one decimal place.", false));
    private final BooleanSetting otherDimension = add(new BooleanSetting("Nether/Overworld", "Show the converted coordinates of the other dimension.", true));
    private final BooleanSetting facing = add(new BooleanSetting("Facing", "Show the direction you are facing.", true));
    private final BooleanSetting chunk = add(new BooleanSetting("Chunk", "Show chunk coordinates and position within the chunk.", false));

    public CoordinatesHud() {
        super("Coordinates", "Shows your coordinates, nether conversion, facing and chunk.", 0, 100);
    }

    @Override
    protected void render(DrawContext ctx, int x, int y, float tickDelta) {
        List<Line> lines = new ArrayList<>();
        Vec3d p = mc.player.getLerpedPos(tickDelta);
        lines.add(new Line("XYZ", format(p.x, p.y, p.z)));
        if (otherDimension.get()) {
            if (mc.world.getRegistryKey() == World.NETHER) lines.add(new Line("Overworld", format(p.x * 8, p.y, p.z * 8), Theme.TEXT_DIM));
            else if (mc.world.getRegistryKey() == World.OVERWORLD) lines.add(new Line("Nether", format(p.x / 8, p.y, p.z / 8), Theme.TEXT_DIM));
        }
        if (facing.get()) {
            String dir = mc.player.getHorizontalFacing().asString();
            String axis = switch (mc.player.getHorizontalFacing()) {
                case NORTH -> "-Z";
                case SOUTH -> "+Z";
                case WEST -> "-X";
                case EAST -> "+X";
                default -> "";
            };
            lines.add(new Line("Facing", Character.toUpperCase(dir.charAt(0)) + dir.substring(1) + " (" + axis + ")"));
        }
        if (chunk.get()) {
            int bx = mc.player.getBlockX(), bz = mc.player.getBlockZ();
            lines.add(new Line("Chunk", (bx >> 4) + " " + (bz >> 4) + "  in " + (bx & 15) + " " + (bz & 15), Theme.TEXT_DIM));
        }
        drawLines(ctx, x, y, lines);
    }

    private String format(double x, double y, double z) {
        return decimals.get()
                ? String.format("%.1f %.1f %.1f", x, y, z)
                : String.format("%d %d %d", (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }
}
