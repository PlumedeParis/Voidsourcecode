package com.mytestclient.program;

import com.mytestclient.MyTestClient;
import com.mytestclient.command.CommandManager;
import com.mytestclient.module.Module;
import com.mytestclient.pathing.BlockInfo;
import com.mytestclient.pathing.Goal;
import com.mytestclient.pathing.Travel;
import com.mytestclient.pathing.Walker;
import com.mytestclient.util.ChatUtil;
import com.mytestclient.util.CombatUtil;
import com.mytestclient.util.ElytraAutopilot;
import com.mytestclient.util.Navigation;
import com.mytestclient.waypoint.Waypoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Every block and condition of Player Programming: how it looks (category colour, label with
 * {fields}) and what it does. Add a block with {@link #block}, a condition with {@link #cond}.
 */
public final class BlockTypes {
    private BlockTypes() {
    }

    // ------------------------------------------------------------------ model

    public enum Kind {NUMBER, COORD, TEXT, ITEM, BLOCK, ENTITY, CHOICE, MODULE, PROGRAM, COND, VAR}

    public enum Shape {STACK, C, IF_ELSE, CAP}

    public record Param(Kind kind, String def, List<String> options) {
    }

    public record Category(String name, int color, String icon) {
    }

    /** A running action. {@code tick} returns true when finished. */
    public interface Task {
        boolean tick();

        default void cancel() {
        }
    }

    @FunctionalInterface
    public interface Exec {
        /** @return a task that takes time, or null if the block finished instantly */
        Task start(ProgramRunner r, ProgBlock b);
    }

    @FunctionalInterface
    public interface Test {
        boolean test(ProgramRunner r, ProgBlock b);
    }

    public record Def(String id, Category category, String label, Map<String, Param> params, Shape shape, Exec exec, String help) {
    }

    public record CondDef(String id, String label, Map<String, Param> params, Test test) {
    }

    public static final Category MOTION = new Category("Motion", 0xFF4C97FF, "➜");
    public static final Category LOOK = new Category("Look", 0xFF9966FF, "◎");
    public static final Category ACTIONS = new Category("Actions", 0xFFE6505F, "✋");
    public static final Category INVENTORY = new Category("Inventory", 0xFFFF8C1A, "▤");
    public static final Category CHAT = new Category("Chat", 0xFF0FBD8C, "✉");
    public static final Category CONTROL = new Category("Control", 0xFFFFAB19, "⟳");
    public static final Category VARIABLES = new Category("Variables", 0xFFFF661A, "✎");
    public static final Category MODULES = new Category("Modules", 0xFFCF63CF, "⚙");
    public static final List<Category> CATEGORIES = List.of(MOTION, LOOK, ACTIONS, INVENTORY, CHAT, CONTROL, VARIABLES, MODULES);
    public static final int COND_COLOR = 0xFF5CB1D6;

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();
    private static final Map<String, CondDef> CONDS = new LinkedHashMap<>();

    public static Def get(String id) {
        return DEFS.get(id);
    }

    public static CondDef cond(String id) {
        return CONDS.get(id);
    }

    public static List<Def> of(Category c) {
        List<Def> out = new ArrayList<>();
        for (Def d : DEFS.values()) if (d.category() == c) out.add(d);
        return out;
    }

    public static List<String> conditionIds() {
        return new ArrayList<>(CONDS.keySet());
    }

    /** Sets the fields of a newly chosen condition to their defaults (stored as "c.&lt;name&gt;"). */
    public static void applyConditionDefaults(ProgBlock b, String condId) {
        b.args.keySet().removeIf(k -> k.startsWith("c."));
        CondDef c = CONDS.get(condId);
        if (c == null) return;
        b.args.put("cond", condId);
        c.params().forEach((k, p) -> b.args.put("c." + k, p.def()));
    }

    // ------------------------------------------------------------------ builders

    private static Param num(double def) {
        return new Param(Kind.NUMBER, trim(def), List.of());
    }

    private static Param coord(String def) {
        return new Param(Kind.COORD, def, List.of());
    }

    private static Param text(String def) {
        return new Param(Kind.TEXT, def, List.of());
    }

    private static Param item(String def) {
        return new Param(Kind.ITEM, def, List.of());
    }

    private static Param blockP(String def) {
        return new Param(Kind.BLOCK, def, List.of());
    }

    private static Param entity(String def) {
        return new Param(Kind.ENTITY, def, List.of());
    }

    private static Param choice(String... options) {
        return new Param(Kind.CHOICE, options[0], List.of(options));
    }

    private static Param kind(Kind k, String def) {
        return new Param(k, def, List.of());
    }

    private static Map<String, Param> p(Object... kv) {
        Map<String, Param> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Param) kv[i + 1]);
        return m;
    }

    private static void block(String id, Category c, String label, Map<String, Param> params, Shape shape, Exec exec, String help) {
        DEFS.put(id, new Def(id, c, label, params, shape, exec, help));
    }

    private static void cond(String id, String label, Map<String, Param> params, Test test) {
        CONDS.put(id, new CondDef(id, label, params, test));
    }

    static String trim(double d) {
        return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
    }

    // ------------------------------------------------------------------ helpers

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static Task waitTicks(int n) {
        int[] left = {n};
        return () -> --left[0] <= 0;
    }

    /** Runs {@code step} every tick until {@code done} or the timeout. */
    private static Task until(BooleanSupplier done, int timeout, Runnable step, Runnable cleanup) {
        int[] t = {0};
        return new Task() {
            public boolean tick() {
                if (done.getAsBoolean() || ++t[0] > timeout) {
                    cleanup.run();
                    return true;
                }
                step.run();
                return false;
            }

            public void cancel() {
                cleanup.run();
            }
        };
    }

    private static KeyBinding key(String name) {
        return switch (name) {
            case "back" -> mc.options.backKey;
            case "left" -> mc.options.leftKey;
            case "right" -> mc.options.rightKey;
            case "jump" -> mc.options.jumpKey;
            case "sneak" -> mc.options.sneakKey;
            case "sprint" -> mc.options.sprintKey;
            case "attack" -> mc.options.attackKey;
            case "use" -> mc.options.useKey;
            default -> mc.options.forwardKey;
        };
    }

    static boolean idMatches(String id, String query) {
        String q = query.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        if (q.isEmpty() || q.equals("any") || q.equals("*")) return true;
        if (!q.contains(":")) id = id.substring(id.indexOf(':') + 1);
        return id.equals(q) || (!q.contains(":") && id.contains(q) && q.length() >= 3);
    }

    static boolean stackMatches(ItemStack s, String query) {
        return !s.isEmpty() && (idMatches(Registries.ITEM.getId(s.getItem()).toString(), query)
                || s.getName().getString().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT).trim()) && query.trim().length() >= 3);
    }

    static boolean entityMatches(Entity e, String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (q.equals("any") || q.isEmpty()) return e instanceof LivingEntity;
        if (q.equals("player")) return e instanceof PlayerEntity;
        if (q.equals("monster") || q.equals("hostile")) return com.mytestclient.util.EntityKind.of(e) == com.mytestclient.util.EntityKind.HOSTILE;
        if (q.equals("animal") || q.equals("passive")) return com.mytestclient.util.EntityKind.of(e) == com.mytestclient.util.EntityKind.PASSIVE;
        return idMatches(Registries.ENTITY_TYPE.getId(e.getType()).toString(), q) || e.getName().getString().equalsIgnoreCase(query.trim());
    }

    static Entity nearestEntity(String query, double range) {
        Entity best = null;
        double bestD = range * range;
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || !e.isAlive() || !entityMatches(e, query)) continue;
            double d = mc.player.squaredDistanceTo(e);
            if (d <= bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** Hotbar slot holding a matching item (moves it from the inventory to the hotbar if needed), or -1. */
    static int bringToHotbar(String query) {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) if (stackMatches(inv.getStack(i), query)) return i;
        for (int i = 9; i < 36; i++) {
            if (stackMatches(inv.getStack(i), query)) {
                int target = inv.getSelectedSlot();
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, i, target, SlotActionType.SWAP, mc.player);
                return target;
            }
        }
        return -1;
    }

    static BlockPos front() {
        return BlockPos.ofFloored(mc.player.getX(), mc.player.getY() + 0.2, mc.player.getZ()).offset(mc.player.getHorizontalFacing());
    }

    /** Walks next to a block (if needed) with the pathfinder, then runs {@code then}. */
    static Task reachThen(BlockPos pos, Task then) {
        return new Task() {
            boolean walking, arrived;

            public boolean tick() {
                if (!arrived) {
                    double d = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos));
                    if (d <= 4.4) {
                        arrived = true;
                        if (walking) Walker.stop();
                    } else {
                        if (!walking) {
                            Walker.walkTo(Goal.reach(pos));
                            walking = true;
                        }
                        Walker.tick();
                        if (Walker.state() == Walker.State.FAILED) {
                            ChatUtil.error("[program] can't reach " + pos.toShortString());
                            Walker.stop();
                            return true;
                        }
                        if (Walker.state() == Walker.State.ARRIVED) {
                            arrived = true;
                            Walker.stop();
                        }
                        return false;
                    }
                }
                return then.tick();
            }

            public void cancel() {
                if (walking) Walker.stop();
                then.cancel();
            }
        };
    }

    static Task openContainer(BlockPos pos) {
        return reachThen(pos, new Task() {
            int t;
            boolean clicked;

            public boolean tick() {
                if (!clicked) {
                    Vec3d c = Vec3d.ofCenter(pos);
                    CombatUtil.lookAt(c);
                    Direction side = Direction.getFacing(mc.player.getEyePos().x - c.x, mc.player.getEyePos().y - c.y, mc.player.getEyePos().z - c.z);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(c, side, pos, false));
                    clicked = true;
                    return false;
                }
                return mc.player.currentScreenHandler != mc.player.playerScreenHandler && ++t > 3 || ++t > 60;
            }
        });
    }

    /** Shift-clicks matching stacks between a container and the inventory. */
    static int moveItems(boolean fromContainer, String query, int count) {
        ScreenHandler h = mc.player.currentScreenHandler;
        if (h == mc.player.playerScreenHandler) {
            ChatUtil.error("[program] no container is open");
            return 0;
        }
        int containerSlots = h.slots.size() - 36, moved = 0;
        int from = fromContainer ? 0 : containerSlots, to = fromContainer ? containerSlots : h.slots.size();
        for (int i = from; i < to && (count <= 0 || moved < count); i++) {
            ItemStack s = h.slots.get(i).getStack();
            if (!stackMatches(s, query)) continue;
            moved += s.getCount();
            mc.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
        }
        return moved;
    }

    static Module findModule(String name) {
        String n = name.toLowerCase(Locale.ROOT).replace(" ", "");
        for (Module m : MyTestClient.get().modules().all()) {
            if (m.getName().toLowerCase(Locale.ROOT).replace(" ", "").equals(n)) return m;
        }
        return null;
    }

    static boolean compare(double a, String op, double b) {
        return switch (op) {
            case ">" -> a > b;
            case "=" -> Math.abs(a - b) < 1e-9;
            case "≥" -> a >= b;
            case "≤" -> a <= b;
            case "≠" -> Math.abs(a - b) >= 1e-9;
            default -> a < b;
        };
    }

    static int countItem(String query) {
        int n = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) if (stackMatches(inv.getStack(i), query)) n += inv.getStack(i).getCount();
        if (stackMatches(mc.player.getOffHandStack(), query)) n += mc.player.getOffHandStack().getCount();
        return n;
    }

    static boolean blockIs(BlockPos pos, String query) {
        return idMatches(Registries.BLOCK.getId(mc.world.getBlockState(pos).getBlock()).toString(), query.isEmpty() ? "air" : query);
    }

    // ------------------------------------------------------------------ blocks

    static {
        Param dir4 = choice("forward", "back", "left", "right");
        Param keys = choice("forward", "back", "left", "right", "jump", "sneak", "sprint", "attack", "use");

        // ---------------- Motion
        block("move", MOTION, "move {dir} {n} blocks", p("dir", dir4, "n", num(5)), Shape.STACK, (r, b) -> {
            KeyBinding k = key(b.arg("dir"));
            Vec3d start = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            double n = r.num(b, "n");
            int[] still = {0};
            Vec3d[] last = {start};
            return until(() -> {
                Vec3d now = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                if (now.squaredDistanceTo(last[0]) < 1e-4) still[0]++;
                else still[0] = 0;
                last[0] = now;
                return Math.hypot(now.x - start.x, now.z - start.z) >= n - 0.3 || still[0] > 20;
            }, (int) (n * 20 + 60), () -> k.setPressed(true), () -> {
                k.setPressed(false);
                KeyBinding.updatePressedStates();
            });
        }, "Walks in a direction (relative to where you look) for a distance.");
        block("walk_to", MOTION, "walk to x {x} y {y} z {z}", p("x", coord("~10"), "y", coord("~"), "z", coord("~")), Shape.STACK, (r, b) -> {
            BlockPos target = BlockPos.ofFloored(r.coord(b, "x", 0), r.coord(b, "y", 1), r.coord(b, "z", 2));
            Walker.walkTo(Goal.near(target, 1.2));
            return until(() -> Walker.state() == Walker.State.ARRIVED || Walker.state() == Walker.State.FAILED, 20 * 60 * 10, Walker::tick, Walker::stop);
        }, "Pathfinds there (jumps, mines, bridges). ~ means relative to you, e.g. ~10.");
        block("walk_wp", MOTION, "walk to waypoint {name}", p("name", text("Home")), Shape.STACK, (r, b) -> {
            String name = r.text(b, "name");
            for (Waypoint wp : MyTestClient.get().waypoints().forCurrentWorld()) {
                if (wp.name.equalsIgnoreCase(name)) {
                    Walker.walkTo(Goal.near(new BlockPos(wp.x, wp.y, wp.z), 1.5));
                    return until(() -> Walker.state() == Walker.State.ARRIVED || Walker.state() == Walker.State.FAILED, 20 * 60 * 10, Walker::tick, Walker::stop);
                }
            }
            ChatUtil.error("[program] no waypoint called " + name);
            return null;
        }, "Pathfinds to one of your waypoints.");
        block("elytra_to", MOTION, "fly with elytra to x {x} z {z}", p("x", coord("~1000"), "z", coord("~")), Shape.STACK, (r, b) -> {
            Navigation.set(new Vec3d(r.coord(b, "x", 0), mc.player.getY(), r.coord(b, "z", 2)), "program target");
            String err = ElytraAutopilot.start();
            if (err != null) {
                ChatUtil.error("[program] elytra: " + err);
                return null;
            }
            return until(() -> !ElytraAutopilot.isActive(), 20 * 60 * 60, () -> {
            }, () -> ElytraAutopilot.stop("program stopped"));
        }, "Elytra autopilot to a place, waits until it lands.");
        block("jump", MOTION, "jump", p(), Shape.STACK, (r, b) -> {
            if (mc.player.isOnGround()) mc.player.jump();
            return waitTicks(2);
        }, "Jumps once.");
        block("hold_key", MOTION, "hold {key} for {n} ticks", p("key", keys, "n", num(20)), Shape.STACK, (r, b) -> {
            KeyBinding k = key(b.arg("key"));
            int n = (int) r.num(b, "n");
            return until(() -> false, n, () -> k.setPressed(true), () -> {
                k.setPressed(false);
                KeyBinding.updatePressedStates();
            });
        }, "Holds a key down for some ticks (20 ticks = 1 second).");
        block("set_key", MOTION, "set {key} {state}", p("key", keys, "state", choice("pressed", "released")), Shape.STACK, (r, b) -> {
            KeyBinding k = key(b.arg("key"));
            boolean on = b.arg("state").equals("pressed");
            k.setPressed(on);
            if (on) r.pressed.add(k);
            else r.pressed.remove(k);
            return null;
        }, "Keeps a key pressed (or releases it) until changed. Released when the program stops.");
        block("stop_moving", MOTION, "stop moving", p(), Shape.STACK, (r, b) -> {
            r.releaseKeys();
            Walker.stop();
            return null;
        }, "Releases every movement key.");

        // ---------------- Look
        block("look", LOOK, "look yaw {yaw} pitch {pitch}", p("yaw", num(0), "pitch", num(0)), Shape.STACK, (r, b) -> {
            mc.player.setYaw((float) r.num(b, "yaw"));
            mc.player.setPitch(MathHelper.clamp((float) r.num(b, "pitch"), -90, 90));
            return null;
        }, "Yaw: 0 south, 90 west, 180 north, -90 east. Pitch: -90 up, 90 down.");
        block("turn", LOOK, "turn {dir} {deg} degrees", p("dir", choice("right", "left", "up", "down"), "deg", num(90)), Shape.STACK, (r, b) -> {
            float d = (float) r.num(b, "deg");
            switch (b.arg("dir")) {
                case "left" -> mc.player.setYaw(mc.player.getYaw() - d);
                case "up" -> mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() - d, -90, 90));
                case "down" -> mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + d, -90, 90));
                default -> mc.player.setYaw(mc.player.getYaw() + d);
            }
            return null;
        }, "Turns the view.");
        block("face", LOOK, "face {dir}", p("dir", choice("north", "south", "east", "west")), Shape.STACK, (r, b) -> {
            mc.player.setYaw(switch (b.arg("dir")) {
                case "north" -> 180f;
                case "east" -> -90f;
                case "west" -> 90f;
                default -> 0f;
            });
            mc.player.setPitch(0);
            return null;
        }, "Looks straight towards a direction.");
        block("look_block", LOOK, "look at block x {x} y {y} z {z}", p("x", coord("~"), "y", coord("~"), "z", coord("~2")), Shape.STACK, (r, b) -> {
            CombatUtil.lookAt(Vec3d.ofCenter(BlockPos.ofFloored(r.coord(b, "x", 0), r.coord(b, "y", 1), r.coord(b, "z", 2))));
            return null;
        }, "Looks at the centre of a block.");
        block("look_entity", LOOK, "look at nearest {entity}", p("entity", entity("player")), Shape.STACK, (r, b) -> {
            Entity e = nearestEntity(r.text(b, "entity"), 64);
            if (e != null) CombatUtil.lookAt(CombatUtil.body(e));
            return null;
        }, "Looks at the closest player/mob of that type (player, monster, animal, minecraft:cow...).");

        // ---------------- Actions
        block("left_click", ACTIONS, "left click", p(), Shape.STACK, (r, b) -> {
            HitResult hit = mc.crosshairTarget;
            if (hit instanceof net.minecraft.util.hit.EntityHitResult eh) mc.interactionManager.attackEntity(mc.player, eh.getEntity());
            else if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) mc.interactionManager.attackBlock(bh.getBlockPos(), bh.getSide());
            mc.player.swingHand(Hand.MAIN_HAND);
            return waitTicks(1);
        }, "Attacks / starts mining what you look at.");
        block("right_click", ACTIONS, "right click", p(), Shape.STACK, (r, b) -> {
            HitResult hit = mc.crosshairTarget;
            if (hit instanceof net.minecraft.util.hit.EntityHitResult eh) mc.interactionManager.interactEntity(mc.player, eh.getEntity(), Hand.MAIN_HAND);
            else if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK
                    && mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bh).isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
            else mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            return waitTicks(1);
        }, "Uses what you look at (block, entity) or the item in your hand.");
        block("attack_nearest", ACTIONS, "attack nearest {entity} within {r} blocks", p("entity", entity("monster"), "r", num(4)), Shape.STACK, (r, b) -> {
            Entity e = nearestEntity(r.text(b, "entity"), r.num(b, "r"));
            if (e == null) return null;
            int slot = CombatUtil.bestWeaponSlot();
            if (slot >= 0) mc.player.getInventory().setSelectedSlot(slot);
            return until(() -> mc.player.getAttackCooldownProgress(0.5f) >= 1f, 30, () -> CombatUtil.lookAt(CombatUtil.body(e)), () -> {
                if (e.isAlive() && mc.player.distanceTo(e) <= r.num(b, "r") + 1) {
                    CombatUtil.lookAt(CombatUtil.body(e));
                    mc.interactionManager.attackEntity(mc.player, e);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            });
        }, "One full-strength hit on the closest target (best weapon). Put it in a loop to fight.");
        block("mine_at", ACTIONS, "mine block at x {x} y {y} z {z}", p("x", coord("~"), "y", coord("~-1"), "z", coord("~1")), Shape.STACK, (r, b) -> {
            BlockPos pos = BlockPos.ofFloored(r.coord(b, "x", 0), r.coord(b, "y", 1), r.coord(b, "z", 2));
            return reachThen(pos, until(() -> mc.world.getBlockState(pos).isAir() || !BlockInfo.breakable(pos), 600, () -> Walker.mine(pos), Walker::stopBreaking));
        }, "Walks next to the block if needed and breaks it with the best tool.");
        block("mine_front", ACTIONS, "mine block in front", p(), Shape.STACK, (r, b) -> {
            BlockPos pos = mc.crosshairTarget instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK ? bh.getBlockPos() : front();
            return until(() -> mc.world.getBlockState(pos).isAir() || !BlockInfo.breakable(pos), 600, () -> Walker.mine(pos), Walker::stopBreaking);
        }, "Breaks the block you look at (or the one in front of your feet).");
        block("mine_nearest", ACTIONS, "mine {n} nearest {block}", p("n", num(8), "block", blockP("diamond")), Shape.STACK, (r, b) -> {
            String q = r.text(b, "block");
            List<String> ids = Travel.resolveBlocks(q);
            if (ids.isEmpty()) {
                ChatUtil.error("[program] unknown block " + q);
                return null;
            }
            Travel.mine(ids, Math.max(1, (int) r.num(b, "n")), q);
            return until(() -> !Travel.isActive(), 20 * 60 * 30, () -> {
            }, () -> Travel.stop("program stopped"));
        }, "Finds, walks to, mines and collects blocks (same as \\mine).");
        block("place", ACTIONS, "place {item} {where}", p("item", item("cobblestone"), "where", choice("below", "in front", "above")), Shape.STACK, (r, b) -> {
            int slot = bringToHotbar(r.text(b, "item"));
            if (slot < 0) {
                ChatUtil.error("[program] no " + r.text(b, "item") + " in your inventory");
                return null;
            }
            BlockPos feet = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() + 0.2, mc.player.getZ());
            BlockPos pos = switch (b.arg("where")) {
                case "in front" -> feet.offset(mc.player.getHorizontalFacing());
                case "above" -> feet.up(2);
                default -> feet.down();
            };
            CombatUtil.place(pos, slot, true);
            return waitTicks(2);
        }, "Places a block from your inventory next to you.");
        block("use_at", ACTIONS, "right click block at x {x} y {y} z {z}", p("x", coord("~"), "y", coord("~"), "z", coord("~1")), Shape.STACK, (r, b) -> {
            BlockPos pos = BlockPos.ofFloored(r.coord(b, "x", 0), r.coord(b, "y", 1), r.coord(b, "z", 2));
            return reachThen(pos, new Task() {
                public boolean tick() {
                    Vec3d c = Vec3d.ofCenter(pos);
                    CombatUtil.lookAt(c);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(c, Direction.UP, pos, false));
                    mc.player.swingHand(Hand.MAIN_HAND);
                    return true;
                }
            });
        }, "Walks there if needed and uses the block (button, lever, door, bed...).");
        block("pickup", ACTIONS, "pick up items within {r} blocks", p("r", num(16)), Shape.STACK, (r, b) -> {
            Travel.pickup(null, r.num(b, "r"));
            return until(() -> !Travel.isActive(), 20 * 60 * 5, () -> {
            }, () -> Travel.stop("program stopped"));
        }, "Walks to the dropped items around you.");
        block("eat", ACTIONS, "eat food", p(), Shape.STACK, (r, b) -> {
            var inv = mc.player.getInventory();
            int slot = -1;
            for (int i = 0; i < 9 && slot < 0; i++) if (inv.getStack(i).get(DataComponentTypes.FOOD) != null) slot = i;
            if (slot < 0) {
                ChatUtil.error("[program] no food in your hotbar");
                return null;
            }
            inv.setSelectedSlot(slot);
            int start = mc.player.getHungerManager().getFoodLevel();
            return until(() -> mc.player.getHungerManager().getFoodLevel() > start || mc.player.getMainHandStack().get(DataComponentTypes.FOOD) == null,
                    60, () -> mc.options.useKey.setPressed(true), () -> {
                        mc.options.useKey.setPressed(false);
                        KeyBinding.updatePressedStates();
                    });
        }, "Eats something from the hotbar.");
        block("drop", ACTIONS, "drop {mode} {item}", p("mode", choice("one", "stack", "all"), "item", item("cobblestone")), Shape.STACK, (r, b) -> {
            String q = r.text(b, "item");
            var inv = mc.player.getInventory();
            int sync = mc.player.playerScreenHandler.syncId;
            for (int i = 0; i < 36; i++) {
                if (!stackMatches(inv.getStack(i), q)) continue;
                int screenSlot = i < 9 ? 36 + i : i;
                mc.interactionManager.clickSlot(sync, screenSlot, b.arg("mode").equals("one") ? 0 : 1, SlotActionType.THROW, mc.player);
                if (!b.arg("mode").equals("all")) break;
            }
            return waitTicks(1);
        }, "Throws items out of your inventory.");

        // ---------------- Inventory
        block("select_slot", INVENTORY, "select hotbar slot {n}", p("n", num(1)), Shape.STACK, (r, b) -> {
            mc.player.getInventory().setSelectedSlot(MathHelper.clamp((int) r.num(b, "n") - 1, 0, 8));
            return null;
        }, "Selects a hotbar slot (1-9).");
        block("hold_item", INVENTORY, "hold {item}", p("item", item("diamond_pickaxe")), Shape.STACK, (r, b) -> {
            int slot = bringToHotbar(r.text(b, "item"));
            if (slot >= 0) mc.player.getInventory().setSelectedSlot(slot);
            else ChatUtil.error("[program] no " + r.text(b, "item") + " in your inventory");
            return waitTicks(1);
        }, "Puts an item in your hand (from the hotbar or the inventory).");
        block("open_nearest", INVENTORY, "open nearest {block}", p("block", blockP("orange_shulker_box")), Shape.STACK, (r, b) -> {
            String q = r.text(b, "block");
            BlockPos pos = Travel.nearest(blk -> idMatches(Registries.BLOCK.getId(blk).toString(), q), java.util.Set.of(), 1);
            if (pos == null) {
                ChatUtil.error("[program] no " + q + " nearby");
                return null;
            }
            return openContainer(pos);
        }, "Walks to the closest block of that type (chest, barrel, orange_shulker_box...) and opens it.");
        block("open_at", INVENTORY, "open container at x {x} y {y} z {z}", p("x", coord("~"), "y", coord("~"), "z", coord("~1")), Shape.STACK,
                (r, b) -> openContainer(BlockPos.ofFloored(r.coord(b, "x", 0), r.coord(b, "y", 1), r.coord(b, "z", 2))),
                "Walks to a container and opens it.");
        block("take", INVENTORY, "take {n} {item} from container", p("n", num(64), "item", item("any")), Shape.STACK, (r, b) -> {
            moveItems(true, r.text(b, "item"), (int) r.num(b, "n"));
            return waitTicks(2);
        }, "Shift-clicks matching stacks out of the open container (0 = all, any = every item).");
        block("put", INVENTORY, "put {n} {item} in container", p("n", num(0), "item", item("cobblestone")), Shape.STACK, (r, b) -> {
            moveItems(false, r.text(b, "item"), (int) r.num(b, "n"));
            return waitTicks(2);
        }, "Shift-clicks matching stacks from your inventory into the open container (0 = all).");
        block("close", INVENTORY, "close container", p(), Shape.STACK, (r, b) -> {
            if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) mc.player.closeHandledScreen();
            return waitTicks(1);
        }, "Closes the open chest/shulker/furnace...");
        block("offhand", INVENTORY, "move held item to offhand", p(), Shape.STACK, (r, b) -> {
            int sel = mc.player.getInventory().getSelectedSlot();
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, 36 + sel, 40, SlotActionType.SWAP, mc.player);
            return waitTicks(1);
        }, "Swaps the item in your hand with the offhand.");

        // ---------------- Chat
        block("say", CHAT, "say {text}", p("text", text("Hello!")), Shape.STACK, (r, b) -> {
            mc.player.networkHandler.sendChatMessage(r.text(b, "text"));
            return waitTicks(1);
        }, "Sends a chat message. Use {x} {y} {z} {health} {var:name} in the text.");
        block("command", CHAT, "run command /{text}", p("text", text("home")), Shape.STACK, (r, b) -> {
            String c = r.text(b, "text");
            mc.player.networkHandler.sendChatCommand(c.startsWith("/") ? c.substring(1) : c);
            return waitTicks(1);
        }, "Runs a server command.");
        block("client_cmd", CHAT, "run VoidClient command \\{text}", p("text", text("coords")), Shape.STACK, (r, b) -> {
            String c = r.text(b, "text");
            CommandManager.handle(c.startsWith("\\") ? c : "\\" + c);
            return null;
        }, "Runs one of the \\ commands (\\goto, \\set, \\toggle...).");
        block("notify", CHAT, "show message {text}", p("text", text("Done!")), Shape.STACK, (r, b) -> {
            ChatUtil.info("[" + r.program.name + "] " + r.text(b, "text"));
            return null;
        }, "Shows a message only to you.");

        // ---------------- Control
        block("wait", CONTROL, "wait {n} seconds", p("n", num(1)), Shape.STACK, (r, b) -> waitTicks((int) Math.round(r.num(b, "n") * 20)), "Pauses.");
        block("wait_ticks", CONTROL, "wait {n} ticks", p("n", num(10)), Shape.STACK, (r, b) -> waitTicks((int) r.num(b, "n")), "Pauses (20 ticks = 1 second).");
        block("repeat", CONTROL, "repeat {n} times", p("n", num(10)), Shape.C, null, "Runs the blocks inside several times.");
        block("forever", CONTROL, "forever", p(), Shape.C, null, "Runs the blocks inside until the program is stopped.");
        block("if", CONTROL, "if {cond} then", p("cond", kind(Kind.COND, "health")), Shape.C, null, "Runs the blocks inside only if the condition is true.");
        block("if_else", CONTROL, "if {cond} then", p("cond", kind(Kind.COND, "health")), Shape.IF_ELSE, null, "One branch or the other.");
        block("until", CONTROL, "repeat until {cond}", p("cond", kind(Kind.COND, "inv_full")), Shape.C, null, "Loops until the condition becomes true.");
        block("while", CONTROL, "while {cond}", p("cond", kind(Kind.COND, "on_ground")), Shape.C, null, "Loops as long as the condition is true.");
        block("wait_until", CONTROL, "wait until {cond}", p("cond", kind(Kind.COND, "chat")), Shape.STACK,
                (r, b) -> until(() -> r.test(b), Integer.MAX_VALUE, () -> {
                }, () -> {
                }), "Pauses until the condition is true.");
        block("run_program", CONTROL, "run program {program} and wait", p("program", kind(Kind.PROGRAM, "")), Shape.STACK, (r, b) -> {
            String name = b.arg("program");
            if (name.equalsIgnoreCase(r.program.name)) return null;
            if (!ProgramManager.start(name, false)) return null;
            return until(() -> !ProgramManager.isRunning(name), Integer.MAX_VALUE, () -> {
            }, () -> ProgramManager.stop(name, false));
        }, "Runs another program and waits for it to end.");
        block("stop", CONTROL, "stop this program", p(), Shape.CAP, null, "Ends the program here.");
        block("stop_all", CONTROL, "stop all programs", p(), Shape.CAP, (r, b) -> {
            ProgramManager.stopAll();
            return null;
        }, "Stops every running program.");

        // ---------------- Variables
        block("set_var", VARIABLES, "set {var} to {value}", p("var", kind(Kind.VAR, "count"), "value", num(0)), Shape.STACK, (r, b) -> {
            r.vars.put(b.arg("var"), r.num(b, "value"));
            return null;
        }, "Stores a number. Use it in conditions or as {var:name} in texts.");
        block("change_var", VARIABLES, "change {var} by {value}", p("var", kind(Kind.VAR, "count"), "value", num(1)), Shape.STACK, (r, b) -> {
            r.vars.merge(b.arg("var"), r.num(b, "value"), Double::sum);
            return null;
        }, "Adds to a number (negative to subtract).");
        block("store_pos", VARIABLES, "remember my position as {var}", p("var", kind(Kind.VAR, "home")), Shape.STACK, (r, b) -> {
            String v = b.arg("var");
            r.vars.put(v + ".x", Math.floor(mc.player.getX()));
            r.vars.put(v + ".y", Math.floor(mc.player.getY()));
            r.vars.put(v + ".z", Math.floor(mc.player.getZ()));
            return null;
        }, "Saves x/y/z in {var:name.x} {var:name.y} {var:name.z}; coordinates can use them too.");

        // ---------------- Modules
        block("module", MODULES, "turn {module} {state}", p("module", kind(Kind.MODULE, "Kill Aura"), "state", choice("on", "off", "toggle")), Shape.STACK, (r, b) -> {
            Module m = findModule(b.arg("module"));
            if (m == null) {
                ChatUtil.error("[program] no module " + b.arg("module"));
                return null;
            }
            switch (b.arg("state")) {
                case "on" -> m.setEnabled(true);
                case "off" -> m.setEnabled(false);
                default -> m.toggle();
            }
            return null;
        }, "Turns a VoidClient module on/off.");
        block("setting", MODULES, "set {module} setting {setting} to {value}", p("module", kind(Kind.MODULE, "Elytra Fly"), "setting", text("Max Speed"), "value", text("60")), Shape.STACK, (r, b) -> {
            CommandManager.handle("\\set " + b.arg("module").replace(" ", "") + " " + r.text(b, "setting").replace(" ", "") + " " + r.text(b, "value"));
            return null;
        }, "Changes a module setting (like \\set).");

        // ---------------- conditions
        Param op = choice("<", ">", "=", "≥", "≤", "≠");
        cond("health", "health {op} {n}", p("op", op, "n", num(10)), (r, b) -> compare(mc.player.getHealth(), b.arg("c.op"), r.num(b, "c.n")));
        cond("hunger", "hunger {op} {n}", p("op", op, "n", num(10)), (r, b) -> compare(mc.player.getHungerManager().getFoodLevel(), b.arg("c.op"), r.num(b, "c.n")));
        cond("coord", "my {axis} {op} {n}", p("axis", choice("y", "x", "z"), "op", op, "n", num(64)), (r, b) -> {
            double v = switch (b.arg("c.axis")) {
                case "x" -> mc.player.getX();
                case "z" -> mc.player.getZ();
                default -> mc.player.getY();
            };
            return compare(v, b.arg("c.op"), r.num(b, "c.n"));
        });
        cond("var", "{var} {op} {n}", p("var", kind(Kind.VAR, "count"), "op", op, "n", num(10)),
                (r, b) -> compare(r.vars.getOrDefault(b.arg("c.var"), 0.0), b.arg("c.op"), r.num(b, "c.n")));
        cond("item_count", "count of {item} {op} {n}", p("item", item("cobblestone"), "op", choice(">", "<", "=", "≥", "≤", "≠"), "n", num(0)),
                (r, b) -> compare(countItem(r.text(b, "c.item")), b.arg("c.op"), r.num(b, "c.n")));
        cond("has_item", "I have {item}", p("item", item("ender_pearl")), (r, b) -> countItem(r.text(b, "c.item")) > 0);
        cond("inv_full", "inventory is full", p(), (r, b) -> mc.player.getInventory().getEmptySlot() < 0);
        cond("holding", "holding {item}", p("item", item("diamond_sword")), (r, b) -> stackMatches(mc.player.getMainHandStack(), r.text(b, "c.item")));
        cond("block_at", "block at x {x} y {y} z {z} is {block}", p("x", coord("~"), "y", coord("~-1"), "z", coord("~"), "block", blockP("air")),
                (r, b) -> blockIs(BlockPos.ofFloored(r.coord(b, "c.x", 0), r.coord(b, "c.y", 1), r.coord(b, "c.z", 2)), r.text(b, "c.block")));
        cond("block_front", "block in front is {block}", p("block", blockP("air")), (r, b) -> blockIs(front(), r.text(b, "c.block")));
        cond("block_below", "block below is {block}", p("block", blockP("air")),
                (r, b) -> blockIs(BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - 0.2, mc.player.getZ()), r.text(b, "c.block")));
        cond("looking_at", "looking at {block}", p("block", blockP("stone")),
                (r, b) -> mc.crosshairTarget instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK && blockIs(bh.getBlockPos(), r.text(b, "c.block")));
        cond("entity_near", "{entity} within {r} blocks", p("entity", entity("monster"), "r", num(8)), (r, b) -> nearestEntity(r.text(b, "c.entity"), r.num(b, "c.r")) != null);
        cond("player_near", "a player within {r} blocks", p("r", num(32)), (r, b) -> nearestEntity("player", r.num(b, "c.r")) != null);
        cond("at_pos", "within {r} blocks of x {x} z {z}", p("r", num(2), "x", coord("0"), "z", coord("0")),
                (r, b) -> Math.hypot(mc.player.getX() - r.coord(b, "c.x", 0), mc.player.getZ() - r.coord(b, "c.z", 2)) <= r.num(b, "c.r"));
        cond("on_ground", "on the ground", p(), (r, b) -> mc.player.isOnGround());
        cond("in_water", "in water", p(), (r, b) -> mc.player.isTouchingWater());
        cond("gliding", "flying with elytra", p(), (r, b) -> mc.player.isGliding());
        cond("day", "it is day", p(), (r, b) -> {
            long t = mc.world.getTimeOfDay() % 24000;
            return t < 12300 || t > 23850;
        });
        cond("container", "a container is open", p(), (r, b) -> mc.player.currentScreenHandler != mc.player.playerScreenHandler);
        cond("chat", "chat says {text}", p("text", text("hello")), (r, b) -> r.consumeChat(r.text(b, "c.text")));
        cond("module_on", "{module} is on", p("module", kind(Kind.MODULE, "Kill Aura")), (r, b) -> {
            Module m = findModule(b.arg("c.module"));
            return m != null && m.isEnabled();
        });
        cond("walking", "pathfinder is busy", p(), (r, b) -> Walker.state() == Walker.State.SEARCHING || Walker.state() == Walker.State.WALKING || Travel.isActive());
        cond("chance", "random {n}% chance", p("n", num(50)), (r, b) -> Math.random() * 100 < r.num(b, "c.n"));
        cond("always", "always", p(), (r, b) -> true);
    }

    /** Player helper for the editor: all block ids of a registry matching a query. */
    public static List<String> suggestions(Kind kind, String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<String> out = new ArrayList<>();
        if (kind == Kind.ENTITY) for (String s : List.of("player", "monster", "animal", "any")) if (s.contains(q)) out.add(s);
        var ids = switch (kind) {
            case ITEM -> Registries.ITEM.getIds();
            case BLOCK -> Registries.BLOCK.getIds();
            case ENTITY -> Registries.ENTITY_TYPE.getIds();
            default -> java.util.Set.<net.minecraft.util.Identifier>of();
        };
        for (var id : ids) {
            if (out.size() >= 40) break;
            if (id.getPath().contains(q.replace(' ', '_'))) out.add(id.getPath());
        }
        out.sort((a, b2) -> Integer.compare(a.length(), b2.length()));
        return out;
    }

    /** Module names for the dropdown. */
    public static List<String> moduleNames() {
        List<String> out = new ArrayList<>();
        for (Module m : MyTestClient.get().modules().all()) out.add(m.getName());
        return out;
    }

    static ClientPlayerEntity player() {
        return mc.player;
    }
}
