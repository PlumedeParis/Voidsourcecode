package com.mytestclient.event;

import com.mytestclient.module.BindMode;
import com.mytestclient.module.Module;
import com.mytestclient.module.ModuleManager;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * Keybind system for modules. Each module stores a GLFW key code (editable in the GUI); this
 * class polls the keyboard once per tick and toggles modules on key press (or holds them while
 * the key is down for {@link BindMode#HOLD}). Keys are ignored while any screen (chat, inventory,
 * GUI...) is open so typing never triggers modules.
 */
public class KeybindManager {
    private final ModuleManager modules;
    private final Map<Integer, Boolean> previous = new HashMap<>();

    public KeybindManager(ModuleManager modules) {
        this.modules = modules;
    }

    public void tick(MinecraftClient mc) {
        if (mc.getWindow() == null) return;
        boolean screenOpen = mc.currentScreen != null;

        Map<Integer, Boolean> now = new HashMap<>();
        for (Module module : modules.all()) {
            int key = module.getKey();
            if (key <= 0 || now.containsKey(key)) continue;
            now.put(key, isKeyDown(key));
        }

        for (Module module : modules.all()) {
            int key = module.getKey();
            if (key <= 0) continue;
            boolean down = now.getOrDefault(key, false);
            boolean wasDown = previous.getOrDefault(key, false);

            if (module.getBindMode() == BindMode.HOLD && !module.isAction()) {
                boolean want = down && !screenOpen;
                if (module.isEnabled() != want) module.setEnabled(want);
            } else if (!screenOpen && down && !wasDown) {
                module.toggle();
            }
        }

        previous.clear();
        previous.putAll(now);
    }

    public static boolean isKeyDown(int key) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (key <= 0 || mc.getWindow() == null) return false;
        return GLFW.glfwGetKey(mc.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
    }

    /**
     * Reads the physical state of a vanilla key binding (keyboard or mouse), ignoring its
     * "pressed" flag. Freecam uses this because it clears that flag to stop the player moving.
     */
    public static boolean isBindingDown(KeyBinding binding) {
        MinecraftClient mc = MinecraftClient.getInstance();
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(binding);
        if (key == null || key.getCode() < 0 || mc.getWindow() == null) return false;
        long handle = mc.getWindow().getHandle();
        if (key.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(handle, key.getCode()) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(handle, key.getCode()) == GLFW.GLFW_PRESS;
    }
}
