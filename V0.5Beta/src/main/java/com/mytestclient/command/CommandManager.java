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
import com.mytestclient.gui.SeedMapScreen;
import com.mytestclient.util.ElytraAutopilot;
import com.mytestclient.util.Navigation;
import com.mytestclient.util.RegistryEntries;
import com.mytestclient.util.StructureLocator;
import com.mytestclient.module.world.SeedInfoModule;
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

    /** Suggests values for the next argument, given the arguments already typed. */
    @FunctionalInterface
    public interface Completer {
        List<String> suggest(String[] previousArgs);
    }

    private record Command(String name, String usage, String description, Handler handler) {
    }

    private static final Map<String, Completer> COMPLETERS = new LinkedHashMap<>();

    private static final Map<String, Command> COMMANDS = new LinkedHashMap<>();
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    static {
        registerDefaults();
        registerCompleters();
    }

    private CommandManager() {
    }

    public static void register(String name, String usage, String description, Handler handler, String... aliases) {
        COMMANDS.put(name, new Command(name, usage, description, handler));
        for (String alias : aliases) ALIASES.put(alias, name);
    }

    /** Attaches argument suggestions to a command (used by the chat autocompletion). */
    public static void completer(String name, Completer completer) {
        COMPLETERS.put(name, completer);
    }

    /**
     * Suggestions for the text typed so far (starting with the prefix): command names first, then
     * the command's arguments. Returns the start index of the word being completed and candidates.
     */
    public static Map.Entry<Integer, List<String>> suggest(String text) {
        String body = text.substring(PREFIX.length());
        int lastSpace = body.lastIndexOf(' ');
        String current = body.substring(lastSpace + 1).toLowerCase(Locale.ROOT);
        int start = PREFIX.length() + lastSpace + 1;
        List<String> candidates = new ArrayList<>();
        if (lastSpace < 0) {
            candidates.addAll(COMMANDS.keySet());
            candidates.addAll(ALIASES.keySet());
        } else {
            String[] typed = body.substring(0, lastSpace).trim().split("\\s+");
            String name = ALIASES.getOrDefault(typed[0].toLowerCase(Locale.ROOT), typed[0].toLowerCase(Locale.ROOT));
            Completer completer = COMPLETERS.get(name);
            if (completer != null) {
                try {
                    candidates.addAll(completer.suggest(Arrays.copyOfRange(typed, 1, typed.length)));
                } catch (RuntimeException ignored) {
                }
            }
        }
        List<String> result = new ArrayList<>();
        for (String c : candidates) if (c.toLowerCase(Locale.ROOT).startsWith(current) && !result.contains(c)) result.add(c);
        return Map.entry(start, result);
    }

    private static List<String> moduleNames() {
        List<String> names = new ArrayList<>();
        for (Module m : Modules.all()) names.add(compact(m.getName()));
        return names;
    }

    private static List<String> waypointNames() {
        List<String> names = new ArrayList<>();
        for (Waypoint wp : MyTestClient.get().waypoints().forCurrentWorld()) if (!wp.name.contains(" ")) names.add(wp.name);
        return names;
    }

    private static List<String> onlinePlayers() {
        List<String> names = new ArrayList<>();
        if (mc.getNetworkHandler() != null) {
            for (var entry : mc.getNetworkHandler().getPlayerList()) names.add(entry.getProfile().name());
        }
        return names;
    }

    private static List<String> keyNames() {
        List<String> names = new ArrayList<>(List.of("none"));
        for (int k = 32; k <= GLFW.GLFW_KEY_LAST; k++) {
            String n = KeyNames.name(k);
            if (!n.startsWith("Key ")) names.add(n.toLowerCase(Locale.ROOT));
        }
        return names;
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

        register("goto", "goto <x> <z> | <x> <y> <z> | <waypoint> | <structure> | <player>",
                "Set the destination (then \\elytra to fly or \\walk to walk). Structures need the seed (\\setseed) on servers.", args -> {
            need(args, 1);
            requireWorld();
            setDestination(join(args, 0));
        });

        register("setseed", "setseed <seed | \"text seed\" | clear>", "Set the world seed (for \\goto structures and Seed Info) on servers.", args -> {
            need(args, 1);
            SeedInfoModule seedInfo = Modules.get(SeedInfoModule.class);
            String value = join(args, 0).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
            if (value.equalsIgnoreCase("clear")) value = "";
            seedInfo.setManualSeed(value);
            if (value.isEmpty()) {
                ChatUtil.info("Seed cleared.");
                return;
            }
            ChatUtil.success("Seed set to " + seedInfo.getSeed() + (mc.isInSingleplayer() ? " (singleplayer uses the real world seed anyway)" : ""));
            String pending = Navigation.pendingStructure();
            if (pending != null && mc.player != null) setDestination(pending);
        });

        register("elytra", "elytra [destination] | stop", "Fly to the destination with the elytra, avoiding obstacles (works in the Nether).", args -> {
            if (args.length == 1 && args[0].equalsIgnoreCase("stop")) {
                ElytraAutopilot.stop("stopped");
                return;
            }
            requireWorld();
            if (args.length > 0 && !setDestination(join(args, 0))) {
                flyWhenResolved = true; // searching / waiting for \setseed: take off when found
                return;
            }
            startFlight();
        }, "fly");

        register("seedmap", "seedmap [seed]", "Open the map of a seed: biomes and structures (uses your world's seed if known).", args -> {
            String seed = args.length > 0 ? join(args, 0).replace("\"", "") : null;
            // the chat screen closes right after this command, so open the map on the next tick
            mc.send(() -> mc.setScreen(new SeedMapScreen(seed)));
        }, "map");

        register("walk", "walk", "Walk to the destination in a straight line.", args -> {
            requireWorld();
            if (!Navigation.has()) throw new IllegalArgumentException("No destination: use \\goto first.");
            PathFollower.start(List.of(Navigation.target()), "Walk to " + Navigation.label());
        });

        register("stop", "stop", "Stop walking, flying and clear nothing else.", args -> {
            PathFollower.stop("stopped");
            ElytraAutopilot.stop("stopped");
        });

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
                    setDestination(join(args, 1));
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

    /**
     * Resolves a destination: coordinates, waypoint, player (current or last known position) or
     * structure (exact in singleplayer, from the seed on servers).
     */
    /** Set by {@code \\elytra <structure>}: take off as soon as the async search finishes. */
    private static boolean flyWhenResolved;

    /** Completion of an asynchronous structure search. */
    private static void resolved(String query, StructureLocator.Result result, Throwable error) {
        if (mc.player == null) return;
        if (error != null || result == null) {
            flyWhenResolved = false;
            ChatUtil.error(error != null ? "Search failed: " + error.getMessage()
                    : "No " + query + " found in this dimension (fortress/bastion: Nether, end city: End).");
            return;
        }
        Navigation.set(Vec3d.ofBottomCenter(result.pos()), result.name());
        announce(result.note());
        if (flyWhenResolved) {
            flyWhenResolved = false;
            startFlight();
        }
    }

    private static void startFlight() {
        String error = ElytraAutopilot.start();
        if (error != null) {
            ChatUtil.error("Can't fly: " + error + ".");
            return;
        }
        ChatUtil.success("Elytra autopilot to " + Navigation.label() + ". \\stop to cancel.");
    }

    /** @return true if the destination is set now, false if it is being searched (async) or waits for a seed */
    private static boolean setDestination(String query) {
        String[] parts = query.trim().split("\\s+");
        try {
            if (parts.length == 2) {
                set(new Vec3d(Double.parseDouble(parts[0]) + 0.5, mc.player.getY(), Double.parseDouble(parts[1]) + 0.5), parts[0] + " " + parts[1]);
                return true;
            }
            if (parts.length == 3) {
                set(new Vec3d(Double.parseDouble(parts[0]) + 0.5, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]) + 0.5),
                        parts[0] + " " + parts[1] + " " + parts[2]);
                return true;
            }
        } catch (NumberFormatException ignored) {
        }
        for (Waypoint wp : MyTestClient.get().waypoints().forCurrentWorld()) {
            if (wp.name.equalsIgnoreCase(query)) {
                // waypoints from the other dimension are converted by Navigation
                Navigation.set(new Vec3d(wp.x + 0.5, wp.y, wp.z + 0.5), wp.name);
                if (!wp.dimension.equals(WaypointManager.currentDimension())) {
                    Vec3d t = toDimension(wp);
                    if (t != null) Navigation.set(t, wp.name);
                }
                announce("waypoint");
                return true;
            }
        }
        for (var p : mc.world.getPlayers()) {
            if (p.getName().getString().equalsIgnoreCase(query)) {
                set(new Vec3d(p.getX(), p.getY(), p.getZ()), p.getName().getString());
                return true;
            }
        }
        if (StructureLocator.isStructureName(query)) {
            if (mc.isInSingleplayer() && mc.getServer() != null) {
                ChatUtil.info("Searching for " + query + "...");
                StructureLocator.locateExact(query, mc.player.getBlockPos())
                        .whenComplete((result, error) -> mc.execute(() -> resolved(query, result, error)));
                return false;
            }
            SeedInfoModule seedInfo = Modules.get(SeedInfoModule.class);
            Long seed = seedInfo.getSeed();
            if (seed == null) {
                Navigation.clear();
                Navigation.setPendingStructure(query);
                ChatUtil.info("Destination '" + query + "' needs the world seed: type \\setseed <seed> and it will be calculated.");
                return false;
            }
            ChatUtil.info("Calculating " + query + " from the seed...");
            var from = mc.player.getBlockPos();
            String dimension = WaypointManager.currentDimension();
            java.util.concurrent.CompletableFuture
                    .supplyAsync(() -> StructureLocator.locateFromSeed(query, seed, from, dimension))
                    .whenComplete((result, error) -> mc.execute(() -> resolved(query, result, error)));
            return false;
        }
        throw new IllegalArgumentException("Unknown destination '" + query + "'.");
    }

    private static Vec3d toDimension(Waypoint wp) {
        String now = WaypointManager.currentDimension();
        if (wp.dimension.equals("minecraft:overworld") && now.equals("minecraft:the_nether")) return new Vec3d(wp.x / 8.0, 70, wp.z / 8.0);
        if (wp.dimension.equals("minecraft:the_nether") && now.equals("minecraft:overworld")) return new Vec3d(wp.x * 8.0, 100, wp.z * 8.0);
        return null;
    }

    private static void set(Vec3d pos, String name) {
        Navigation.set(pos, name);
        announce(null);
    }

    private static void announce(String note) {
        Vec3d t = Navigation.target();
        double dist = Math.sqrt(mc.player.squaredDistanceTo(t.x, mc.player.getY(), t.z));
        ChatUtil.success(String.format("Destination: %s at %d %d %d (%.0f blocks). \\elytra to fly, \\walk to walk.",
                Navigation.label(), (int) t.x, (int) t.y, (int) t.z, dist));
        if (note != null && !note.equals("exact")) ChatUtil.info("(" + note + ")");
    }

    private static void registerCompleters() {
        completer("toggle", a -> a.length == 0 ? moduleNames() : List.of());
        completer("bind", a -> a.length == 0 ? moduleNames() : a.length == 1 ? keyNames() : List.of());
        completer("set", a -> {
            if (a.length == 0) return moduleNames();
            Module m = module(a[0]);
            if (a.length == 1) {
                List<String> names = new ArrayList<>();
                for (Setting<?> s : m.getSettings()) names.add(compact(s.getName()));
                return names;
            }
            Setting<?> s = setting(m, a[1]);
            if (a.length == 2) {
                if (s instanceof BooleanSetting) return List.of("true", "false", "toggle");
                if (s instanceof ModeSetting mode) {
                    List<String> modes = new ArrayList<>();
                    for (String v : mode.getModes()) modes.add(compact(v));
                    return modes;
                }
                if (s instanceof ListSetting) return List.of("add", "remove");
                if (s instanceof NumberSetting n) return List.of(n.format());
            }
            if (a.length == 3 && s instanceof ListSetting list) {
                List<String> ids = new ArrayList<>();
                if (a[2].equalsIgnoreCase("remove")) ids.addAll(list.get());
                else for (var e : RegistryEntries.get(list.getKind())) ids.add(e.id());
                return ids;
            }
            return List.of();
        });
        completer("breadcrumbs", a -> a.length == 0 ? List.of("back", "clear", "stop") : List.of());
        Completer destinations = a -> {
            if (a.length > 0) return List.of();
            List<String> all = new ArrayList<>(waypointNames());
            all.addAll(StructureLocator.names());
            if (mc.world != null) for (var p : mc.world.getPlayers()) if (p != mc.player) all.add(p.getName().getString());
            if (mc.player != null) all.add(mc.player.getBlockX() + " " + mc.player.getBlockZ());
            return all;
        };
        completer("goto", destinations);
        completer("elytra", a -> {
            List<String> all = new ArrayList<>(destinations.suggest(a));
            if (a.length == 0) all.add(0, "stop");
            return all;
        });
        completer("setseed", a -> a.length == 0 ? List.of("clear") : List.of());
        completer("waypoint", a -> {
            if (a.length == 0) return List.of("add", "del", "list", "goto");
            if (a.length == 1 && !a[0].equalsIgnoreCase("add")) return waypointNames();
            return List.of();
        });
        completer("friend", a -> {
            if (a.length == 0) return List.of("add", "del", "list");
            if (a.length == 1 && a[0].equalsIgnoreCase("add")) return onlinePlayers();
            if (a.length == 1) return new ArrayList<>(Friends.all());
            return List.of();
        });
        completer("profile", a -> {
            if (a.length == 0) return List.of("save", "load", "list");
            if (a.length == 1 && !a[0].equalsIgnoreCase("list")) return MyTestClient.get().profiles().list();
            return List.of();
        });
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
