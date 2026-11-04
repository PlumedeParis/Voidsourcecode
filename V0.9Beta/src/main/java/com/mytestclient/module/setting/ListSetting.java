package com.mytestclient.module.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * A set of registry ids (blocks or entity types) chosen from a searchable picker menu in the GUI
 * — no need to type ids by hand.
 */
public class ListSetting extends Setting<List<String>> {
    public enum Kind {BLOCK, ENTITY}

    private final Kind kind;
    private Set<String> lookup = new HashSet<>();

    public ListSetting(String name, String description, Kind kind, String... defaults) {
        super(name, description, List.of(defaults));
        this.kind = kind;
        set(List.of(defaults));
    }

    public ListSetting visibleWhen(BooleanSupplier condition) {
        setVisibility(condition);
        return this;
    }

    @Override
    protected List<String> sanitize(List<String> v) {
        List<String> clean = new ArrayList<>(new LinkedHashSet<>(v == null ? defaultValue : v));
        lookup = new HashSet<>(clean);
        return clean;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean contains(String id) {
        return lookup.contains(id);
    }

    public void toggle(String id) {
        List<String> copy = new ArrayList<>(value);
        if (!copy.remove(id)) copy.add(id);
        set(copy);
    }

    public void add(String id) {
        if (!contains(id)) toggle(id);
    }

    public void remove(String id) {
        if (contains(id)) toggle(id);
    }

    public int size() {
        return value.size();
    }

    @Override
    public JsonElement toJson() {
        JsonArray array = new JsonArray();
        for (String id : value) array.add(id);
        return array;
    }

    @Override
    public void fromJson(JsonElement element) {
        List<String> ids = new ArrayList<>();
        for (JsonElement e : element.getAsJsonArray()) ids.add(e.getAsString());
        set(ids);
    }
}
