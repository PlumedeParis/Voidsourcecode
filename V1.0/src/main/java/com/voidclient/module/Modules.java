package com.voidclient.module;

import com.voidclient.VoidClient;

import java.util.List;

/** Static shortcut for module lookups, convenient from mixins. */
public final class Modules {
    private Modules() {
    }

    public static <T extends Module> T get(Class<T> type) {
        VoidClient client = VoidClient.get();
        return client == null || client.modules() == null ? null : client.modules().get(type);
    }

    /** True if the module exists and is enabled. */
    public static boolean active(Class<? extends Module> type) {
        Module module = get(type);
        return module != null && module.isEnabled();
    }

    public static List<Module> all() {
        VoidClient client = VoidClient.get();
        return client == null || client.modules() == null ? List.of() : client.modules().all();
    }
}
