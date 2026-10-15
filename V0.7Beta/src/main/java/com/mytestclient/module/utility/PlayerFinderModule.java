package com.mytestclient.module.utility;

import com.mytestclient.MyTestClient;
import com.mytestclient.finder.Activity;
import com.mytestclient.finder.BaseScanner;
import com.mytestclient.finder.WebMap;
import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Modules;
import com.mytestclient.module.SafeMode;
import com.mytestclient.module.movement.ElytraFlyModule;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.ModeSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.module.setting.StringSetting;
import com.mytestclient.render.Overlay;
import com.mytestclient.util.ChatUtil;
import com.mytestclient.util.ElytraAutopilot;
import com.mytestclient.util.Navigation;
import com.mytestclient.util.Notifications;
import com.mytestclient.waypoint.Waypoint;
import com.mytestclient.waypoint.WaypointManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Player Finder: finds players and where they live, anywhere in the world, without OP.
 * <p>
 * A Minecraft server only sends you other players' positions when they are near you, so there is
 * no single trick that reveals everyone. This combines every source a normal client can get:
 * <ol>
 *     <li><b>Web map</b>: if the server runs Dynmap / BlueMap / squaremap / Pl3xMap (very common), the
 *     map website shows every player live; this reads it. Instant, whole world.</li>
 *     <li><b>Activity radar</b>: block changes reach you from your whole render distance, much further
 *     than player positions. Mining, building and breaking far away shows up as hotspots.</li>
 *     <li><b>Base finder</b>: every chunk you load is scanned for player-made blocks (shulkers, ender
 *     chests, beacons, concrete, signs, lots of chests...).</li>
 *     <li><b>Patrol</b>: the elytra autopilot flies a spiral around you (or spawn) so the three
 *     scanners above cover a huge area while you are AFK, and every player met is recorded.</li>
 * </ol>
 * Everything found goes to {@code \finder} (report), {@code \players}, world markers and
 * optionally waypoints; {@code \goto <player>} uses the last known position.
 */
public class PlayerFinderModule extends Module {
    private final BooleanSetting webMap = add(new BooleanSetting("Web Map", "Read player positions from the server's Dynmap/BlueMap/squaremap/Pl3xMap website.", true));
    private final StringSetting mapUrl = add(new StringSetting("Map URL", "Address of the server's map website if it isn't found automatically (e.g. map.server.net).", "", 120)
            .visibleWhen(webMap::get));
    private final NumberSetting refresh = add(new NumberSetting("Refresh", "Seconds between web map updates.", 10, 2, 120, 1).visibleWhen(webMap::get));
    private final BooleanSetting activity = add(new BooleanSetting("Activity Radar", "Detect far-away mining/building from block changes.", true));
    private final BooleanSetting bases = add(new BooleanSetting("Base Finder", "Scan loaded chunks for player-made bases and stashes.", true));
    private final NumberSetting baseThreshold = add(new NumberSetting("Base Score", "Score a chunk needs to count as a base (higher = fewer false alarms).", 20, 5, 100, 1)
            .visibleWhen(bases::get));
    private final BooleanSetting patrol = add(new BooleanSetting("Patrol", "Fly a spiral with the elytra autopilot to scan a large area (needs an elytra).", false));
    private final NumberSetting patrolRadius = add(new NumberSetting("Patrol Radius", "How far from the centre the spiral goes.", 3000, 200, 30000, 100).visibleWhen(patrol::get));
    private final NumberSetting patrolSpacing = add(new NumberSetting("Lane Spacing", "Blocks between two passes (0 = from your render distance).", 0, 0, 1024, 16).visibleWhen(patrol::get));
    private final ModeSetting patrolCenter = add(new ModeSetting("Patrol Centre", "Spiral around where you are, or around 0 0.", "Here", "Here", "Spawn (0 0)").visibleWhen(patrol::get));
    private final BooleanSetting markers = add(new BooleanSetting("Markers", "Show players, hotspots and bases in the world.", true));
    private final BooleanSetting waypoints = add(new BooleanSetting("Waypoints For Bases", "Add a waypoint for every base found.", false));
    private final BooleanSetting alerts = add(new BooleanSetting("Alerts", "Notify on new bases and new activity hotspots.", true));

