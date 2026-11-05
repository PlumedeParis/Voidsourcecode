package com.mytestclient.module.misc;

import com.mytestclient.MyTestClient;
import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Modules;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.util.ChatUtil;
import com.mytestclient.util.Navigation;
import com.mytestclient.util.Notifications;
import com.mytestclient.waypoint.Waypoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leak Guard: stops you from leaking your coordinates in chat.
 * <p>
 * Before a chat message (or a private message command like /msg, /w, /r) leaves your client,
 * the numbers in it are compared with the places you would not want to share: where you are
 * now, your waypoints and your current destination. Nether/Overworld conversions (×8, ÷8) and
 * shorthands like "12k", "12.5k" or "12,000" are understood. If two numbers in the message
 * match an X and Z within the tolerance, the message is held back and put back in the chat box;
 * press Enter again within 10 seconds to send it anyway. Spawn coordinates are ignored.
 * <p>
 * Nothing extra is sent to the server: a held message is simply not sent.
 */
public class LeakGuardModule extends Module {
    private final NumberSetting tolerance = add(new NumberSetting("Tolerance", "How close (blocks) two numbers must be to a protected X and Z to count as a leak.", 300, 10, 5000, 10));
    private final NumberSetting spawnRadius = add(new NumberSetting("Ignore Spawn", "Places closer than this to 0 0 (Overworld blocks) are not protected.", 1500, 0, 20000, 100));
    private final BooleanSetting waypoints = add(new BooleanSetting("Waypoints", "Also protect the coordinates of your waypoints.", true));
    private final BooleanSetting privateMessages = add(new BooleanSetting("Private Messages", "Also check /msg, /w, /tell, /r and similar commands.", true));

    private static final Set<String> MESSAGE_COMMANDS = Set.of(
            "msg", "w", "tell", "whisper", "r", "reply", "m", "pm", "dm", "me", "say", "mail",
            "party", "p", "pc", "t", "teammsg", "tm", "cc", "gc", "ac", "message");
    /** 12,000 | 12_000 | -12.5k | 12k | 1234 | 1.5 */
    private static final Pattern NUMBER = Pattern.compile("(?<![\\w.])[-+]?(?:\\d{1,3}(?:[,_]\\d{3})+|\\d+(?:\\.\\d+)?)(k)?(?![\\w])", Pattern.CASE_INSENSITIVE);

    private record Place(String label, double x, double z) {
    }

    private String heldBack;
    private long heldAt;

    public LeakGuardModule() {
        super("Leak Guard", "Holds back chat messages that contain your coordinates or your waypoints'.", Category.MISC);
    }

    /** From the chat hook: false = do not send. */
    public static boolean allowChat(String message) {
        LeakGuardModule m = Modules.get(LeakGuardModule.class);
        return m == null || !m.isEnabled() || m.check(message, false);
    }

    /** From the command hook ({@code command} has no leading slash). */
    public static boolean allowCommand(String command) {
        LeakGuardModule m = Modules.get(LeakGuardModule.class);
        if (m == null || !m.isEnabled() || !m.privateMessages.get()) return true;
        String name = command.split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (name.contains(":")) name = name.substring(name.indexOf(':') + 1);
        if (!MESSAGE_COMMANDS.contains(name)) return true;
        return m.check(command, true);
    }

    private boolean check(String text, boolean command) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return true;
        if (text.equals(heldBack) && System.currentTimeMillis() - heldAt < 10_000) {
            heldBack = null; // second Enter: the user really wants to send it
            return true;
        }
        Place hit = findLeak(text, places(mc));
        if (hit == null) return true;
        heldBack = text;
        heldAt = System.currentTimeMillis();
        Notifications.warn("Leak Guard held back a message");
        ChatUtil.error("Leak Guard: that message contains coordinates near " + hit.label + ". It was NOT sent. Press Enter again within 10 s to send it anyway.");
        String restore = command ? "/" + text : text;
        mc.send(() -> mc.setScreen(new ChatScreen(restore, false)));
        return false;
    }

    /** Everything worth protecting, in the coordinates of the dimension you are in. */
    private List<Place> places(MinecraftClient mc) {
        List<Place> out = new ArrayList<>();
        String dim = mc.world.getRegistryKey().getValue().toString();
        boolean nether = mc.world.getRegistryKey() == World.NETHER;
        out.add(new Place("your position", mc.player.getX(), mc.player.getZ()));
        Vec3d target = Navigation.target();
        if (target != null) out.add(new Place("your destination", target.x, target.z));
        if (waypoints.get()) {
            for (Waypoint wp : MyTestClient.get().waypoints().forCurrentWorld()) {
                double scale = 1;
                if (!wp.dimension.equals(dim)) {
                    if (wp.dimension.equals("minecraft:the_nether") && !nether) scale = 8;
                    else if (wp.dimension.equals("minecraft:overworld") && nether) scale = 1 / 8.0;
                    else continue;
                }
                out.add(new Place("waypoint \"" + wp.name + "\"", wp.x * scale, wp.z * scale));
            }
        }
        // ignore anything near spawn (in Overworld blocks)
        double spawn = spawnRadius.get();
        out.removeIf(p -> Math.hypot(p.x, p.z) * (nether ? 8 : 1) < spawn);
        return out;
    }

    private Place findLeak(String text, List<Place> places) {
        if (places.isEmpty()) return null;
        List<Double> numbers = new ArrayList<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            String raw = m.group().replace(",", "").replace("_", "").replaceAll("(?i)k$", "");
            try {
                double v = Double.parseDouble(raw);
                if (m.group(1) != null) v *= 1000;
                numbers.add(v);
            } catch (NumberFormatException ignored) {
            }
        }
        double tol = tolerance.get();
        for (Place p : places) {
            // the same place as it would be written in the other dimension, too
            // (x, z, tolerance): the tolerance scales with the conversion
            double[][] variants = {{p.x, p.z, tol}, {p.x * 8, p.z * 8, tol * 8}, {p.x / 8, p.z / 8, tol / 8}};
            for (double[] v : variants) {
                // "x z" or "x y z": the Z is one or two numbers after the X
                for (int i = 0; i < numbers.size(); i++) {
                    for (int j = i + 1; j <= Math.min(numbers.size() - 1, i + 2); j++) {
                        if (Math.abs(numbers.get(i) - v[0]) <= v[2] && Math.abs(numbers.get(j) - v[1]) <= v[2]) return p;
                    }
                }
            }
        }
        return null;
    }
}
