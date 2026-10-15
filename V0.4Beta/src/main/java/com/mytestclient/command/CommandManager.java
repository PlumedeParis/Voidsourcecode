package com.mytestclient.command;

import com.mytestclient.MyTestClient;
import com.mytestclient.module.Module;
import com.mytestclient.module.Modules;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.ColorSetting;
import com.mytestclient.module.setting.ListSetting;
import com.mytestclient.module.setting.ModeSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.module.setting.Setting;
import com.mytestclient.module.setting.StringSetting;
import com.mytestclient.module.utility.ChestMemoryModule;
import com.mytestclient.module.utility.PlayerTrackerModule;
import com.mytestclient.module.visuals.BreadcrumbsModule;
import com.mytestclient.module.world.EventLocatorModule;
import com.mytestclient.module.world.QuickWaypointModule;
import com.mytestclient.util.ChatUtil;
import com.mytestclient.util.Friends;
import com.mytestclient.util.KeyNames;
import com.mytestclient.util.PathFollower;
import com.mytestclient.waypoint.Waypoint;
import com.mytestclient.waypoint.WaypointManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Client-side chat commands starting with {@code \} (e.g. {@code \breadcrumbs back}). They are
 * handled locally and never sent to the server. Type {@code \help} for the list.
 * <p>
 * To add a command: call {@link #register} in {@link #registerDefaults()}.
 */
public final class CommandManager {
    public static final String PREFIX = "\\";

    @FunctionalInterface
    public interface Handler {
        void run(String[] args);
    }

    private record Command(String name, String usage, String description, Handler handler) {
    }

    private static final Map<String, Command> COMMANDS = new LinkedHashMap<>();
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    static {
        registerDefaults();
    }

    private CommandManager() {
    }

    public static void register(String name, String usage, String description, Handler handler, String... aliases) {
        COMMANDS.put(name, new Command(name, usage, description, handler));
        for (String alias : aliases) ALIASES.put(alias, name);
    }

    /** @return true if the message was a client command (and must not be sent to the server) */
    public static boolean handle(String message) {
        if (!message.startsWith(PREFIX)) return false;
        String[] parts = message.substring(PREFIX.length()).trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            help();
            return true;
        }
        String name = parts[0].toLowerCase(Locale.ROOT);
        name = ALIASES.getOrDefault(name, name);
        Command command = COMMANDS.get(name);
        if (command == null) {
            ChatUtil.error("Unknown command '" + parts[0] + "'. Type \\help");
            return true;
        }
        try {
            command.handler.run(Arrays.copyOfRange(parts, 1, parts.length));
        } catch (IllegalArgumentException e) {
            ChatUtil.error(e.getMessage() + "  Usage: \\" + command.usage);
        } catch (RuntimeException e) {
            ChatUtil.error("Error: " + e);
            MyTestClient.LOGGER.error("Command {} failed", message, e);
        }
        return true;
    }

    // ------------------------------------------------------------------ helpers

    private static void need(String[] args, int count) {
        if (args.length < count) throw new IllegalArgumentException("Missing arguments.");
    }

    private static String join(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    private static String compact(String s) {
        return s.replace(" ", "").replace("_", "").toLowerCase(Locale.ROOT);
    }

    /** Finds a module by name, ignoring spaces and case ("autocrystal" = "Auto Crystal"). */
    private static Module module(String name) {
        for (Module m : Modules.all()) if (compact(m.getName()).equals(compact(name))) return m;
        throw new IllegalArgumentException("No module named '" + name + "'.");
    }

    private static Setting<?> setting(Module module, String name) {
        for (Setting<?> s : module.getSettings()) if (compact(s.getName()).equals(compact(name))) return s;
        throw new IllegalArgumentException("Module " + module.getName() + " has no setting '" + name + "'.");
    }

    private static int parseKey(String name) {
        if (name.equalsIgnoreCase("none")) return GLFW.GLFW_KEY_UNKNOWN;
        for (int k = 32; k <= GLFW.GLFW_KEY_LAST; k++) if (KeyNames.name(k).equalsIgnoreCase(name)) return k;
        throw new IllegalArgumentException("Unknown key '" + name + "'.");
    }

    private static void requireWorld() {
        if (mc.player == null) throw new IllegalArgumentException("Join a world first.");
    }

    // ------------------------------------------------------------------ commands

    private static void registerDefaults() {
        register("help", "help", "List commands.", args -> help(), "?", "h");

        register("toggle", "toggle <module>", "Turn a module on/off.", args -> {
            need(args, 1);
            Module m = module(join(args, 0));
            m.toggle();
            if (!m.isAction()) ChatUtil.info(m.getName() + " is now " + (m.isEnabled() ? "ON" : "OFF"));
        }, "t");

        register("modules", "modules", "List all modules.", args -> {
            StringBuilder sb = new StringBuilder();
            for (Module m : Modules.all()) sb.append(m.isEnabled() ? "§a" : "§7").append(m.getName()).append("§7, ");
            ChatUtil.info(sb.toString());
        }, "mods");

        register("bind", "bind <module> <key|none>", "Set a module keybind.", args -> {
            need(args, 2);
            Module m = module(join(Arrays.copyOf(args, args.length - 1), 0));
            int key = parseKey(args[args.length - 1]);
            m.setKey(key);
            ChatUtil.success(m.getName() + " bound to " + KeyNames.name(key));
        });

        register("set", "set <module> <setting> <value>", "Change a setting (no spaces in names needed).", args -> {
            need(args, 3);
            Module m = module(args[0]);
            Setting<?> s = setting(m, args[1]);
            String value = join(args, 2);
            applySetting(s, value);
            ChatUtil.success(m.getName() + " > " + s.getName() + " = " + s.get());
        });

        register("breadcrumbs", "breadcrumbs <back|clear|stop>", "Walk back along your trail, or clear it.", args -> {
            need(args, 1);
            BreadcrumbsModule crumbs = Modules.get(BreadcrumbsModule.class);
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "back" -> {
                    requireWorld();
                    if (crumbs.size() < 2) throw new IllegalArgumentException("No trail recorded: enable Breadcrumbs and walk first.");
                    PathFollower.start(crumbs.pathBack(), "Breadcrumbs back");
                }
                case "clear" -> {
                    crumbs.clear();
                    ChatUtil.success("Trail cleared.");
                }
                case "stop" -> PathFollower.stop("stopped");
                default -> throw new IllegalArgumentException("Unknown option.");
            }
        }, "bc", "breadcrumb");

        register("goto", "goto <x> <z> | <x> <y> <z> | <waypoint>", "Walk in a straight line to coordinates or a waypoint.", args -> {
            need(args, 1);
            requireWorld();
            Vec3d target;
            try {
                if (args.length == 2) target = new Vec3d(Double.parseDouble(args[0]), mc.player.getY(), Double.parseDouble(args[1]));
                else if (args.length == 3) target = new Vec3d(Double.parseDouble(args[0]), Double.parseDouble(args[1]), Double.parseDouble(args[2]));
                else throw new NumberFormatException();
            } catch (NumberFormatException e) {
                Waypoint wp = findWaypoint(join(args, 0));
                target = new Vec3d(wp.x + 0.5, wp.y, wp.z + 0.5);
            }
            PathFollower.start(List.of(target), "Goto");
        });

        register("stop", "stop", "Stop walking (goto / breadcrumbs back).", args -> PathFollower.stop("stopped"));

        register("waypoint", "waypoint <add|del|list|goto> [name]", "Manage waypoints.", args -> {
            need(args, 1);
            WaypointManager manager = MyTestClient.get().waypoints();
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "add" -> {
                    requireWorld();
                    QuickWaypointModule.addHere(args.length > 1 ? join(args, 1) : null);
                }
                case "del", "remove" -> {
                    need(args, 2);
                    Waypoint wp = findWaypoint(join(args, 1));
                    manager.remove(wp);
                    ChatUtil.success("Deleted waypoint " + wp.name);
                }
                case "list" -> {
                    List<Waypoint> list = manager.forCurrentWorld();
                    if (list.isEmpty()) ChatUtil.info("No waypoints in this world.");
                    for (Waypoint wp : list) ChatUtil.info(" " + wp.name + ": " + wp.x + " " + wp.y + " " + wp.z + " (" + wp.dimension + ")");
                }
                case "goto" -> {
                    need(args, 2);
                    requireWorld();
                    Waypoint wp = findWaypoint(join(args, 1));
                    PathFollower.start(List.of(new Vec3d(wp.x + 0.5, wp.y, wp.z + 0.5)), "Goto " + wp.name);
                }
                default -> throw new IllegalArgumentException("Unknown option.");
            }
        }, "wp");

        register("friend", "friend <add|del|list> [name]", "Friends are never attacked and shown in their own colour.", args -> {
            need(args, 1);
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "add" -> {
                    need(args, 2);
                    Friends.add(args[1]);
                    ChatUtil.success(args[1] + " is now a friend.");
                }
                case "del", "remove" -> {
                    need(args, 2);
                    ChatUtil.info(Friends.remove(args[1]) ? args[1] + " removed." : args[1] + " was not a friend.");
                }
                case "list" -> ChatUtil.info("Friends: " + (Friends.all().isEmpty() ? "none" : String.join(", ", Friends.all())));
                default -> throw new IllegalArgumentException("Unknown option.");
            }
        }, "f");

        register("profile", "profile <save|load|list> [name]", "Save/load module presets.", args -> {
            need(args, 1);
            var profiles = MyTestClient.get().profiles();
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "save" -> {
                    need(args, 2);
                    ChatUtil.info(profiles.save(args[1]) ? "Saved profile " + args[1] : "Could not save.");
                }
                case "load" -> {
                    need(args, 2);
                    ChatUtil.info(profiles.load(args[1]) ? "Loaded profile " + args[1] : "No profile named " + args[1]);
                }
                case "list" -> ChatUtil.info("Profiles: " + String.join(", ", profiles.list()));
                default -> throw new IllegalArgumentException("Unknown option.");
            }
        });

        register("coords", "coords", "Copy your coordinates to the clipboard.", args -> {
            requireWorld();
            String c = mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ();
            mc.keyboard.setClipboard(c);
            ChatUtil.success("Copied " + c);
        });

        register("find", "find <item>", "Find an item in containers you opened before (Chest Memory).", args -> {
            need(args, 1);
            ChestMemoryModule memory = Modules.get(ChestMemoryModule.class);
            if (!memory.isEnabled()) ChatUtil.info("Tip: enable Chest Memory so containers you open are remembered.");
            memory.find(join(args, 0));
        });

        register("players", "players", "List online players and where they were last seen.", args -> {
            requireWorld();
            Modules.get(PlayerTrackerModule.class).printPlayers();
        }, "who");

        register("events", "events", "List recent world events (Event Locator).", args -> Modules.get(EventLocatorModule.class).printEvents());
    }

    private static Waypoint findWaypoint(String name) {
        for (Waypoint wp : MyTestClient.get().waypoints().forCurrentWorld()) if (wp.name.equalsIgnoreCase(name)) return wp;
        throw new IllegalArgumentException("No waypoint named '" + name + "' in this world.");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void applySetting(Setting<?> s, String value) {
        try {
            if (s instanceof BooleanSetting b) {
                b.set(value.equalsIgnoreCase("toggle") ? !b.get() : Boolean.parseBoolean(value) || value.equalsIgnoreCase("on"));
            } else if (s instanceof NumberSetting n) {
                n.set(Double.parseDouble(value));
            } else if (s instanceof ModeSetting m) {
                for (String mode : m.getModes()) if (compact(mode).equals(compact(value))) {
                    m.set(mode);
                    return;
                }
                throw new IllegalArgumentException("Modes: " + String.join(", ", m.getModes()));
            } else if (s instanceof ColorSetting c) {
                c.set((int) Long.parseLong(value.replace("#", ""), 16));
            } else if (s instanceof StringSetting str) {
                str.set(value);
            } else if (s instanceof ListSetting list) {
                String[] p = value.split("\\s+");
                if (p.length != 2) throw new IllegalArgumentException("Use: add|remove <id>");
                String id = p[1].contains(":") ? p[1] : "minecraft:" + p[1];
                if (p[0].equalsIgnoreCase("add")) list.add(id);
                else list.remove(id);
            } else {
                ((Setting) s).set(value);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid value '" + value + "'.");
        }
    }

    private static void help() {
        ChatUtil.success("VoidClient commands (prefix \\):");
        List<String> lines = new ArrayList<>();
        for (Command c : COMMANDS.values()) lines.add(" \\" + c.usage + " §8- §7" + c.description);
        for (String line : lines) ChatUtil.info(line);
    }
}
