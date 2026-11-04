package com.mytestclient.module.utility;

import com.mytestclient.gui.Theme;
import com.mytestclient.hud.HudModule;
import com.mytestclient.module.Category;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.render.Render2D;
import com.mytestclient.util.EntityKind;
import com.mytestclient.util.Notifications;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Radar: a HUD minimap that shows players, animals, hostile mobs and items around you, with
 * entity counts and an optional alert when a hostile mob gets close.
 * <p>
 * ⚠ CLIENT_SIDE: only uses entities the server already sent you (not detectable), but shows
 * entities behind walls, which some servers' rules forbid.
 */
public class RadarModule extends HudModule {
    private final NumberSetting size = add(new NumberSetting("Size", "Radar size in pixels.", 90, 40, 200, 2));
    private final NumberSetting range = add(new NumberSetting("Range", "Blocks from the centre to the edge.", 48, 8, 160, 1));
    private final BooleanSetting rotateWithPlayer = add(new BooleanSetting("Rotate", "Rotate so that up is where you look (otherwise north is up).", true));
    private final BooleanSetting players = add(new BooleanSetting("Players", "Show players (blue).", true));
    private final BooleanSetting hostiles = add(new BooleanSetting("Hostiles", "Show hostile mobs (red).", true));
    private final BooleanSetting passives = add(new BooleanSetting("Animals", "Show animals and passive mobs (green).", true));
    private final BooleanSetting items = add(new BooleanSetting("Items", "Show dropped items (yellow).", false));
    private final BooleanSetting counts = add(new BooleanSetting("Counts", "Show entity counts below the radar.", true));
    private final BooleanSetting alert = add(new BooleanSetting("Hostile Alert", "Notify when a hostile mob comes close.", false));
    private final NumberSetting alertRange = add(new NumberSetting("Alert Range", "Distance for the hostile alert.", 10, 3, 32, 1)
            .visibleWhen(alert::get));

    private long lastAlert;

    public RadarModule() {
        super("Radar", "Minimap showing animals, mobs, players and items around you.", Category.UTILITY, 100, 30, false);
        setRisk(Risk.CLIENT_SIDE, "Uses data your client already has (not detectable), but shows entities behind walls, which some servers forbid.");
    }

    @Override
    public void onTick() {
        if (!alert.get()) return;
        double r2 = alertRange.get() * alertRange.get();
        for (Entity e : mc.world.getEntities()) {
            if (EntityKind.of(e) == EntityKind.HOSTILE && mc.player.squaredDistanceTo(e) < r2) {
                long now = System.currentTimeMillis();
                if (now - lastAlert > 5000) {
                    Notifications.warn(e.getName().getString() + " nearby!");
                    lastAlert = now;
                }
                return;
            }
        }
    }

    @Override
    protected void render(DrawContext ctx, int x, int y, float tickDelta) {
        int s = size.getInt();
        int cx = x + s / 2, cy = y + s / 2;
        double scale = (s / 2.0 - 3) / range.get();

        Render2D.roundedRect(ctx, x, y, s, s, 6, 0xB0101216);
        // crosshair
        ctx.fill(cx, y + 4, cx + 1, y + s - 4, Theme.SEPARATOR);
        ctx.fill(x + 4, cy, x + s - 4, cy + 1, Theme.SEPARATOR);

        Vec3d me = mc.player.getLerpedPos(tickDelta);
        double yaw = Math.toRadians(mc.player.getYaw(tickDelta));
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // forward
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);  // right

        int nPlayers = 0, nHostile = 0, nPassive = 0, nItems = 0;
        double maxR = range.get();
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player) continue;
            EntityKind kind = EntityKind.of(e);
            int color;
            switch (kind) {
                case PLAYER -> {
                    if (!players.get()) continue;
                    color = 0xFF6C8CFF;
                }
                case HOSTILE -> {
                    if (!hostiles.get()) continue;
                    color = 0xFFEF4444;
                }
                case PASSIVE -> {
                    if (!passives.get()) continue;
                    color = 0xFF4ADE80;
                }
                case ITEM -> {
                    if (!items.get()) continue;
                    color = 0xFFFACC15;
                }
                default -> {
                    continue;
                }
            }
            Vec3d p = e.getLerpedPos(tickDelta);
            double dx = p.x - me.x, dz = p.z - me.z;
            if (dx * dx + dz * dz > maxR * maxR) continue;
            switch (kind) {
                case PLAYER -> nPlayers++;
                case HOSTILE -> nHostile++;
                case PASSIVE -> nPassive++;
                case ITEM -> nItems++;
                default -> {
                }
            }
            double sx, sy;
            if (rotateWithPlayer.get()) {
                sx = dx * rx + dz * rz;
                sy = -(dx * fx + dz * fz);
            } else {
                sx = dx;
                sy = dz;
            }
            int px = cx + (int) Math.round(sx * scale);
            int py = cy + (int) Math.round(sy * scale);
            int dot = kind == EntityKind.PLAYER ? 2 : 1;
            ctx.fill(px - dot, py - dot, px + dot + 1, py + dot + 1, color);
        }

        // player marker + facing tick
        ctx.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
        if (rotateWithPlayer.get()) {
            ctx.fill(cx, cy - 5, cx + 1, cy - 1, 0xFFFFFFFF);
            // north is world direction (0, -1): on screen that is (-rz, fz), already unit length
            double edge = s / 2.0 - 7;
            int nx = cx + (int) Math.round(-rz * edge) - 2;
            int ny = cy + (int) Math.round(fz * edge) - 4;
            ctx.drawText(mc.textRenderer, "N", nx, ny, Theme.TEXT_DIM, false);
        } else {
            double fdx = fx * 5, fdz = fz * 5;
            ctx.fill(cx + (int) Math.round(fdx), cy + (int) Math.round(fdz), cx + (int) Math.round(fdx) + 1, cy + (int) Math.round(fdz) + 1, 0xFFFFFFFF);
            ctx.drawText(mc.textRenderer, "N", cx - 2, y + 3, Theme.TEXT_DIM, false);
        }

        int h = s;
        if (counts.get()) {
            String text = "§9" + nPlayers + " §c" + nHostile + " §a" + nPassive + (items.get() ? " §e" + nItems : "");
            int tw = mc.textRenderer.getWidth(text);
            ctx.drawText(mc.textRenderer, text, x + (s - tw) / 2, y + s + 3, Theme.TEXT, true);
            h += 13;
        }
        setSize(s, h);
    }

    @Override
    protected boolean alignRight() {
        return false;
    }
}
