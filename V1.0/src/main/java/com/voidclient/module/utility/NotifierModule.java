package com.voidclient.module.utility;

import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.setting.BooleanSetting;
import com.voidclient.util.ChatUtil;
import com.voidclient.util.Friends;
import com.voidclient.util.Notifications;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Notifier: tells you when a player enters or leaves your render distance (visual range) and
 * counts other players' totem pops, announcing how many they burned when they die.
 */
public class NotifierModule extends Module {
    private final BooleanSetting visualRange = add(new BooleanSetting("Visual Range", "Notify when a player enters or leaves your render distance.", true));
    private final BooleanSetting pops = add(new BooleanSetting("Totem Pops", "Count the totem pops of other players.", true));
    private final BooleanSetting chat = add(new BooleanSetting("In Chat", "Also write the notifications in chat (with coordinates).", true));
    private final BooleanSetting friends = add(new BooleanSetting("Friends Too", "Include players on your friend list.", false));

    private final Map<UUID, String> visible = new HashMap<>();
    private final Map<UUID, Integer> popCount = new HashMap<>();

    public NotifierModule() {
        super("Notifier", "Players entering/leaving your render distance, and totem pop counts.", Category.UTILITY);
    }

    @Override
    public void onWorldLeave() {
        visible.clear();
        popCount.clear();
    }

    /** From the network hook: entity status 35 (totem pop) for any entity. */
    public void onTotemPop(Entity entity) {
        if (!pops.get() || !(entity instanceof PlayerEntity p) || p == mc.player) return;
        int n = popCount.merge(p.getUuid(), 1, Integer::sum);
        say(p.getName().getString() + " popped " + n + " totem" + (n == 1 ? "" : "s"), false);
    }

    private void say(String text, boolean warn) {
        if (warn) Notifications.warn(text);
        else Notifications.info(text);
        if (chat.get()) ChatUtil.info(text);
    }

    @Override
    public void onTick() {
        Set<UUID> now = new HashSet<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            String name = p.getName().getString();
            if (!friends.get() && Friends.isFriend(name)) continue;
            now.add(p.getUuid());
            if (visible.put(p.getUuid(), name) == null && visualRange.get()) {
                say(name + " entered visual range (" + p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ() + ")", true);
            }
            if (pops.get() && (p.isDead() || p.getHealth() <= 0)) {
                Integer n = popCount.remove(p.getUuid());
                if (n != null) say(name + " died after popping " + n + " totem" + (n == 1 ? "" : "s"), false);
            }
        }
        visible.entrySet().removeIf(e -> {
            if (now.contains(e.getKey())) return false;
            if (visualRange.get()) say(e.getValue() + " left visual range", false);
            return true;
        });
    }
}
