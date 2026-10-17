package com.mytestclient.module.hud;

import com.mytestclient.hud.HudModule;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.ModeSetting;
import com.mytestclient.render.Colors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Armor HUD: your armour and held items with durability. */
public class ArmorHud extends HudModule {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final ModeSetting layout = add(new ModeSetting("Layout", "Arrange items in a row or a column.", "Horizontal", "Horizontal", "Vertical"));
    private final BooleanSetting hands = add(new BooleanSetting("Hands", "Also show main hand and offhand items.", true));
    private final BooleanSetting percent = add(new BooleanSetting("Percent", "Show durability percentage.", true));

    public ArmorHud() {
        super("Armor", "Shows armour and held items with durability.", 50, 88);
    }

    @Override
    protected void render(DrawContext ctx, int x, int y, float tickDelta) {
        List<ItemStack> stacks = new ArrayList<>();
        if (hands.get()) stacks.add(mc.player.getMainHandStack());
        for (EquipmentSlot slot : ARMOR) stacks.add(mc.player.getEquippedStack(slot));
        if (hands.get()) stacks.add(mc.player.getOffHandStack());
        stacks.removeIf(ItemStack::isEmpty);
        if (stacks.isEmpty()) {
            setSize(20, 20);
            return;
        }

        boolean horizontal = layout.is("Horizontal");
        int cell = percent.get() ? (horizontal ? 22 : 18) : 18;
        int i = 0;
        for (ItemStack stack : stacks) {
            int ix = horizontal ? x + 2 + i * 20 : x + 2;
            int iy = horizontal ? y + 2 : y + 2 + i * cell;
            ctx.drawItem(stack, ix, iy);
            ctx.drawStackOverlay(mc.textRenderer, stack, ix, iy);
            if (percent.get() && stack.isDamageable()) {
                float frac = 1f - (float) stack.getDamage() / stack.getMaxDamage();
                String text = Math.round(frac * 100) + "%";
                int tx = horizontal ? ix + 8 - mc.textRenderer.getWidth(text) / 2 : ix + 19;
                int ty = horizontal ? iy + 17 : iy + 4;
                ctx.drawText(mc.textRenderer, text, tx, ty, Colors.health(frac), true);
            }
            i++;
        }
        if (horizontal) setSize(stacks.size() * 20 + 2, percent.get() ? 30 : 20);
        else setSize(percent.get() ? 44 : 20, stacks.size() * cell + 2);
    }
}
