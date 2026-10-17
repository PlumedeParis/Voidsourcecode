package com.mytestclient.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;

/** Coarse entity grouping used by ESP and the radar. */
public enum EntityKind {
    PLAYER, HOSTILE, PASSIVE, ITEM, OTHER;

    public static EntityKind of(Entity entity) {
        if (entity instanceof PlayerEntity) return PLAYER;
        if (entity instanceof Monster) return HOSTILE;
        if (entity instanceof LivingEntity) return PASSIVE;
        if (entity instanceof ItemEntity) return ITEM;
        return OTHER;
    }
}
