package com.javafied.villagernews.mixin;

import com.javafied.villagernews.dialog.WorldReactions;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers who threw an item (villagers sharing food), which the add-on's pick-up reactions need. */
@Mixin(BehaviorUtils.class)
abstract class BehaviorUtilsMixin {
	@Inject(method = "throwItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;F)V",
			at = @At("HEAD"))
	private static void villagernewsjavafied$throwing(LivingEntity thrower, ItemStack stack, Vec3 target, Vec3 speed, float yOffset,
			CallbackInfo ci) {
		if (!thrower.level().isClientSide()) {
			WorldReactions.throwing(thrower, stack);
		}
	}

	@Inject(method = "throwItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;F)V",
			at = @At("RETURN"))
	private static void villagernewsjavafied$thrown(LivingEntity thrower, ItemStack stack, Vec3 target, Vec3 speed, float yOffset,
			CallbackInfo ci) {
		WorldReactions.throwing(null, null);
	}
}