    private record MapPlayer(WebMap.PlayerPos pos, long time) {
    }

    private final Map<String, MapPlayer> mapPlayers = new HashMap<>();
    private WebMap.Source source;
    private String sourceServer;
    private boolean discovering, fetching;
    private int refreshTimer;

    private List<Vec3d> patrolPoints = List.of();
    private int patrolLeg;
    private boolean patrolling;
    private final java.util.Set<Long> alertedHotspots = new java.util.HashSet<>();

    public PlayerFinderModule() {
        super("Player Finder", "Finds players and bases anywhere: server web map, far activity, base scanner, elytra patrol.", Category.UTILITY);
    }

    @Override
    protected void onEnable() {
        if (mc.world == null) return;
        refreshTimer = 0;
        ChatUtil.info("Player Finder on: " + sourcesText() + ". \\finder for the report.");
        if (patrol.get()) startPatrol(patrolRadius.getInt());
    }

    @Override
    protected void onDisable() {
        stopPatrol("Player Finder turned off");
    }

    @Override
    public void onWorldLeave() {
        stopPatrol("left the world");
        mapPlayers.clear();
    }

    private String sourcesText() {
        List<String> s = new ArrayList<>();
        if (webMap.get()) s.add("web map");
        if (activity.get()) s.add("activity radar");
        if (bases.get()) s.add("base finder");
        if (patrol.get()) s.add("patrol");
        return String.join(", ", s);
    }

    // ------------------------------------------------------------------ hooks

    public void onBlockChange(BlockPos pos, BlockState old, BlockState now) {
        if (activity.get()) Activity.onBlockChange(pos, old, now, 8);
    }

    public void onChunkLoad(WorldChunk chunk) {
        if (!bases.get() || mc.world == null) return;
        String dim = WaypointManager.currentDimension();
        BaseScanner.Base base = BaseScanner.scan(chunk, dim, baseThreshold.getInt());
        if (base == null) return;
        if (alerts.get()) Notifications.warn(String.format("Base found at %d %d %d (%s)", base.center().getX(), base.center().getY(), base.center().getZ(), base.summary()));
        if (waypoints.get()) {
            MyTestClient.get().waypoints().add(new Waypoint("Base " + base.score(), base.center().getX(), base.center().getY(), base.center().getZ(),
                    dim, WaypointManager.currentWorldKey(), 0xFFF472B6));
        }
    }

    @Override
    public void onTick() {
        if (webMap.get() && !mc.isInSingleplayer()) tickWebMap();
        if (alerts.get() && activity.get()) {
            long now = System.currentTimeMillis();
            for (Activity.Hotspot h : Activity.hotspots()) {
                long key = ((long) h.chunkX << 32) ^ (h.chunkZ & 0xFFFFFFFFL);
                if (h.total() >= 6 && now - h.lastTime < 5000 && alertedHotspots.add(key)) {
                    Notifications.warn(String.format("Activity: %s near %d %d %d", h.describe(), h.last.getX(), h.last.getY(), h.last.getZ()));
                }
            }
        }
        if (patrolling) tickPatrol();
    }

    // ------------------------------------------------------------------ web map

