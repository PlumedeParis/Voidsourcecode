package com.mytestclient.util;

import com.mytestclient.gui.Theme;
import com.mytestclient.module.BindMode;
import com.mytestclient.module.Module;
import com.mytestclient.module.Modules;
import com.mytestclient.module.misc.ClickGuiModule;
import com.mytestclient.render.Colors;
import com.mytestclient.render.Render2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/** Small toast-like messages in the bottom-right corner of the HUD. */
public final class Notifications {
    private static final long DURATION_MS = 2600;
    private static final int MAX = 5;
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private record Entry(String text, int color, long start) {
    }

    private Notifications() {
    }

    public static void info(String text) {
        push(text, Theme.accent());
    }

    public static void warn(String text) {
        push(text, 0xFFFFC857);
    }

    public static void moduleToggled(Module module) {
        ClickGuiModule gui = Modules.get(ClickGuiModule.class);
        if (gui != null && !gui.notifications.get()) return;
        if (module.getBindMode() == BindMode.HOLD) return;
        push(module.getName() + (module.isEnabled() ? " enabled" : " disabled"),
                module.isEnabled() ? 0xFF4ADE80 : 0xFFEF4444);
    }

    private static void push(String text, int color) {
        ENTRIES.add(new Entry(text, color, System.currentTimeMillis()));
        while (ENTRIES.size() > MAX) ENTRIES.remove(0);
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        ENTRIES.removeIf(e -> now - e.start > DURATION_MS);
    }

    public static void render(DrawContext ctx) {
        if (ENTRIES.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();
        int y = mc.getWindow().getScaledHeight() - 6;
        long now = System.currentTimeMillis();
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            Entry e = ENTRIES.get(i);
            long age = now - e.start;
            // slide in during the first 180ms, out during the last 250ms
            float t = Math.min(1f, age / 180f);
            if (age > DURATION_MS - 250) t = Math.max(0f, (DURATION_MS - age) / 250f);
            float k = easeOut(t);
            String text = e.text;
            int maxW = Math.max(60, sw / 2);
            if (tr.getWidth(text) > maxW) text = tr.trimToWidth(text, maxW - tr.getWidth("...")) + "...";
            int w = tr.getWidth(text) + 22, h = 18;
            int x = sw - Math.round((w + 6) * k); // slides in from the right edge, 6px margin
            y -= Math.round((h + 4) * Math.max(k, 0.0001f));
            int alpha = Math.round(255 * k);
            if (alpha <= 4) continue;
            Render2D.shadow(ctx, x, y, w, h, 5, 4, (int) (0x90 * k));
            Render2D.roundedRect(ctx, x, y, w, h, 5, Colors.fade(0xF2101019, k));
            Render2D.roundedOutline(ctx, x, y, w, h, 5, Colors.fade(Colors.withAlpha(e.color, 0x50), k));
            Render2D.circle(ctx, x + 8, y + h / 2, 2, Colors.fade(e.color, k));
            ctx.drawText(tr, text, x + 14, y + 5, Colors.fade(Theme.TEXT, k), false);
            // remaining time
            float left = 1f - Math.min(1f, age / (float) DURATION_MS);
            int barW = Math.round((w - 10) * left);
            if (barW > 0) ctx.fill(x + 5, y + h - 2, x + 5 + barW, y + h - 1, Colors.fade(Colors.withAlpha(e.color, 0x90), k));
        }
    }

    private static float easeOut(float t) {
        return 1f - (1f - t) * (1f - t);
    }
}
