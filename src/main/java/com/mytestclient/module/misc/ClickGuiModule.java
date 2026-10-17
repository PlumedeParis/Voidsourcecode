package com.mytestclient.module.misc;

import com.mytestclient.gui.ClickGuiScreen;
import com.mytestclient.gui.Theme;
import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.ColorSetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Opens the click GUI (Right Shift by default; change it in Minecraft's Controls settings) and holds
 * the client-wide settings:
 * accent colour, Safe Mode, notifications and tooltips.
 */
public class ClickGuiModule extends Module {
    private static final KeyBinding.Category KEYBIND_CATEGORY =
            KeyBinding.Category.create(Identifier.of("mytestclient", "voidclient"));
    private final KeyBinding openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.mytestclient.open_gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, KEYBIND_CATEGORY));

    public final ColorSetting accent = add(new ColorSetting("Accent", "Accent colour of the GUI and HUD.", Theme.DEFAULT_ACCENT));
    public final BooleanSetting safeMode = add(new BooleanSetting("Safe Mode",
            "Block modules marked with a red ⚠ (detectable) on multiplayer servers. They still work in singleplayer.", true));
    public final BooleanSetting notifications = add(new BooleanSetting("Notifications", "Show a toast when a module is toggled.", true));
    public final BooleanSetting tooltips = add(new BooleanSetting("Tooltips", "Show descriptions when hovering things in the GUI.", true));
    public final ColorSetting accent2 = add(new ColorSetting("Accent 2", "Second colour of the accent gradient.", Theme.DEFAULT_ACCENT2));
    public final BooleanSetting animations = add(new BooleanSetting("Animations", "Opening, sliding and fading animations in the GUI.", true));
    public final BooleanSetting starfield = add(new BooleanSetting("Starfield", "Drifting stars behind the GUI.", true));
    public final BooleanSetting blur = add(new BooleanSetting("Blur", "Blur the game behind the GUI.", true));

    public ClickGuiModule() {
        super("ClickGUI", "Opens this menu. Change its key in Minecraft's Controls settings. Also holds global client settings.",
                Category.MISC);
    }

    @Override
    public boolean isAction() {
        return true;
    }

    @Override
    protected void onAction() {
        mc.setScreen(new ClickGuiScreen());
    }

    public boolean wasOpenGuiPressed() {
        return openGuiKey.wasPressed();
    }
}
