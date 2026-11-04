package com.mytestclient.module.combat;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.util.ChatUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Death Recap: a breakdown of what killed you, like the recap screen of a MOBA.
 * <p>
 * The server tells the client about every hit you take (damage type, who caused it, what hit
 * you) but not how much it took. This module joins the two: each hit is matched with the drop
 * in health + absorption that follows it. Totem pops are recorded too. When you die (or pop a
 * totem, if enabled) it prints who did how much damage, how, and the last hits in order.
 * <p>
 * Read-only: nothing is sent to the server.
 */
public class DeathRecapModule extends Module {
    private final NumberSetting window = add(new NumberSetting("Window", "How many seconds before death to include.", 15, 3, 60, 1));
    private final BooleanSetting onTotem = add(new BooleanSetting("On Totem Pop", "Also print a short recap every time a totem saves you.", true));
    private final NumberSetting maxLines = add(new NumberSetting("Hit Lines", "How many individual hits to list.", 6, 0, 20, 1));

    private static final class Hit {
        long time;
        String type;
        String attacker;
        String direct;
        float amount = -1; // unknown until the health update arrives
    }

    private final Deque<Hit> hits = new ArrayDeque<>();
    private final Deque<Long> pops = new ArrayDeque<>();
    private float lastTotal = -1;
    private boolean reported;
    private int totemRecapIn = -1;

    public DeathRecapModule() {
        super("Death Recap", "Tells you exactly what killed you: attackers, damage and totem pops.", Category.COMBAT);
    }

    @Override
    protected void onDisable() {
        hits.clear();
        pops.clear();
        lastTotal = -1;
    }

    @Override
    public void onWorldLeave() {
        onDisable();
    }

    /** From the network handler mixin, on the client thread: a damage event for any entity. */
    public void onDamage(int entityId, RegistryEntry<DamageType> type, int causeId, int directId) {
        if (mc.player == null || entityId != mc.player.getId()) return;
        Hit h = new Hit();
        h.time = System.currentTimeMillis();
        h.type = type.getKey().map(k -> pretty(k.getValue().getPath())).orElse("Damage");
        h.attacker = name(causeId);
        h.direct = name(directId);
        if (h.direct != null && h.direct.equals(h.attacker)) h.direct = null;
        hits.addLast(h);
        while (hits.size() > 200) hits.removeFirst();
    }

    /** From the network handler mixin: entity status 35 on you = a totem popped. */
    public void onTotemPop() {
        long now = System.currentTimeMillis();
        pops.addLast(now);
        // the health update that tells how much the last hit took arrives a moment later
        if (onTotem.get() && isEnabled()) totemRecapIn = 10;
    }

    private String name(int id) {
        if (id < 0 || mc.world == null) return null;
        Entity e = mc.world.getEntityById(id);
        return e == null ? null : e.getName().getString();
    }

    @Override
    public void onTick() {
        float total = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (lastTotal >= 0 && total < lastTotal - 0.01f) {
            float lost = lastTotal - total;
            Hit target = null;
            for (Iterator<Hit> it = hits.descendingIterator(); it.hasNext(); ) {
                Hit h = it.next();
                if (System.currentTimeMillis() - h.time > 1500) break;
                if (h.amount < 0) target = h; // oldest unassigned recent hit
            }
            if (target == null) {
                target = new Hit();
                target.time = System.currentTimeMillis();
                target.type = mc.player.isOnFire() ? "Burning" : "Unknown";
                hits.addLast(target);
            }
            target.amount = lost;
        }
        lastTotal = total;
        if (totemRecapIn >= 0 && totemRecapIn-- == 0) recap("Totem popped", 5, Math.min(4, maxLines.getInt()));

        if (mc.player.isDead() || mc.player.getHealth() <= 0) {
            if (!reported) {
                reported = true;
                recap("Death recap", window.getInt(), maxLines.getInt());
            }
        } else if (reported) {
            reported = false; // respawned
            hits.clear();
            pops.clear();
        }
        long cutoff = System.currentTimeMillis() - 60_000;
        while (!hits.isEmpty() && hits.peekFirst().time < cutoff) hits.removeFirst();
        while (!pops.isEmpty() && pops.peekFirst() < cutoff) pops.removeFirst();
    }

