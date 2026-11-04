package com.mytestclient.program;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mytestclient.config.JsonFiles;
import com.mytestclient.event.KeybindManager;
import com.mytestclient.module.Modules;
import com.mytestclient.module.SafeMode;
import com.mytestclient.module.misc.PlayerProgrammingModule;
import com.mytestclient.util.ChatUtil;
import com.mytestclient.util.Notifications;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Keeps the programs (config/mytestclient/programs.json), starts/stops them with their keys and
 * ticks the running ones. Also remembers recent chat lines for the "chat says" condition.
 */
public final class ProgramManager {
    private static final List<Program> PROGRAMS = new ArrayList<>();
    private static final Map<String, ProgramRunner> RUNNING = new LinkedHashMap<>();
    private static final Map<Integer, Boolean> KEYS = new HashMap<>();
    private static boolean loaded;

    private record ChatLine(long seq, long time, String text) {
    }

    private static final Deque<ChatLine> CHAT = new ArrayDeque<>();
    private static long chatSeq;

    private ProgramManager() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("mytestclient").resolve("programs.json");
    }

    public static List<Program> programs() {
        load();
        return PROGRAMS;
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        JsonElement json = JsonFiles.read(file());
        if (json != null && json.isJsonArray()) {
            for (JsonElement e : json.getAsJsonArray()) {
                try {
                    PROGRAMS.add(Program.fromJson(e.getAsJsonObject()));
                } catch (Exception ignored) {
                }
            }
        }
        if (PROGRAMS.isEmpty()) PROGRAMS.add(example());
    }

    public static void save() {
        if (!loaded) return;
        JsonArray arr = new JsonArray();
        for (Program p : PROGRAMS) arr.add(p.toJson());
        JsonFiles.write(file(), arr);
    }

    /** A small example so the editor isn't empty the first time. */
    private static Program example() {
        Program p = new Program("Example: strip mine");
        ProgBlock say = ProgBlock.create("notify");
        say.args.put("text", "Mining at {x} {y} {z}");
        p.blocks.add(say);
        ProgBlock loop = ProgBlock.create("until");
        BlockTypes.applyConditionDefaults(loop, "inv_full");
        ProgBlock mine = ProgBlock.create("mine_front");
        ProgBlock move = ProgBlock.create("move");
        move.args.put("n", "1");
        loop.body.add(mine);
        loop.body.add(move);
        p.blocks.add(loop);
        ProgBlock done = ProgBlock.create("notify");
        done.args.put("text", "Inventory full!");
        p.blocks.add(done);
        return p;
    }

    public static Program find(String name) {
        for (Program p : programs()) if (p.name.equalsIgnoreCase(name)) return p;
        return null;
    }

    public static boolean isRunning(String name) {
        ProgramRunner r = RUNNING.get(name.toLowerCase(Locale.ROOT));
        return r != null && !r.isFinished();
    }

    public static ProgramRunner runner(Program p) {
        return RUNNING.get(p.name.toLowerCase(Locale.ROOT));
    }

    /** @return true if started */
    public static boolean start(String name, boolean announce) {
        Program p = find(name);
        if (p == null) {
            ChatUtil.error("No program called '" + name + "'.");
            return false;
        }
        String blocked = SafeMode.blockReason(Modules.get(PlayerProgrammingModule.class));
        if (blocked != null) {
            ChatUtil.error("Player Programming is " + blocked);
            return false;
        }
        if (MinecraftClient.getInstance().player == null) return false;
        stop(name, false);
        RUNNING.put(p.name.toLowerCase(Locale.ROOT), new ProgramRunner(p));
        if (announce) Notifications.info("▶ " + p.name);
        return true;
    }

    public static void stop(String name, boolean announce) {
        ProgramRunner r = RUNNING.remove(name.toLowerCase(Locale.ROOT));
        if (r != null) {
            r.stop();
            if (announce) Notifications.info("■ " + r.program.name + " stopped");
        }
    }

    public static void stopAll() {
        for (ProgramRunner r : new ArrayList<>(RUNNING.values())) r.stop();
        RUNNING.clear();
    }

    public static void toggle(Program p) {
        if (isRunning(p.name)) stop(p.name, true);
        else start(p.name, true);
    }

    /** Start of every client tick. */
    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        load();
        // keys: start/stop (not while typing in a screen)
        Map<Integer, Boolean> now = new HashMap<>();
        for (Program p : PROGRAMS) {
            if (p.key <= 0) continue;
            boolean down = mc.currentScreen == null && KeybindManager.isKeyDown(p.key);
            boolean was = KEYS.getOrDefault(p.key, false);
            if (down && !was && !now.containsKey(p.key)) toggle(p);
            now.put(p.key, down);
        }
        KEYS.clear();
        KEYS.putAll(now);

        if (mc.player == null) {
            if (!RUNNING.isEmpty()) stopAll();
            return;
        }
        for (ProgramRunner r : new ArrayList<>(RUNNING.values())) {
            try {
                r.tick();
            } catch (Throwable e) {
                com.mytestclient.MyTestClient.LOGGER.warn("Program {} crashed", r.program.name, e);
                ChatUtil.error("[" + r.program.name + "] stopped: " + e);
                r.stop();
            }
            if (r.isFinished()) {
                RUNNING.values().remove(r);
                Notifications.info("■ " + r.program.name + " finished");
            }
        }
    }

    // ------------------------------------------------------------------ chat

    public static void onChat(String text) {
        CHAT.addLast(new ChatLine(++chatSeq, System.currentTimeMillis(), text.toLowerCase(Locale.ROOT)));
        while (CHAT.size() > 100) CHAT.removeFirst();
    }

    static long chatSeq() {
        return chatSeq;
    }

    /** Sequence number of the first line after {@code after} containing {@code text}, or -1. */
    static long findChat(String text, long after) {
        String t = text.toLowerCase(Locale.ROOT);
        for (ChatLine l : CHAT) if (l.seq() > after && l.text().contains(t)) return l.seq();
        return -1;
    }
}
