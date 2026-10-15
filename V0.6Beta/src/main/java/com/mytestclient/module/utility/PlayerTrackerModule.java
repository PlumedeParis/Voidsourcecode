package com.mytestclient.module.utility;

import com.mytestclient.gui.Theme;
import com.mytestclient.hud.HudModule;
import com.mytestclient.module.Category;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.render.Overlay;
import com.mytestclient.util.ChatUtil;
import com.mytestclient.util.Friends;
import com.mytestclient.util.Notifications;
import com.mytestclient.waypoint.WaypointManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Player Tracker: shows how many players are online, where the players near you are (distance,
 * direction, coordinates), and remembers the <b>last known position</b> of everyone who came
 * near you, with alerts when someone enters or leaves your range. {@code \players} prints it all.
 * <p>
 * Honest limit: without OP no client can know the exact position of players the server does not
 * send (outside its entity tracking range, roughly your render distance). This tracks everything
 * the server does send and keeps it after they leave. Combine with Event Locator for events.
 * <p>
 * ⚠ CLIENT_SIDE: uses only data sent to your client (not detectable), but tracking other players
 * may be against a server's rules.
 */
public class PlayerTrackerModule extends HudModule {
    private final BooleanSetting alerts = add(new BooleanSetting("Alerts", "Notify when a player enters or leaves your range.", true));
    private final BooleanSetting showCoords = add(new BooleanSetting("Coordinates", "Show coordinates in the list.", true));
    private final BooleanSetting lastSeenMarkers = add(new BooleanSetting("Last Seen Markers", "Mark where out-of-range players were last seen.", true));
    private final NumberSetting maxRows = add(new NumberSetting("Max Rows", "Maximum players listed on the HUD.", 8, 1, 30, 1));

    private record Seen(String name, double x, double y, double z, String dim, String world, long time) {
    }

    private final Map<String, Seen> lastSeen = new HashMap<>();
    private Set<String> inRange = new HashSet<>();

    public PlayerTrackerModule() {
        super("Player Tracker", "Online count, nearby players with direction, and last known positions of everyone you met.", Category.UTILITY, 100, 70);
        setRisk(Risk.CLIENT_SIDE, "Only players the server sends you (≈ render distance) can be located; their last positions are remembered. Tracking may be against server rules.");
    }

    @Override
    public void onTick() {
        Set<String> now = new HashSet<>();
        String dim = WaypointManager.currentDimension(), world = WaypointManager.currentWorldKey();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            String name = p.getName().getString();
            now.add(name);
            lastSeen.put(name, new Seen(name, p.getX(), p.getY(), p.getZ(), dim, world, System.currentTimeMillis()));
        }
        if (alerts.get()) {
            for (String n : now) if (!inRange.contains(n)) Notifications.warn(n + " entered your range");
            for (String n : inRange) if (!now.contains(n)) Notifications.info(n + " left your range");
        }
        inRange = now;
    }

    @Override
    public void onWorldLeave() {
        inRange = new HashSet<>();
    }

    private int online() {
        return mc.getNetworkHandler() == null ? 0 : mc.getNetworkHandler().getPlayerList().size();
    }

    @Override
    protected void render(DrawContext ctx, int x, int y, float tickDelta) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line("Players", online() + " online, " + inRange.size() + " near"));
        List<AbstractClientPlayerEntity> near = new ArrayList<>();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) if (p != mc.player) near.add(p);
        near.sort(Comparator.comparingDouble(p -> mc.player.squaredDistanceTo(p)));
        int rows = 0;
        for (AbstractClientPlayerEntity p : near) {
            if (rows++ >= maxRows.getInt()) break;
            String name = p.getName().getString();
            double dist = Math.sqrt(mc.player.squaredDistanceTo(p));
            String text = String.format("%s %.0fm", arrow(p.getX(), p.getZ()), dist)
                    + (showCoords.get() ? String.format("  %d %d %d", (int) p.getX(), (int) p.getY(), (int) p.getZ()) : "");
            lines.add(new Line(name, text, Friends.isFriend(name) ? Theme.GOOD : Theme.TEXT));
        }
        // recently seen, now out of range
        String world = WaypointManager.currentWorldKey();
        List<Seen> gone = new ArrayList<>();
        for (Seen s : lastSeen.values()) if (!inRange.contains(s.name) && s.world.equals(world)) gone.add(s);
        gone.sort(Comparator.comparingLong(Seen::time).reversed());
        for (Seen s : gone) {
            if (rows++ >= maxRows.getInt()) break;
            lines.add(new Line(s.name, String.format("last %d %d %d, %s ago", (int) s.x, (int) s.y, (int) s.z, ago(s.time)), Theme.TEXT_DIM));
        }
        drawLines(ctx, x, y, lines);
    }

    /** Arrow pointing towards a position relative to where you look. */
    private String arrow(double x, double z) {
        float yaw = (float) Math.toDegrees(Math.atan2(z - mc.player.getZ(), x - mc.player.getX())) - 90f;
        float diff = MathHelper.wrapDegrees(yaw - mc.player.getYaw());
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        int idx = Math.floorMod(Math.round(diff / 45f), 8);
        return arrows[idx];
    }

    private static String ago(long time) {
        long s = (System.currentTimeMillis() - time) / 1000;
        if (s < 60) return s + "s";
        if (s < 3600) return s / 60 + "m";
        return s / 3600 + "h";
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        if (!lastSeenMarkers.get()) return;
        String dim = WaypointManager.currentDimension(), world = WaypointManager.currentWorldKey();
        for (Seen s : lastSeen.values()) {
            if (inRange.contains(s.name) || !s.dim.equals(dim) || !s.world.equals(world)) continue;
            o.marker(s.x, s.y + 1, s.z, 0xFFFB923C, s.name, s.name + " (last seen " + ago(s.time) + " ago)", 0.7f);
        }
    }

    /** {@code \players}: every online player, with position if known. */
    public void printPlayers() {
        if (mc.getNetworkHandler() == null) return;
        ChatUtil.success(online() + " players online:");
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            String name = entry.getProfile().name();
            Seen s = lastSeen.get(name);
            String where;
            if (inRange.contains(name)) where = String.format("%d %d %d (near you)", (int) s.x, (int) s.y, (int) s.z);
            else if (s != null) where = String.format("last seen %d %d %d in %s, %s ago", (int) s.x, (int) s.y, (int) s.z, s.dim, ago(s.time));
            else where = "position unknown (never in your range)";
            ChatUtil.info(" " + name + (Friends.isFriend(name) ? " ★" : "") + ": " + where);
        }
    }
}
