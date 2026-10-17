package com.mytestclient.module.movement;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;

/**
 * Safe Walk: never walk off edges, like holding sneak but at full speed. Uses vanilla's own
 * sneak-edge logic ({@code PlayerEntityMixin}). The server only sees normal walking.
 */
public class SafeWalkModule extends Module {
    public SafeWalkModule() {
        super("Safe Walk", "Stops you from walking off edges, without sneaking.", Category.MOVEMENT);
    }
}
