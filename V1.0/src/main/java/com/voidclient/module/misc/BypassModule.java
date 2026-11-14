package com.voidclient.module.misc;

import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.setting.BooleanSetting;
import com.voidclient.module.setting.NumberSetting;

/**
 * Bypass: makes the automated modules behave the way server anti-cheats on anarchy servers
 * expect, so they keep working there instead of being rubber-banded or cancelled.
 * <ul>
 *     <li>Silent Rotations: every automated action first tells the server you are looking at
 *     the block/entity (your camera does not move).</li>
 *     <li>Strict Direction: only click block faces you could really see.</li>
 *     <li>Actions Per Tick: cap on automated block breaks/places per tick.</li>
 *     <li>Swing: send the arm swing with every action.</li>
 * </ul>
 * These are settings other modules read; turning the module off restores the old behaviour.
 */
public class BypassModule extends Module {
    public final BooleanSetting silentRotations = add(new BooleanSetting("Silent Rotations", "Rotate server-side only: your camera stays where you look.", true));
    public final NumberSetting holdTicks = add(new NumberSetting("Hold Ticks", "How long a silent rotation is kept after an action.", 4, 1, 20, 1));
    public final BooleanSetting strictDirection = add(new BooleanSetting("Strict Direction", "Only click block faces that face you (some anti-cheats cancel the others).", true));
    public final NumberSetting actionsPerTick = add(new NumberSetting("Actions Per Tick", "Maximum automated block breaks/places per tick (lower = safer on strict servers).", 8, 1, 30, 1));
    public final BooleanSetting swing = add(new BooleanSetting("Swing", "Swing your arm with automated actions (servers expect it).", true));
    public final BooleanSetting keepSprint = add(new BooleanSetting("Stop Sprint On Use", "Stop sprinting before placing/attacking (required by some movement checks).", false));

    public BypassModule() {
        super("Bypass", "Anti-cheat compatibility for automated modules: silent rotations, strict clicks, action limits.", Category.MISC);
    }
}
