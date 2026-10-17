package com.mytestclient.module.utility;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.BooleanSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Item Info: adds extra lines to item tooltips — exact durability, food values, stack size and
 * registry id. Purely informational, no warning.
 */
public class ItemInfoModule extends Module {
    private final BooleanSetting durability = add(new BooleanSetting("Durability", "Show remaining durability.", true));
    private final BooleanSetting food = add(new BooleanSetting("Food", "Show hunger and saturation restored.", true));
    private final BooleanSetting stack = add(new BooleanSetting("Stack Size", "Show the maximum stack size.", false));
    private final BooleanSetting id = add(new BooleanSetting("Item ID", "Show the registry id.", true));

    public ItemInfoModule() {
        super("Item Info", "Extra tooltip lines: durability, food values, stack size and id.", Category.UTILITY);
    }

    /** Called from the Fabric tooltip callback. */
    public void appendTooltip(ItemStack itemStack, List<Text> lines) {
        if (durability.get() && itemStack.isDamageable()) {
            int max = itemStack.getMaxDamage();
            int left = max - itemStack.getDamage();
            Formatting color = left * 4 < max ? Formatting.RED : left * 2 < max ? Formatting.YELLOW : Formatting.GREEN;
            lines.add(Text.literal("Durability: ").formatted(Formatting.GRAY)
                    .append(Text.literal(left + " / " + max).formatted(color)));
        }
        if (food.get()) {
            FoodComponent foodComponent = itemStack.get(DataComponentTypes.FOOD);
            if (foodComponent != null) {
                lines.add(Text.literal(String.format("Food: %d hunger, %.1f saturation",
                        foodComponent.nutrition(), foodComponent.saturation())).formatted(Formatting.GOLD));
            }
        }
        if (stack.get()) lines.add(Text.literal("Max stack: " + itemStack.getMaxCount()).formatted(Formatting.GRAY));
        if (id.get()) lines.add(Text.literal(Registries.ITEM.getId(itemStack.getItem()).toString()).formatted(Formatting.DARK_GRAY));
    }
}
