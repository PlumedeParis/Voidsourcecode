package com.mytestclient.module.visuals;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;

/** No Hurt Cam: removes the camera shake when you take damage (see GameRendererMixin). Visual only. */
public class NoHurtCamModule extends Module {
    public NoHurtCamModule() {
        super("No Hurt Cam", "Removes the camera shake when you get hurt.", Category.VISUALS);
    }
}