    private void tickWebMap() {
        ServerInfo info = mc.getCurrentServerEntry();
        if (info == null) return;
        if (!info.address.equals(sourceServer)) {
            sourceServer = info.address;
            source = null;
            discovering = false;
        }
        if (source == null) {
            if (discovering) return;
            discovering = true;
            String host = ServerAddress.parse(info.address).getAddress();
            List<String> candidates = WebMap.candidates(host, mapUrl.get());
            WebMap.discover(candidates).whenComplete((s, e) -> mc.execute(() -> {
                if (s == null) {
                    ChatUtil.info("Player Finder: no public web map found (tried " + candidates.size()
                            + " addresses). If the server has one, put its address in Map URL.");
                    // try again later in case the setting changes
                    discovering = true;
                    new Thread(() -> {
                        try {
                            Thread.sleep(120_000);
                        } catch (InterruptedException ignored) {
                        }
                        discovering = false;
                    }, "VoidClient-MapRetry").start();
                    return;
                }
                source = s;
                ChatUtil.success("Player Finder: reading " + s + " - every player on the map, live.");
            }));
            return;
        }
        if (fetching || --refreshTimer > 0) return;
        refreshTimer = refresh.getInt() * 20;
        fetching = true;
        WebMap.players(source).whenComplete((list, e) -> mc.execute(() -> {
            fetching = false;
            if (list == null) return;
            long now = System.currentTimeMillis();
            PlayerTrackerModule tracker = Modules.get(PlayerTrackerModule.class);
            for (WebMap.PlayerPos p : list) {
                mapPlayers.put(p.name(), new MapPlayer(p, now));
                if (tracker != null) tracker.report(p.name(), p.x(), p.y(), p.z(), p.dimension(), "web map");
            }
        }));
    }

    // ------------------------------------------------------------------ patrol

    /** Starts the elytra patrol. */
    public void startPatrol(int radius) {
        if (mc.player == null) return;
        if (!mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
            ChatUtil.error("Patrol needs an elytra on your chest.");
            return;
        }
        String blocked = SafeMode.blockReason(Modules.get(ElytraFlyModule.class));
        if (blocked != null) {
            ChatUtil.error("Patrol uses Elytra Fly, which is " + blocked);
            return;
        }
        int spacing = patrolSpacing.getInt() > 0 ? patrolSpacing.getInt() : Math.max(64, mc.options.getClampedViewDistance() * 16 * 2 - 48);
        double cx = patrolCenter.is("Here") ? mc.player.getX() : 0, cz = patrolCenter.is("Here") ? mc.player.getZ() : 0;
        if (WaypointManager.currentDimension().equals("minecraft:the_nether") && !patrolCenter.is("Here")) {
            cx = 0;
            cz = 0;
        }
        patrolPoints = spiral(cx, cz, radius, spacing, mc.player.getY());
        patrolLeg = 0;
        patrolling = true;
        Navigation.set(patrolPoints.get(0), "Patrol 1/" + patrolPoints.size());
        ElytraAutopilot.setPatrol(true);
        if (!ElytraAutopilot.isActive()) {
            String error = ElytraAutopilot.start();
            if (error != null) {
                patrolling = false;
                ElytraAutopilot.setPatrol(false);
                ChatUtil.error("Patrol: " + error);
                return;
            }
        }
        double km = 0;
        for (int i = 1; i < patrolPoints.size(); i++) km += patrolPoints.get(i).distanceTo(patrolPoints.get(i - 1)) / 1000;
        ChatUtil.success(String.format("Patrol started: spiral of %d blocks around %.0f %.0f, lanes every %d blocks (%.1f km). \\finder stop to end.",
                radius, cx, cz, spacing, km));
    }

    public void stopPatrol(String reason) {
        if (!patrolling) return;
        patrolling = false;
        ElytraAutopilot.stop(reason);
    }

    private void tickPatrol() {
        if (!ElytraAutopilot.isActive()) {
            patrolling = false;
            ChatUtil.info("Patrol stopped (autopilot stopped). \\finder for what was found.");
            return;
        }
        Vec3d target = patrolPoints.get(patrolLeg);
        double d = Math.hypot(target.x - mc.player.getX(), target.z - mc.player.getZ());
        if (d < 48) {
            patrolLeg++;
            if (patrolLeg >= patrolPoints.size()) {
                stopPatrol("patrol finished");
                report();
                return;
            }
            Navigation.set(patrolPoints.get(patrolLeg), "Patrol " + (patrolLeg + 1) + "/" + patrolPoints.size());
        }
    }

