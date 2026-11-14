package com.voidclient.module.movement;

import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.Risk;
import com.voidclient.module.setting.BooleanSetting;
import com.voidclient.module.setting.NumberSetting;
import com.voidclient.pathing.PathSearch;
import com.voidclient.pathing.Travel;
import com.voidclient.util.ChatUtil;

/**
 * Pathfinder settings, used by {@code \walk}, {@code \mine}, {@code \pickup}, {@code \fetch}, Auto Mine
 * and Item Collector. Triggering the module stops whatever is running.
 * <p>
 * ⚠ DETECTABLE: it plays for you (it walks, mines and places with perfect timing and instant
 * turns), which some servers' anti-bot checks look for.
 */
public class PathfinderModule extends Module {
    private final BooleanSetting allowBreak = add(new BooleanSetting("Break Blocks", "Mine through blocks that are in the way.", true));
    private final BooleanSetting allowPlace = add(new BooleanSetting("Place Blocks", "Bridge gaps and pillar up with cheap blocks (dirt, cobblestone, netherrack...).", true));
    private final NumberSetting maxFall = add(new NumberSetting("Max Fall", "Highest drop it will jump down (any height into water).", 3, 1, 20, 1));
    private final BooleanSetting sprint = add(new BooleanSetting("Sprint", "Sprint on straight lines.", true));
    private final NumberSetting maxNodes = add(new NumberSetting("Search Size", "Thousands of positions explored per search (bigger = finds harder paths, slower).", 150, 20, 600, 10));
    public final BooleanSetting showPath = add(new BooleanSetting("Show Path", "Draw the planned path in green.", true));

    public PathfinderModule() {
        super("Pathfinder", "Settings for \\walk, \\mine, \\pickup and \\fetch. Trigger it to stop the current task.", Category.MOVEMENT);
        setRisk(Risk.DETECTABLE, "It plays for you: walking, mining and placing with bot-like timing; some servers detect bots.");
    }

    @Override
    public boolean isAction() {
        return true;
    }

    @Override
    protected void onAction() {
        if (Travel.isActive()) Travel.stop("stopped");
        else ChatUtil.info("Pathfinder: nothing running. Use \\walk, \\mine <block>, \\pickup or \\fetch <item>.");
    }

    public PathSearch.Settings searchSettings() {
        return new PathSearch.Settings(allowBreak.get(), allowPlace.get(), maxFall.getInt(), maxNodes.getInt() * 1000);
    }

    public boolean sprint() {
        return sprint.get();
    }
}
