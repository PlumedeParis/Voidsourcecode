package com.mytestclient.program;

import com.mytestclient.pathing.Walker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs one program, a few blocks per tick. Actions that take time (walking, waiting, mining...)
 * are {@link BlockTypes.Task}s ticked until they finish; loops yield once per turn so a "forever"
 * without waits never freezes the game.
 */
public final class ProgramRunner {
    private enum Loop {SEQ, REPEAT, FOREVER, UNTIL, WHILE}

    private static final class Frame {
        final List<ProgBlock> list;
        final Loop loop;
        final ProgBlock owner;
        int index, remaining;

        Frame(List<ProgBlock> list, Loop loop, ProgBlock owner, int remaining) {
            this.list = list;
            this.loop = loop;
            this.owner = owner;
            this.remaining = remaining;
        }
    }

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Pattern VAR = Pattern.compile("\\{([a-zA-Z]+)(?::([^}]+))?}");

    public final Program program;
    public final Map<String, Double> vars = new HashMap<>();
    final Set<KeyBinding> pressed = new HashSet<>();
    private final Deque<Frame> stack = new ArrayDeque<>();
    private BlockTypes.Task current;
    private ProgBlock currentBlock;
    private boolean finished;
    long chatCursor;
    public final long startedAt = System.currentTimeMillis();

    public ProgramRunner(Program program) {
        this.program = program;
        stack.push(new Frame(program.blocks, Loop.SEQ, null, 0));
        chatCursor = ProgramManager.chatSeq();
    }

    public boolean isFinished() {
        return finished;
    }

    /** Block being executed (highlighted in the editor). */
    public ProgBlock currentBlock() {
        return currentBlock;
    }

    public void stop() {
        if (current != null) {
            try {
                current.cancel();
            } catch (Throwable ignored) {
            }
        }
        current = null;
        releaseKeys();
        finished = true;
    }

    void releaseKeys() {
        for (KeyBinding k : pressed) k.setPressed(false);
        pressed.clear();
        KeyBinding.updatePressedStates();
    }

    /** One game tick. */
    public void tick() {
        if (finished) return;
        if (mc.player == null || mc.world == null) {
            stop();
            return;
        }
        for (KeyBinding k : pressed) k.setPressed(true);
        if (current != null) {
            if (!current.tick()) return;
            current = null;
        }
        int budget = 300;
        while (budget-- > 0) {
            Frame f = stack.peek();
            if (f == null) {
                stop();
                return;
            }
            if (f.index >= f.list.size()) {
                switch (f.loop) {
                    case SEQ -> stack.pop();
                    case REPEAT -> {
                        if (--f.remaining > 0) {
                            f.index = 0;
                            return; // one turn per tick at most
                        }
                        stack.pop();
                    }
                    case FOREVER -> {
                        f.index = 0;
                        return;
                    }
                    case UNTIL -> {
                        if (test(f.owner)) stack.pop();
                        else {
                            f.index = 0;
                            return;
                        }
                    }
                    case WHILE -> {
                        if (!test(f.owner)) stack.pop();
                        else {
                            f.index = 0;
                            return;
                        }
                    }
                }
                continue;
            }
            ProgBlock b = f.list.get(f.index++);
            currentBlock = b;
            switch (b.type) {
                case "repeat" -> {
                    int n = (int) num(b, "n");
                    if (n > 0 && !b.body.isEmpty()) stack.push(new Frame(b.body, Loop.REPEAT, b, n));
                }
                case "forever" -> {
                    if (b.body.isEmpty()) return; // nothing to loop: just idle
                    stack.push(new Frame(b.body, Loop.FOREVER, b, 0));
                }
                case "if" -> {
                    if (test(b)) stack.push(new Frame(b.body, Loop.SEQ, b, 0));
                }
                case "if_else" -> stack.push(new Frame(test(b) ? b.body : b.elseBody, Loop.SEQ, b, 0));
                case "until" -> {
                    if (!test(b)) stack.push(new Frame(b.body, Loop.UNTIL, b, 0));
                }
                case "while" -> {
                    if (test(b)) stack.push(new Frame(b.body, Loop.WHILE, b, 0));
                }
                case "stop" -> {
                    stop();
                    return;
                }
                default -> {
                    BlockTypes.Def def = BlockTypes.get(b.type);
                    if (def == null || def.exec() == null) continue;
                    BlockTypes.Task t;
                    try {
                        t = def.exec().start(this, b);
                    } catch (Throwable e) {
                        com.mytestclient.MyTestClient.LOGGER.warn("Program block {} failed", b.type, e);
                        com.mytestclient.util.ChatUtil.error("[" + program.name + "] block '" + b.type + "' failed: " + e.getMessage());
                        continue;
                    }
                    if (t != null && !t.tick()) {
                        current = t;
                        return;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ values

    boolean test(ProgBlock b) {
        BlockTypes.CondDef c = BlockTypes.cond(b.arg("cond"));
        boolean v = c != null && c.test().test(this, b);
        return b.arg("not").equals("true") != v;
    }

    /** Text with {x} {y} {z} {health} {hunger} {var:name} replaced. */
    public String text(ProgBlock b, String key) {
        String raw = b.arg(key);
        if (raw.indexOf('{') < 0) return raw;
        Matcher m = VAR.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = switch (m.group(1).toLowerCase(Locale.ROOT)) {
                case "x" -> Integer.toString(mc.player.getBlockX());
                case "y" -> Integer.toString(mc.player.getBlockY());
                case "z" -> Integer.toString(mc.player.getBlockZ());
                case "health" -> Integer.toString(Math.round(mc.player.getHealth()));
                case "hunger" -> Integer.toString(mc.player.getHungerManager().getFoodLevel());
                case "name" -> mc.player.getName().getString();
                case "var" -> m.group(2) == null ? "" : BlockTypes.trim(vars.getOrDefault(m.group(2), 0.0));
                default -> m.group();
            };
            m.appendReplacement(sb, Matcher.quoteReplacement(v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** A number field (also accepts {var:name}). */
    public double num(ProgBlock b, String key) {
        return parse(text(b, key), 0);
    }

    /** Coordinate field: a number, "~" (my position), "~5" (relative) or {var:name.x}. axis 0/1/2 = x/y/z. */
    public double coord(ProgBlock b, String key, int axis) {
        String t = text(b, key).trim();
        double mine = axis == 0 ? mc.player.getX() : axis == 1 ? mc.player.getY() : mc.player.getZ();
        if (t.startsWith("~")) return mine + parse(t.substring(1), 0);
        return parse(t, mine);
    }

    private static double parse(String s, double fallback) {
        try {
            return s.isBlank() ? fallback : Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Condition "chat says": true once per new matching message. */
    boolean consumeChat(String text) {
        long seq = ProgramManager.findChat(text, chatCursor);
        if (seq < 0) return false;
        chatCursor = seq;
        return true;
    }

    static boolean walkerBusy() {
        return Walker.state() == Walker.State.SEARCHING || Walker.state() == Walker.State.WALKING;
    }
}
