package com.voidclient.module.visuals;

import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.setting.BooleanSetting;
import com.voidclient.render.Colors;
import com.voidclient.render.Overlay;
import com.voidclient.util.Friends;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

/**
 * Nametags: a readable tag above every player at any distance and through walls: name, health
 * (coloured), ping, distance and what they hold. Friends are shown in cyan.
 */
public class NametagsModule extends Module {
    private final BooleanSetting health = add(new BooleanSetting("Health", "Show health (with absorption).", true));
    private final BooleanSetting ping = add(new BooleanSetting("Ping", "Show the player's ping.", true));
    private final BooleanSetting distance = add(new BooleanSetting("Distance", "Show how far away they are.", true));
    private final BooleanSetting item = add(new BooleanSetting("Held Item", "Show the item in their main hand.", true));

    public NametagsModule() {
        super("Nametags", "Bigger, see-through nametags with health, ping, distance and held item.", Category.VISUALS);
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isInvisible()) continue;
            Vec3d pos = p.getLerpedPos(o.tickDelta());
            String name = p.getName().getString();
            StringBuilder sb = new StringBuilder(name);
            float hp = p.getHealth() + p.getAbsorptionAmount();
            if (health.get()) sb.append("  ").append(String.format("%.0f", hp)).append("❤");
            if (ping.get() && mc.getNetworkHandler() != null) {
                var entry = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
                if (entry != null) sb.append("  ").append(entry.getLatency()).append("ms");
            }
            if (distance.get()) sb.append("  ").append(Math.round(mc.player.distanceTo(p))).append("m");
            ItemStack held = p.getMainHandStack();
            if (item.get() && !held.isEmpty()) sb.append("  [").append(held.getName().getString()).append("]");
            int color = Friends.isFriend(name) ? 0xFF22D3EE : health.get() ? Colors.health(Math.min(1f, hp / 20f)) : 0xFFFFFFFF;
            o.label(pos.x, pos.y + p.getHeight() + 0.45, pos.z, sb.toString(), color);
        }
    }
}
