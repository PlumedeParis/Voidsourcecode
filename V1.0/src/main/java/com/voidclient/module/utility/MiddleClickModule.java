package com.voidclient.module.utility;

import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.setting.BooleanSetting;
import com.voidclient.util.Friends;
import com.voidclient.util.Notifications;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

/** Middle Click: on a player adds/removes them as a friend, anywhere else throws an ender pearl from your hotbar. */
public class MiddleClickModule extends Module {
    private final BooleanSetting friends = add(new BooleanSetting("Friend", "Middle click a player to add or remove them as a friend.", true));
    private final BooleanSetting pearl = add(new BooleanSetting("Pearl", "Middle click the air to throw an ender pearl without switching slots.", true));
    private boolean wasDown;

    public MiddleClickModule() {
        super("Middle Click", "Middle click a player: friend. Middle click the air: throw an ender pearl.", Category.UTILITY);
    }

    @Override
    public void onTick() {
        boolean down = mc.currentScreen == null && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        boolean clicked = down && !wasDown;
        wasDown = down;
        if (!clicked) return;
        if (friends.get() && mc.targetedEntity instanceof PlayerEntity p) {
            String name = p.getName().getString();
            if (Friends.isFriend(name)) {
                Friends.remove(name);
                Notifications.info(name + " is no longer a friend");
            } else {
                Friends.add(name);
                Notifications.info(name + " added as a friend");
            }
            return;
        }
        if (!pearl.get()) return;
        for (int i = 0; i < 9; i++) {
            if (!mc.player.getInventory().getStack(i).isOf(Items.ENDER_PEARL)) continue;
            int previous = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(i);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.getInventory().setSelectedSlot(previous);
            return;
        }
        Notifications.warn("No ender pearl in your hotbar");
    }
}
