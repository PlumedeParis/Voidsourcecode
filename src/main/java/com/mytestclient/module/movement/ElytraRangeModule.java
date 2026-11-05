package com.mytestclient.module.movement;

import com.mytestclient.gui.Theme;
import com.mytestclient.hud.HudModule;
import com.mytestclient.module.Category;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.util.Navigation;
import com.mytestclient.util.Notifications;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Elytra Range: how far you can still fly, before you need to land.
 * <p>
 * An elytra loses 1 durability per second of gliding and stops working at 1. Unbreaking makes
 * each of those losses happen only 60% + 40% / (level + 1) of the time. From that, the worn
 * elytra and every spare one in your inventory, this HUD shows your remaining flight time, the
 * distance it covers at your current speed (or a cruise speed while you are on the ground) and
 * your rockets. With a destination set ({@code \goto}, Seed Map...) it also tells you whether
 * you will make it, how many elytra swaps that needs, or how far short you will land.
 */
public class ElytraRangeModule extends HudModule {
    private final NumberSetting cruise = add(new NumberSetting("Cruise Speed", "Speed used for the range while you are not flying (blocks/s). Rocket flight is ~30, \\elytra autopilot ~40.", 33, 5, 120, 1));
    private final BooleanSetting warn = add(new BooleanSetting("Swap Warning", "Notify you when the worn elytra has less than a minute of flight left.", true));

    private final double[] speeds = new double[40];
    private int speedIndex, speedCount;
    private Vec3d lastPos;
    private boolean warned;

    public ElytraRangeModule() {
        super("Elytra Range", "How far your elytras and rockets can still take you, and if you will reach your destination.", Category.MOVEMENT, 0, 62, true);
    }

    /** Average horizontal speed over the last 2 seconds of gliding, or -1. */
    private double flightSpeed() {
        if (speedCount < 10) return -1;
        double sum = 0;
        for (int i = 0; i < speedCount; i++) sum += speeds[i];
        return sum / speedCount * 20;
    }

    @Override
    public void onTick() {
        Vec3d pos = mc.player.getEntityPos();
        if (mc.player.isGliding() && lastPos != null) {
            double d = Math.hypot(pos.x - lastPos.x, pos.z - lastPos.z);
            if (d < 20) { // not a teleport
                speeds[speedIndex] = d;
                speedIndex = (speedIndex + 1) % speeds.length;
                speedCount = Math.min(speeds.length, speedCount + 1);
            }
        } else if (!mc.player.isGliding()) {
            speedCount = 0;
        }
        lastPos = pos;

        ItemStack worn = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        double left = worn.isOf(Items.ELYTRA) ? seconds(worn) : 0;
        if (warn.get() && mc.player.isGliding() && worn.isOf(Items.ELYTRA)) {
            if (left < 60 && !warned) {
                warned = true;
                Notifications.warn("Elytra: " + time(left) + " of flight left, swap soon");
            } else if (left >= 60) warned = false;
        }
    }

    /** Expected seconds of gliding left in one elytra (Unbreaking included). */
    private double seconds(ItemStack stack) {
        int usable = stack.getMaxDamage() - stack.getDamage() - 1;
        if (usable <= 0) return 0;
        int level = 0;
        if (mc.world != null) {
            level = mc.world.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT)
                    .flatMap(r -> r.getEntry(Enchantments.UNBREAKING.getValue()))
                    .map(e -> EnchantmentHelper.getLevel(e, stack)).orElse(0);
        }
        double chance = 0.6 + 0.4 / (level + 1);
        return usable / chance;
    }

    private static String time(double seconds) {
        long s = Math.round(seconds);
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }

    private static String dist(double blocks) {
        return blocks >= 1000 ? String.format(Locale.ROOT, "%.1f km", blocks / 1000) : Math.round(blocks) + " m";
    }

    @Override
    protected void render(DrawContext ctx, int x, int y, float tickDelta) {
        List<Line> lines = new ArrayList<>();
        ItemStack worn = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        boolean wearing = worn.isOf(Items.ELYTRA);
        double wornSeconds = wearing ? seconds(worn) : 0;

        double spareSeconds = 0;
        int spares = 0, rockets = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s == worn) continue;
            if (s.isOf(Items.ELYTRA) && s.getMaxDamage() - s.getDamage() > 1) {
                spares++;
                spareSeconds += seconds(s);
            } else if (s.isOf(Items.FIREWORK_ROCKET)) rockets += s.getCount();
        }
        if (!wearing && spares == 0) {
            lines.add(new Line("Elytra", "none", Theme.TEXT_MUTED));
            drawLines(ctx, x, y, lines);
            return;
        }

        if (wearing) {
            int pct = Math.round(100f * (worn.getMaxDamage() - worn.getDamage()) / worn.getMaxDamage());
            int color = wornSeconds < 60 ? Theme.BAD : wornSeconds < 300 ? Theme.WARN : Theme.TEXT;
            lines.add(new Line("Elytra", time(wornSeconds) + "  (" + pct + "%)", color));
        } else {
            lines.add(new Line("Elytra", "not worn", Theme.WARN));
        }
        if (spares > 0) lines.add(new Line("Spares", spares + "  +" + time(spareSeconds), Theme.TEXT_DIM));

        double measured = flightSpeed();
        double speed = measured > 1 ? measured : cruise.get();
        double total = wornSeconds + spareSeconds;
        lines.add(new Line("Range", dist(total * speed) + String.format(Locale.ROOT, "  @ %.0f b/s", speed), measured > 1 ? Theme.TEXT : Theme.TEXT_DIM));
        lines.add(new Line("Rockets", String.valueOf(rockets), rockets == 0 ? Theme.BAD : rockets < 16 ? Theme.WARN : Theme.TEXT));

        Vec3d target = Navigation.target();
        if (target != null) {
            double d = Math.hypot(target.x - mc.player.getX(), target.z - mc.player.getZ());
            double needed = d / speed;
            String verdict;
            int color;
            if (needed <= wornSeconds) {
                int arrive = (int) Math.round(100 * (wornSeconds - needed) / Math.max(1, wornSeconds));
                verdict = "✔ " + dist(d) + ", lands with " + time(wornSeconds - needed);
                color = arrive > 20 ? Theme.GOOD : Theme.WARN;
            } else if (needed <= total) {
                int swaps = 0;
                double acc = wornSeconds;
                List<Double> each = new ArrayList<>();
                for (int i = 0; i < mc.player.getInventory().size(); i++) {
                    ItemStack s = mc.player.getInventory().getStack(i);
                    if (s != worn && s.isOf(Items.ELYTRA) && s.getMaxDamage() - s.getDamage() > 1) each.add(seconds(s));
                }
                each.sort((a, b) -> Double.compare(b, a));
                for (double e : each) {
                    if (acc >= needed) break;
                    acc += e;
                    swaps++;
                }
                verdict = "✔ " + dist(d) + " with " + swaps + " swap" + (swaps == 1 ? "" : "s");
                color = Theme.WARN;
            } else {
                verdict = "✘ " + dist(d) + ", " + dist((needed - total) * speed) + " short";
                color = Theme.BAD;
            }
            lines.add(new Line("Target", verdict, color));
        }
        drawLines(ctx, x, y, lines);
    }
}
