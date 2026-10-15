package com.mytestclient.mixin;

import com.mytestclient.module.movement.EntityControlModule;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.StriderEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity Control: the rider becomes the controlling passenger without a saddle. Each class
 * overrides {@code getControllingPassenger}, so all of them are targeted.
 */
@Mixin({MobEntity.class, AbstractHorseEntity.class, PigEntity.class, StriderEntity.class})
public abstract class MountControlMixin {
    @Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
    private void mytestclient$controlWithoutSaddle(CallbackInfoReturnable<LivingEntity> cir) {
        LivingEntity controller = EntityControlModule.controller((Entity) (Object) this);
        if (controller != null) cir.setReturnValue(controller);
    }
}
