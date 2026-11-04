package com.mytestclient.module.misc;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import net.minecraft.util.Hand;

/**
 * Anti AFK: does a small action every so often (jump, look around, swing) so you are not
 * kicked for being idle. Note: some servers' rules forbid AFK bypassing.
 */
public class AntiAfkModule extends Module {
    private final NumberSetting interval = add(new NumberSetting("Interval", "Seconds between actions.", 30, 5, 300, 5));
    private final BooleanSetting jump = add(new BooleanSetting("Jump", "Jump.", true));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", "Turn the camera a bit.", true));
    private final BooleanSetting swing = add(new BooleanSetting("Swing", "Swing your arm.", false));

    private int timer;

    public AntiAfkModule() {
        super("Anti AFK", "Moves a little now and then so you don't get kicked for being idle.", Category.MISC);
    }

    @Override
    public void onTick() {
        if (timer-- > 0) return;
        timer = interval.getInt() * 20;
        if (jump.get() && mc.player.isOnGround()) mc.player.jump();
        if (rotate.get()) mc.player.setYaw(mc.player.getYaw() + (float) (Math.random() * 60 - 30));
        if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
    }
}
