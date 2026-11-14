package com.voidclient.module.visuals;

import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.setting.BooleanSetting;
import com.voidclient.module.setting.NumberSetting;
import com.voidclient.render.Overlay;
import com.voidclient.util.ChatUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Logout Spots: when a player near you logs out, marks the spot where they stood (box, name,
 * health, how long ago). They will come back exactly there. The mark goes away when they rejoin.
 */
public class LogoutSpotsModule extends Module {
    private final NumberSetting keep = add(new NumberSetting("Keep Minutes", "Forget a spot after this many minutes (0 = until you leave).", 30, 0, 240, 5));
    private final BooleanSetting chat = add(new BooleanSetting("In Chat", "Write the spot's coordinates in chat.", true));

    private record Seen(String name, Box box, float health) {
    }

    private record Spot(String name, Box box, float health, long time, String dimension) {
    }

    private final Map<UUID, Seen> last = new HashMap<>();
    private final Map<UUID, Spot> spots = new HashMap<>();

    public LogoutSpotsModule() {
        super("Logout Spots", "Marks where nearby players logged out.", Category.VISUALS);
    }

    @Override
    public void onWorldLeave() {
        last.clear();
        spots.clear();
    }

    @Override
    public void onTick() {
        if (mc.getNetworkHandler() == null) return;
        String dim = mc.world.getRegistryKey().getValue().toString();
        Map<UUID, Seen> now = new HashMap<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            now.put(p.getUuid(), new Seen(p.getName().getString(), p.getBoundingBox(), p.getHealth() + p.getAbsorptionAmount()));
            spots.remove(p.getUuid()); // back online
        }
        for (Map.Entry<UUID, Seen> e : last.entrySet()) {
            if (now.containsKey(e.getKey())) continue;
            // gone from the world AND from the tab list = logged out (not just out of range)
            if (mc.getNetworkHandler().getPlayerListEntry(e.getKey()) != null) continue;
            Seen s = e.getValue();
            spots.put(e.getKey(), new Spot(s.name, s.box, s.health, System.currentTimeMillis(), dim));
            if (chat.get()) ChatUtil.info(String.format("%s logged out at %d %d %d (%.0f❤).", s.name, (int) s.box.minX, (int) s.box.minY, (int) s.box.minZ, s.health));
        }
        last.clear();
        last.putAll(now);
        long max = keep.getInt() * 60_000L;
        for (Iterator<Spot> it = spots.values().iterator(); it.hasNext(); ) {
            Spot s = it.next();
            if (max > 0 && System.currentTimeMillis() - s.time > max) it.remove();
        }
        // rejoined somewhere out of range
        spots.keySet().removeIf(id -> mc.getNetworkHandler().getPlayerListEntry(id) != null && !last.containsKey(id) && System.currentTimeMillis() - spots.get(id).time > 3000);
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        String dim = mc.world.getRegistryKey().getValue().toString();
        for (Spot s : spots.values()) {
            if (!s.dimension.equals(dim)) continue;
            o.box(s.box, 0xFFFF5C5C);
            long sec = (System.currentTimeMillis() - s.time) / 1000;
            String ago = sec >= 60 ? (sec / 60) + "m" : sec + "s";
            o.label((s.box.minX + s.box.maxX) / 2, s.box.maxY + 0.4, (s.box.minZ + s.box.maxZ) / 2, s.name + " logged out " + ago + " ago  " + Math.round(s.health) + "❤", 0xFFFF5C5C);
        }
    }
}
