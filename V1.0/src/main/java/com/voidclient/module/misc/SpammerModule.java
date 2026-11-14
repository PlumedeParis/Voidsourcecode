package com.voidclient.module.misc;

import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.Risk;
import com.voidclient.module.setting.BooleanSetting;
import com.voidclient.module.setting.NumberSetting;
import com.voidclient.module.setting.StringSetting;

/** Spammer: sends your messages to chat in a loop. Separate several messages with " | ". */
public class SpammerModule extends Module {
    private final StringSetting messages = add(new StringSetting("Messages", "Messages to send, separated by | (commands starting with / work too).", "VoidClient on top | join the void", 200));
    private final NumberSetting delay = add(new NumberSetting("Delay", "Seconds between messages.", 20, 2, 600, 1));
    private final BooleanSetting random = add(new BooleanSetting("Random Order", "Pick a random message each time instead of going in order.", false));
    private final BooleanSetting antiSpam = add(new BooleanSetting("Anti Anti-Spam", "Append a few random characters so identical messages are not blocked.", false));
    private int timer, index;

    public SpammerModule() {
        super("Spammer", "Sends your messages to chat on a timer.", Category.MISC);
        setRisk(Risk.DETECTABLE, "Visible to everyone; most servers mute or kick for spam.");
    }

    @Override
    public boolean persistsState() {
        return false;
    }

    @Override
    public void onTick() {
        if (timer-- > 0) return;
        timer = delay.getInt() * 20;
        String[] list = messages.get().split("\\|");
        if (list.length == 0) return;
        String msg = list[random.get() ? (int) (Math.random() * list.length) : index++ % list.length].trim();
        if (msg.isEmpty()) return;
        if (msg.startsWith("/")) {
            mc.getNetworkHandler().sendChatCommand(msg.substring(1));
            return;
        }
        if (antiSpam.get()) msg += " [" + Integer.toHexString((int) (Math.random() * 0xFFFF)) + "]";
        if (msg.length() > 256) msg = msg.substring(0, 256);
        mc.getNetworkHandler().sendChatMessage(msg);
    }
}
