package com.voidclient.module.movement;

import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.Risk;

/** No Slow: keep your normal speed while eating, drinking, blocking or drawing a bow. */
public class NoSlowModule extends Module {
    public NoSlowModule() {
        super("No Slow", "Using an item (eating, shield, bow) no longer slows you down.", Category.MOVEMENT);
        setRisk(Risk.DETECTABLE, "You move faster than the server expects while using an item.");
    }
}