    /** Prints the summary of the hits taken in the last {@code seconds}. */
    private void recap(String title, int seconds, int lines) {
        long end = System.currentTimeMillis(), start = end - seconds * 1000L;
        List<Hit> recent = new ArrayList<>();
        for (Hit h : hits) if (h.time >= start) recent.add(h);
        int popCount = 0;
        for (long p : pops) if (p >= start) popCount++;
        if (recent.isEmpty()) {
            ChatUtil.info(title + ": no hits recorded in the last " + seconds + "s.");
            return;
        }

        // who did what: attacker (or damage type when there is none) -> total, ways
        Map<String, float[]> byCulprit = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> ways = new LinkedHashMap<>();
        float total = 0;
        for (Hit h : recent) {
            float amount = Math.max(0, h.amount);
            total += amount;
            String culprit = h.attacker != null ? h.attacker : h.type;
            byCulprit.computeIfAbsent(culprit, k -> new float[1])[0] += amount;
            if (h.attacker != null) ways.computeIfAbsent(culprit, k -> new LinkedHashMap<>()).merge(way(h), 1, Integer::sum);
        }
        List<Map.Entry<String, float[]>> sorted = new ArrayList<>(byCulprit.entrySet());
        sorted.sort((a, b) -> Float.compare(b.getValue()[0], a.getValue()[0]));

        ChatUtil.send(Text.literal("[MTC] ").formatted(Formatting.AQUA)
                .append(Text.literal(title).formatted(Formatting.RED, Formatting.BOLD))
                .append(Text.literal(String.format(Locale.ROOT, "  %.1f ❤ in %.1fs, %d hit%s%s", total / 2, (end - recent.get(0).time) / 1000f,
                        recent.size(), recent.size() == 1 ? "" : "s", popCount > 0 ? ", " + popCount + " totem" + (popCount == 1 ? "" : "s") + " popped" : ""))
                        .formatted(Formatting.GRAY)));
        for (Map.Entry<String, float[]> e : sorted) {
            float dmg = e.getValue()[0];
            int pct = total > 0 ? Math.round(dmg * 100 / total) : 0;
            MutableText line = Text.literal("  " + e.getKey()).formatted(Formatting.WHITE)
                    .append(Text.literal(String.format(Locale.ROOT, "  %.1f ❤ (%d%%)", dmg / 2, pct)).formatted(Formatting.RED));
            Map<String, Integer> w = ways.get(e.getKey());
            if (w != null) {
                StringBuilder sb = new StringBuilder("  via ");
                boolean first = true;
                for (Map.Entry<String, Integer> way : w.entrySet()) {
                    if (!first) sb.append(", ");
                    sb.append(way.getKey()).append(way.getValue() > 1 ? " ×" + way.getValue() : "");
                    first = false;
                }
                line.append(Text.literal(sb.toString()).formatted(Formatting.DARK_GRAY));
            }
            ChatUtil.send(line);
        }
        for (int i = Math.max(0, recent.size() - lines); i < recent.size(); i++) {
            Hit h = recent.get(i);
            String amount = h.amount < 0 ? "  ?" : String.format(Locale.ROOT, "-%.1f", h.amount / 2);
            ChatUtil.send(Text.literal(String.format(Locale.ROOT, "    %5.1fs  ", (end - h.time) / 1000f)).formatted(Formatting.DARK_GRAY)
                    .append(Text.literal(amount + " ❤ ").formatted(Formatting.RED))
                    .append(Text.literal(way(h) + (h.attacker != null ? " by " + h.attacker : "")).formatted(Formatting.GRAY)));
        }
    }

    /** "Explosion (End Crystal)", "Arrow"... */
    private static String way(Hit h) {
        return h.direct != null ? h.type + " (" + h.direct + ")" : h.type;
    }

    /** "player_explosion" -> "Player Explosion". */
    private static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
