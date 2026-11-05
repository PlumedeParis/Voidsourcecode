package com.mytestclient.module.utility;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.render.Overlay;
import com.mytestclient.util.ChatUtil;
import com.mytestclient.util.Friends;
import com.mytestclient.util.Notifications;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tail Alarm: warns you when someone is following you, the classic way bases get found on
 * anarchy servers.
 * <p>
 * Every player you can see gets a "tail" record holding the point where <i>you</i> were when
 * they appeared. As long as they stay in your render distance (short gaps are forgiven), the
 * record keeps measuring how far you have moved from that point. Render distance is only a
 * few hundred blocks, so if you are 1,000+ blocks away from where you first saw someone and they
 * are still around, they are travelling with you. That fires an alarm, repeated every time the
 * distance grows by the same amount again. Standing around players at spawn never triggers it,
 * because it measures how far you got, not how long they were near.
 * <p>
 * Purely read-only: nothing is sent to the server.
 */
public class TailAlarmModule extends Module {
    private final NumberSetting distance = add(new NumberSetting("Distance", "Alarm when someone stays in render distance while you travel this far (blocks).", 1000, 200, 10000, 100));
    private final NumberSetting grace = add(new NumberSetting("Grace", "Seconds someone may drop out of render distance before their trail resets.", 45, 5, 300, 5));
    private final BooleanSetting ignoreFriends = add(new BooleanSetting("Ignore Friends", "Never alarm about players on your \\friend list.", true));
    private final BooleanSetting sound = add(new BooleanSetting("Sound", "Play a bell when the alarm fires.", true));
    private final BooleanSetting marker = add(new BooleanSetting("Marker", "Show a red TAIL label and a tracer on players who are following you.", true));

    private static final class Tail {
        String name;
        Vec3d myStart;
        double farthest;
        long firstSeen, lastSeen;
        int alarms;
    }

    private final Map<UUID, Tail> tails = new HashMap<>();
    private long ticks;
    private String dimension = "";

    public TailAlarmModule() {
        super("Tail Alarm", "Warns you when a player keeps following you while you travel.", Category.UTILITY);
    }

    @Override
    protected void onDisable() {
        tails.clear();
    }

    @Override
    public void onWorldLeave() {
        tails.clear();
    }

    @Override
    public void onTick() {
        ticks++;
        String dim = mc.world.getRegistryKey().getValue().toString();
        if (!dim.equals(dimension)) { // portals: positions are not comparable any more
            dimension = dim;
            tails.clear();
        }
        Vec3d me = mc.player.getEntityPos();
        long graceTicks = (long) (grace.get() * 20);
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isSpectator()) continue;
            String name = p.getName().getString();
            if (ignoreFriends.get() && Friends.isFriend(name)) continue;
            Tail t = tails.get(p.getUuid());
            if (t == null || ticks - t.lastSeen > graceTicks) {
                t = new Tail();
                t.myStart = me;
                t.firstSeen = ticks;
                tails.put(p.getUuid(), t);
            }
            t.name = name;
            t.lastSeen = ticks;
            t.farthest = Math.max(t.farthest, horizontal(me, t.myStart));
            int due = (int) (t.farthest / distance.get());
            if (due > t.alarms) {
                t.alarms = due;
                alarm(t, p);
            }
        }
        tails.values().removeIf(t -> ticks - t.lastSeen > graceTicks);
    }

    private void alarm(Tail t, PlayerEntity p) {
        long seconds = (ticks - t.firstSeen) / 20;
        String time = seconds >= 60 ? (seconds / 60) + "m " + (seconds % 60) + "s" : seconds + "s";
        double away = mc.player.distanceTo(p);
        String where = relativeDirection(p.getX() - mc.player.getX(), p.getZ() - mc.player.getZ());
        Notifications.warn("Tail Alarm: " + t.name + " is following you");
        ChatUtil.error(String.format("Tail Alarm: %s has stayed in render distance for %,d blocks of your trip (%s). Now %.0f m %s.%s",
                t.name, (int) t.farthest, time, away, where, t.alarms > 1 ? " Still following!" : ""));
        if (sound.get()) mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BELL, 0.7f));
    }

    /** "behind you", "ahead, to the left"... relative to where you are looking. */
    private String relativeDirection(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(-dx, dz)) - mc.player.getYaw();
        angle = ((angle % 360) + 540) % 360 - 180; // -180..180, 0 = straight ahead
        double a = Math.abs(angle);
        String side = angle > 0 ? "right" : "left";
        if (a < 30) return "ahead of you";
        if (a > 150) return "behind you";
        return (a < 90 ? "ahead, to your " : "behind, to your ") + side;
    }

    private static double horizontal(Vec3d a, Vec3d b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        if (!marker.get()) return;
        for (PlayerEntity p : mc.world.getPlayers()) {
            Tail t = tails.get(p.getUuid());
            if (t == null || t.alarms == 0 || p == mc.player) continue;
            Vec3d pos = p.getLerpedPos(o.tickDelta());
            String km = t.farthest >= 1000 ? String.format("%.1fk", t.farthest / 1000) : String.valueOf((int) t.farthest);
            o.label(pos.x, pos.y + p.getHeight() + 0.6, pos.z, "TAIL " + km + " · " + t.name, 0xFFFF5C5C);
            o.tracer(pos.x, pos.y + p.getHeight() / 2, pos.z, 0xC0FF5C5C);
        }
    }
}
