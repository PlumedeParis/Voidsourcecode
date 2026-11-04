package com.mytestclient.module.combat;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.ModeSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * Criticals: makes your hits critical (+50% damage).
 * <ul>
 *     <li>Packet: before each hit, tells the server you did a tiny hop, so a hit from the ground counts as falling.</li>
 *     <li>Jump: Kill Aura and Trigger Bot jump and wait for the falling part of the jump to hit (legit crits).</li>
 * </ul>
 * ⚠ DETECTABLE: Packet mode sends fake positions; most anti-cheats check it.
 */
public class CriticalsModule extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Packet: fake tiny hop before each hit. Jump: automated attacks jump and hit while falling.", "Packet", "Packet", "Jump"));

    public CriticalsModule() {
        super("Criticals", "Every hit becomes a critical hit (+50% damage).", Category.COMBAT);
        setRisk(Risk.DETECTABLE, "Packet mode sends fake positions; anti-cheats check for it.");
    }

    private boolean canCrit() {
        var p = mc.player;
        return p.isOnGround() && !p.isTouchingWater() && !p.isInLava() && !p.isClimbing() && !p.hasVehicle() && !p.isGliding();
    }

    /** Called right before an attack. */
    public void beforeAttack(Entity target) {
        if (!mode.is("Packet") || !(target instanceof LivingEntity) || !canCrit()) return;
        var p = mc.player;
        boolean hc = p.horizontalCollision;
        p.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(p.getX(), p.getY() + 0.0625, p.getZ(), false, hc));
        p.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(p.getX(), p.getY(), p.getZ(), false, hc));
    }

    /** Jump mode: true while automated attacks should wait (jumps when on the ground). */
    public boolean holdAttack() {
        if (!mode.is("Jump")) return false;
        var p = mc.player;
        if (p.isTouchingWater() || p.isInLava() || p.isClimbing() || p.hasVehicle() || p.isGliding()) return false;
        if (p.isOnGround()) {
            p.jump();
            return true;
        }
        return p.getVelocity().y > -0.05 || p.fallDistance <= 0; // still going up
    }
}
