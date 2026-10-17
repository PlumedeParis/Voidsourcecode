package com.mytestclient.util;

import com.mytestclient.module.setting.ListSetting;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Cached, sorted lists of blocks / entity types with display names and icons for the picker menu. */
public final class RegistryEntries {
    public record Entry(String id, String name, String search, ItemStack icon) {
    }

    private static final Map<ListSetting.Kind, List<Entry>> CACHE = new EnumMap<>(ListSetting.Kind.class);

    private RegistryEntries() {
    }

    public static List<Entry> get(ListSetting.Kind kind) {
        return CACHE.computeIfAbsent(kind, RegistryEntries::build);
    }

    public static Entry find(ListSetting.Kind kind, String id) {
        for (Entry e : get(kind)) if (e.id.equals(id)) return e;
        return null;
    }

    private static List<Entry> build(ListSetting.Kind kind) {
        List<Entry> list = new ArrayList<>();
        if (kind == ListSetting.Kind.BLOCK) {
            for (Identifier id : Registries.BLOCK.getIds()) {
                Block block = Registries.BLOCK.get(id);
                ItemStack icon = new ItemStack(block.asItem());
                list.add(entry(id, block.getName().getString(), icon));
            }
        } else {
            for (Identifier id : Registries.ENTITY_TYPE.getIds()) {
                EntityType<?> type = Registries.ENTITY_TYPE.get(id);
                SpawnEggItem egg = SpawnEggItem.forEntity(type);
                list.add(entry(id, type.getName().getString(), egg == null ? ItemStack.EMPTY : new ItemStack(egg)));
            }
        }
        list.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    private static Entry entry(Identifier id, String name, ItemStack icon) {
        String full = id.toString();
        return new Entry(full, name, (name + " " + full).toLowerCase(Locale.ROOT), icon);
    }
}
