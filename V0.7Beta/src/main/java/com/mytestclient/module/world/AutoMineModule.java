package com.mytestclient.module.world;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.SafeMode;
import com.mytestclient.module.setting.ListSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.pathing.Travel;
import com.mytestclient.util.ChatUtil;

/**
 * Auto Mine (action): finds the chosen blocks in the loaded chunks, walks to them, mines them and
 * picks up the drops, until it has mined the amount you set. Same as {@code \mine}.
 * Note: on servers with anti-xray, hidden ores are not sent to the client, so it can't see them.
 */
public class AutoMineModule extends Module {
    private final ListSetting blocks = add(new ListSetting("Blocks", "What to mine.", ListSetting.Kind.BLOCK,
            "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"));
    private final NumberSetting count = add(new NumberSetting("Count", "How many blocks to mine.", 16, 1, 512, 1));

    public AutoMineModule() {
        super("Auto Mine", "Action: walks to the chosen ores/blocks, mines them and collects the drops.", Category.WORLD);
        setRisk(Risk.DETECTABLE, "Bot-like mining straight to hidden ores is what anti-xray and anti-bot plugins look for.");
    }

    @Override
    public boolean isAction() {
        return true;
    }

    @Override
    protected void onAction() {
        if (mc.player == null) return;
        String blocked = SafeMode.blockReason(this);
        if (blocked != null) {
            ChatUtil.error("Auto Mine is " + blocked);
            return;
        }
        if (blocks.get().isEmpty()) {
            ChatUtil.error("Auto Mine: pick some blocks first.");
            return;
        }
        String label = blocks.get().size() == 1 ? blocks.get().get(0).replace("minecraft:", "") : blocks.get().size() + " block types";
        if (blocks.get().stream().allMatch(id -> id.contains("diamond_ore"))) label = "diamond";
        Travel.mine(blocks.get(), count.getInt(), label);
    }
}
