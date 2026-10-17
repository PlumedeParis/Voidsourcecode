package com.mytestclient.module.world;

import com.mytestclient.MyTestClient;
import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.render.Overlay;
import com.mytestclient.util.ChatUtil;
import com.mytestclient.util.Notifications;
import com.mytestclient.waypoint.Waypoint;
import com.mytestclient.waypoint.WaypointManager;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Event Locator: finds where things happen in your world using information the server already
 * sends to every player — no OP needed.
 * <ul>
 *     <li><b>Global events</b> (whole dimension): wither spawned, ender dragon killed, end portal
 *     opened. Vanilla sends their exact coordinates to everyone; the game only uses them to aim
 *     the sound, this module shows them.</li>
 *     <li><b>Loud sounds</b>: lightning strikes (heard dimension-wide), explosions, raid horns,
 *     and other loud sounds with their real position.</li>
 * </ul>
 * Each event gets an on-screen marker for a while and can create a waypoint. {@code \events}
 * lists recent ones. Uses only packets every vanilla client receives, so no warning.
 */
public class EventLocatorModule extends Module {
    private final BooleanSetting globalEvents = add(new BooleanSetting("Global Events", "Wither spawn, dragon death, end portal opened.", true));
    private final BooleanSetting lightning = add(new BooleanSetting("Lightning", "Lightning strikes (can be very far away).", true));
    private final BooleanSetting explosions = add(new BooleanSetting("Explosions", "TNT, creepers, crystals, beds...", true));
    private final BooleanSetting raids = add(new BooleanSetting("Raids", "Raid horns.", true));
    private final NumberSetting minDistance = add(new NumberSetting("Min Distance", "Ignore sounds closer than this (you can see them anyway).", 24, 0, 200, 4));
    private final NumberSetting markerSeconds = add(new NumberSetting("Marker Time", "Seconds a marker stays on screen.", 60, 5, 600, 5));
    private final BooleanSetting waypoints = add(new BooleanSetting("Auto Waypoint", "Create a waypoint for global events.", true));
    private final BooleanSetting notify = add(new BooleanSetting("Notify", "Show a notification for each event.", true));

    public record Event(String name, double x, double y, double z, String dim, long time) {
    }

    private final List<Event> events = new ArrayList<>();

    public EventLocatorModule() {
        super("Event Locator", "Locates world events and loud sounds (wither, dragon, portals, lightning, explosions, raids) with coordinates.", Category.WORLD);
    }

    /** From the network mixin: a world event packet. */
    public void onWorldEvent(int id, BlockPos pos, boolean global) {
        if (!global || !globalEvents.get()) return;
        String name = switch (id) {
            case 1023 -> "Wither spawned";
            case 1028 -> "Ender dragon killed";
            case 1038 -> "End portal opened";
            default -> "World event " + id;
        };
        record(name, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, true);
    }

    /** From the network mixin: a positioned sound packet. */
    public void onSound(String soundId, double x, double y, double z) {
        String name = null;
        if (lightning.get() && soundId.contains("lightning_bolt.thunder")) name = "Lightning";
        else if (explosions.get() && (soundId.contains("generic.explode") || soundId.contains("dragon_fireball.explode"))) name = "Explosion";
        else if (raids.get() && soundId.contains("raid.horn")) name = "Raid horn";
        if (name == null || mc.player == null) return;
        if (mc.player.squaredDistanceTo(x, y, z) < minDistance.get() * minDistance.get()) return;
        // merge duplicates (e.g. many explosions at the same place)
        for (Event e : events) {
            if (e.name.equals(name) && System.currentTimeMillis() - e.time < 3000 && Math.abs(e.x - x) < 8 && Math.abs(e.z - z) < 8) return;
        }
        record(name, x, y, z, false);
    }

    private void record(String name, double x, double y, double z, boolean important) {
        Event e = new Event(name, x, y, z, WaypointManager.currentDimension(), System.currentTimeMillis());
        events.add(e);
        while (events.size() > 100) events.remove(0);
        double dist = mc.player == null ? 0 : Math.sqrt(mc.player.squaredDistanceTo(x, y, z));
        if (notify.get()) Notifications.warn(String.format("%s at %d %d %d (%.0fm)", name, (int) x, (int) y, (int) z, dist));
        if (important) {
            ChatUtil.info(String.format("%s at %d %d %d", name, (int) x, (int) y, (int) z));
            if (waypoints.get()) {
                MyTestClient.get().waypoints().add(new Waypoint(name, (int) x, (int) y, (int) z,
                        WaypointManager.currentDimension(), WaypointManager.currentWorldKey(), 0xFFA855F7));
            }
        }
    }

    public List<Event> recent() {
        return events;
    }

    /** {@code \events} */
    public void printEvents() {
        if (events.isEmpty()) {
            ChatUtil.info("No events recorded yet (Event Locator must be enabled).");
            return;
        }
        ChatUtil.success("Recent events:");
        for (int i = events.size() - 1; i >= Math.max(0, events.size() - 15); i--) {
            Event e = events.get(i);
            long s = (System.currentTimeMillis() - e.time) / 1000;
            ChatUtil.info(String.format(" %s @ %d %d %d (%s), %ds ago", e.name, (int) e.x, (int) e.y, (int) e.z, e.dim, s));
        }
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        long now = System.currentTimeMillis();
        long keep = markerSeconds.getInt() * 1000L;
        String dim = WaypointManager.currentDimension();
        for (Event e : events) {
            if (now - e.time > keep || !e.dim.equals(dim)) continue;
            double dist = Math.sqrt(mc.player.squaredDistanceTo(e.x, e.y, e.z));
            o.marker(e.x, e.y + 1, e.z, 0xFFA855F7, String.format("%.0fm", dist), e.name + String.format(" (%.0fm)", dist), 0.7f);
        }
    }

    @Override
    public void onWorldLeave() {
        events.clear();
    }
}
