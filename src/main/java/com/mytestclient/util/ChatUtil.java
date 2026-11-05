package com.mytestclient.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Client-only chat messages (never sent to the server). */
public final class ChatUtil {
    private ChatUtil() {
    }

    private static MutableText prefix() {
        return Text.literal("[MTC] ").formatted(Formatting.AQUA);
    }

    public static void info(String message) {
        send(prefix().append(Text.literal(message).formatted(Formatting.GRAY)));
    }

    public static void success(String message) {
        send(prefix().append(Text.literal(message).formatted(Formatting.GREEN)));
    }

    public static void error(String message) {
        send(prefix().append(Text.literal(message).formatted(Formatting.RED)));
    }

    public static void send(Text text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) mc.inGameHud.getChatHud().addMessage(text);
    }
}
