package com.mytestclient.util;

import com.mytestclient.gui.Theme;
import com.mytestclient.module.BindMode;
import com.mytestclient.module.Module;
import com.mytestclient.module.Modules;
import com.mytestclient.module.misc.ClickGuiModule;
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
        int y = mc.getWindow().getScaledHeight() - 8;
        long now = System.currentTimeMillis();
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            Entry e = ENTRIES.get(i);
            long age = now - e.start;
            // slide in during the first 150ms, out during the last 250ms
            float t = Math.min(1f, age / 150f);
            if (age > DURATION_MS - 250) t = Math.max(0f, (DURATION_MS - age) / 250f);
            int w = tr.getWidth(e.text) + 16;
            int h = 18;
            int x = sw - 8 - (int) (w * easeOut(t));
            y -= h + 4;
            Render2D.roundedRect(ctx, x, y, w + 8, h, 5, Theme.BG);
            Render2D.roundedRect(ctx, x, y, 3, h, 1, e.color);
            ctx.drawText(tr, e.text, x + 9, y + 5, Theme.TEXT, false);
        }
    }

    private static float easeOut(float t) {
        return 1f - (1f - t) * (1f - t);
    }
}
