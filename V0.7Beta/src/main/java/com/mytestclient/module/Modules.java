package com.mytestclient.module;

import com.mytestclient.MyTestClient;

import java.util.List;

/** Static shortcut for module lookups, convenient from mixins. */
public final class Modules {
    private Modules() {
    }

    public static <T extends Module> T get(Class<T> type) {
        MyTestClient client = MyTestClient.get();
        return client == null || client.modules() == null ? null : client.modules().get(type);
    }

    /** True if the module exists and is enabled. */
    public static boolean active(Class<? extends Module> type) {
        Module module = get(type);
        return module != null && module.isEnabled();
    }

    public static List<Module> all() {
        MyTestClient client = MyTestClient.get();
        return client == null || client.modules() == null ? List.of() : client.modules().all();
    }
}