    /** Square spiral corners: E, S, W, N with growing legs. */
    private static List<Vec3d> spiral(double cx, double cz, int radius, int spacing, double y) {
        List<Vec3d> out = new ArrayList<>();
        double x = cx, z = cz;
        int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        int len = 1, d = 0;
        while (len * spacing <= radius * 2 + spacing) {
            for (int rep = 0; rep < 2; rep++) {
                x += dirs[d][0] * len * spacing;
                z += dirs[d][1] * len * spacing;
                out.add(new Vec3d(x, y, z));
                d = (d + 1) % 4;
            }
            len++;
        }
        return out;
    }

    // ------------------------------------------------------------------ report + markers

    /** {@code \finder}: everything found so far. */
    public void report() {
        PlayerTrackerModule tracker = Modules.get(PlayerTrackerModule.class);
        ChatUtil.success("=== Player Finder (" + sourcesText() + ") ===");
        if (source != null) ChatUtil.info("Web map: " + source + " (" + mapPlayers.size() + " players on it)");
        if (tracker != null) tracker.printPlayers();

        List<Activity.Hotspot> hs = Activity.hotspots();
        hs.sort(Comparator.comparingLong((Activity.Hotspot h) -> h.lastTime).reversed());
        ChatUtil.success("Activity hotspots (" + hs.size() + "):");
        for (int i = 0; i < Math.min(10, hs.size()); i++) {
            Activity.Hotspot h = hs.get(i);
            ChatUtil.info(String.format(" %s near %d %d %d (%s) - %s ago", h.describe(), h.last.getX(), h.last.getY(), h.last.getZ(),
                    h.dim.replace("minecraft:", ""), ago(h.lastTime)));
        }
        List<BaseScanner.Base> bs = BaseScanner.bases();
        bs.sort(Comparator.comparingInt(BaseScanner.Base::score).reversed());
        ChatUtil.success("Bases (" + bs.size() + "):");
        for (int i = 0; i < Math.min(10, bs.size()); i++) {
            BaseScanner.Base b = bs.get(i);
            ChatUtil.info(String.format(" score %d at %d %d %d (%s): %s", b.score(), b.center().getX(), b.center().getY(), b.center().getZ(),
                    b.dim().replace("minecraft:", ""), b.summary()));
        }
        if (patrolling) ChatUtil.info("Patrol: leg " + (patrolLeg + 1) + "/" + patrolPoints.size());
    }

    public void clearData() {
        Activity.clear();
        BaseScanner.clear();
        mapPlayers.clear();
        alertedHotspots.clear();
    }

    private static String ago(long time) {
        long s = (System.currentTimeMillis() - time) / 1000;
        return s < 60 ? s + "s" : s < 3600 ? s / 60 + "m" : s / 3600 + "h";
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        if (!markers.get()) return;
        String dim = WaypointManager.currentDimension();
        long now = System.currentTimeMillis();
        for (MapPlayer mp : mapPlayers.values()) {
            WebMap.PlayerPos p = mp.pos();
            if (!p.dimension().equals(dim) || now - mp.time() > 120_000) continue;
            if (mc.player != null && p.name().equals(mc.player.getName().getString())) continue;
            o.marker(p.x(), p.y() + 2, p.z(), 0xFF38BDF8, p.name(), p.name() + " (web map)", 0.8f);
        }
        for (Activity.Hotspot h : Activity.hotspots()) {
            if (!h.dim.equals(dim) || now - h.lastTime > 600_000 || h.total() < 3) continue;
            String icon = switch (h.main()) {
                case MINING -> "⛏";
                case BUILDING -> "▲";
                case BREAKING -> "✖";
                case REDSTONE -> "⚡";
            };
            o.marker(h.last.getX() + 0.5, h.last.getY() + 1, h.last.getZ() + 0.5, 0xFFFACC15, icon + h.total(), "Activity: " + h.describe(), 0.7f);
        }
        for (BaseScanner.Base b : BaseScanner.bases()) {
            if (!b.dim().equals(dim)) continue;
            o.marker(b.center().getX() + 0.5, b.center().getY() + 1, b.center().getZ() + 0.5, 0xFFF472B6, "⌂" + b.score(), "Base: " + b.summary(), 0.7f);
        }
    }
}
