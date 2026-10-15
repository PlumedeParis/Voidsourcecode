package com.mytestclient.module.hud;

import com.mytestclient.hud.HudModule;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.render.Colors;
import com.mytestclient.gui.Theme;
import com.mytestclient.util.TickRate;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Info HUD: FPS, ping, speed, biome, light level, in-game day/time and real time. */
public class InfoHud extends HudModule {
    private final BooleanSetting fps = add(new BooleanSetting("FPS", "Frames per second.", true));
    private final BooleanSetting ping = add(new BooleanSetting("Ping", "Latency to the server.", true));
    private final BooleanSetting tps = add(new BooleanSetting("TPS", "Estimated server ticks per second (20 = no lag).", true));
    private final BooleanSetting speed = add(new BooleanSetting("Speed", "Horizontal speed in blocks per second.", true));
    private final BooleanSetting biome = add(new BooleanSetting("Biome", "Biome at your position.", true));
    private final BooleanSetting light = add(new BooleanSetting("Light", "Block and sky light at your feet.", false));
    private final BooleanSetting gameTime = add(new BooleanSetting("Game Time", "In-game day and time.", true));
    private final BooleanSetting realTime = add(new BooleanSetting("Real Time", "Your computer clock.", false));

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private double lastX, lastZ, currentSpeed;

    public InfoHud() {
        super("Info", "FPS, ping, speed, biome, light and time.", 0, 0);
    }

    @Override
    public void onTick() {
        double dx = mc.player.getX() - lastX, dz = mc.player.getZ() - lastZ;
        double instant = Math.sqrt(dx * dx + dz * dz) * 20;
        if (instant > 100) instant = 0; // teleport
        currentSpeed = currentSpeed * 0.6 + instant * 0.4;
        lastX = mc.player.getX();
        lastZ = mc.player.getZ();
    }

    @Override
    protected void render(DrawContext ctx, int x, int y, float tickDelta) {
        List<Line> lines = new ArrayList<>();
        if (fps.get()) {
            int f = mc.getCurrentFps();
            lines.add(new Line("FPS", Integer.toString(f), Colors.health(Math.min(1f, f / 60f))));
        }
        if (ping.get() && mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) {
                int p = entry.getLatency();
                lines.add(new Line("Ping", p + "ms", Colors.health(1f - Math.min(1f, p / 300f))));
            }
        }
        if (tps.get()) {
            double t = TickRate.get();
            double stall = TickRate.secondsSinceLast();
            String text = t < 0 ? "..." : String.format("%.1f", t) + (stall > 3 ? String.format(" (lag %.0fs)", stall) : "");
            lines.add(new Line("TPS", text, t < 0 ? Theme.TEXT_DIM : Colors.health((float) (t / 20.0))));
        }
        if (speed.get()) lines.add(new Line("Speed", String.format("%.2f b/s", currentSpeed)));
        BlockPos pos = mc.player.getBlockPos();
        if (biome.get()) {
            String name = mc.world.getBiome(pos).getKey().map(k -> k.getValue().getPath()).orElse("unknown");
            lines.add(new Line("Biome", prettify(name)));
        }
        if (light.get()) {
            lines.add(new Line("Light", "block " + mc.world.getLightLevel(LightType.BLOCK, pos) + ", sky " + mc.world.getLightLevel(LightType.SKY, pos)));
        }
        if (gameTime.get()) {
            long t = mc.world.getTimeOfDay();
            long day = t / 24000;
            long ticks = t % 24000;
            int hours = (int) ((ticks / 1000 + 6) % 24);
            int minutes = (int) (ticks % 1000 * 60 / 1000);
            lines.add(new Line("Day", day + "  " + String.format("%02d:%02d", hours, minutes)));
        }
        if (realTime.get()) lines.add(new Line("Time", LocalTime.now().format(CLOCK)));
        drawLines(ctx, x, y, lines);
    }

    private static String prettify(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
