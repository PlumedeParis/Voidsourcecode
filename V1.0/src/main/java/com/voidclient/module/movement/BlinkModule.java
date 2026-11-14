package com.voidclient.module.movement;

import com.voidclient.module.BindMode;
import com.voidclient.module.Category;
import com.voidclient.module.Module;
import com.voidclient.module.Risk;
import com.voidclient.module.setting.NumberSetting;
import com.voidclient.render.Overlay;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

/**
 * Blink: while on, your movement is not sent to the server: for everyone else you stand still
 * (a box shows where). Turn it off and all the held movement is sent at once: you "teleport".
 */
public class BlinkModule extends Module {
    private final NumberSetting maxPackets = add(new NumberSetting("Max Packets", "Send everything automatically after this many held packets (0 = never).", 200, 0, 1000, 10));
    private final List<Packet<?>> held = new ArrayList<>();
    private Box start;
    private boolean flushing;

    public BlinkModule() {
        super("Blink", "Holds your movement packets, then sends them all at once.", Category.MOVEMENT);
        setRisk(Risk.DETECTABLE, "The server sees you freeze and then move a long way at once.");
        setDefaultBindMode(BindMode.HOLD);
    }

    @Override
    public boolean persistsState() {
        return false;
    }

    @Override
    protected void onEnable() {
        if (mc.player != null) start = mc.player.getBoundingBox();
    }

    @Override
    protected void onDisable() {
        flush();
    }

    @Override
    public void onWorldLeave() {
        held.clear();
        setEnabledQuietly(false);
    }

    /** From the connection mixin. @return true to hold the packet back */
    public boolean intercept(Packet<?> packet) {
        if (flushing || !isEnabled() || !(packet instanceof PlayerMoveC2SPacket) || mc.player == null) return false;
        held.add(packet);
        if (maxPackets.getInt() > 0 && held.size() >= maxPackets.getInt()) mc.execute(() -> setEnabled(false));
        return true;
    }

    private void flush() {
        if (mc.getNetworkHandler() == null) {
            held.clear();
            return;
        }
        flushing = true;
        try {
            for (Packet<?> p : held) mc.getNetworkHandler().sendPacket(p);
        } finally {
            flushing = false;
            held.clear();
        }
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        if (start != null) o.box(start, 0xFF22D3EE);
    }
}
