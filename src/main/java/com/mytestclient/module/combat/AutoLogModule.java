package com.mytestclient.module.combat;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.util.Friends;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/** Auto Log: disconnects you when your health is low or (optionally) when a player comes close. */
public class AutoLogModule extends Module {
    private final NumberSetting health = add(new NumberSetting("Health", "Leave when health is at or below this (0 = off).", 6, 0, 19, 1));
    private final BooleanSetting onPlayer = add(new BooleanSetting("Player Nearby", "Leave when a non-friend player comes within range.", false));
    private final NumberSetting range = add(new NumberSetting("Range", "Player distance.", 32, 4, 128, 4).visibleWhen(onPlayer::get));
    private final BooleanSetting disableAfter = add(new BooleanSetting("Disable After", "Turn the module off after leaving, so you can log back in.", true));

    public AutoLogModule() {
        super("Auto Log", "Leaves the server when you're about to die or a player shows up.", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (mc.isInSingleplayer() || mc.player.isCreative()) return;
        String reason = null;
        if (health.get() > 0 && mc.player.getHealth() <= health.get()) reason = "health " + (int) mc.player.getHealth();
        if (reason == null && onPlayer.get()) {
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player || Friends.isFriend(p.getName().getString())) continue;
                if (mc.player.distanceTo(p) <= range.get()) {
                    reason = p.getName().getString() + " came close";
                    break;
                }
            }
        }
        if (reason == null) return;
        if (disableAfter.get()) setEnabled(false);
        mc.player.networkHandler.getConnection().disconnect(Text.literal("[VoidClient] Auto Log: " + reason));
    }
}
