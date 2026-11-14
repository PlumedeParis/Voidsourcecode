package com.voidclient.module.misc;

import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.Modules;
import com.voidclient.module.setting.BooleanSetting;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Chat Timestamps: puts the time in front of chat lines (server/system messages, which is how anarchy servers send chat). */
public class ChatTimestampsModule extends Module {
    private final BooleanSetting seconds = add(new BooleanSetting("Seconds", "Include seconds.", false));

    public ChatTimestampsModule() {
        super("Chat Timestamps", "Shows the time in front of chat messages.", Category.MISC);
    }

    /** From the message hook. */
    public static Text decorate(Text message, boolean overlay) {
        ChatTimestampsModule m = Modules.get(ChatTimestampsModule.class);
        if (overlay || m == null || !m.isEnabled()) return message;
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern(m.seconds.get() ? "HH:mm:ss" : "HH:mm"));
        return Text.literal("[" + time + "] ").formatted(Formatting.DARK_GRAY).append(message);
    }
}
