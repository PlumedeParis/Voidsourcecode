package com.mytestclient.module.movement;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.NumberSetting;

/**
 * Air Jump: press jump while in the air to jump again (double/triple/infinite jump).
 * <p>
 * ⚠ DETECTABLE: the server sees you gaining height with nothing under you, like flying.
 */
public class AirJumpModule extends Module {
    private final NumberSetting maxJumps = add(new NumberSetting("Jump Limit", "Jumps allowed before landing again (0 = unlimited).", 0, 0, 20, 1));

    private boolean wasPressed;
    private int jumpsUsed;

    public AirJumpModule() {
        super("Air Jump", "Jump again and again while in the air (unlimited by default).", Category.MOVEMENT);
        setRisk(Risk.DETECTABLE, "The server sees you gaining height in mid-air; vanilla servers may kick for flying and anti-cheats flag it.");
    }

    @Override
    public void onTick() {
        boolean pressed = mc.options.jumpKey.isPressed();
        if (mc.player.isOnGround() || mc.player.isTouchingWater() || mc.player.isClimbing()) {
            jumpsUsed = 0;
        } else if (pressed && !wasPressed && !mc.player.getAbilities().flying && !mc.player.isGliding()) {
            int max = maxJumps.getInt();
            if (max == 0 || jumpsUsed < max) {
                mc.player.jump();
                jumpsUsed++;
            }
        }
        wasPressed = pressed;
    }
}
