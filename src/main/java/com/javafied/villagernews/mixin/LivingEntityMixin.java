package com.javafied.villagernews.mixin;

import com.javafied.villagernews.behavior.BehaviorSensors;
import com.javafied.villagernews.dialog.HurtReactions;
import com.javafied.villagernews.dialog.PlayerActionReactions;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Villagers comment on players eating, and on witches' potions taking effect
 * on them; the Mayor, a baby for good, speaks with a baby's pitch.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@Shadow
	public abstract net.minecraft.world.item.ItemStack getUseItem();

	@Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
			at = @At("RETURN"))
	private void villagernewsjavafied$effectAdded(MobEffectInstance effect, Entity source,
			CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (cir.getReturnValueZ() && !self.level().isClientSide()) {
			HurtReactions.effectAdded(self, effect.getEffect(), source);
		}
	}

	/** Vanilla's baby pitch, for a villager its definition makes a baby for good (the Mayor stays an adult in Java). */
	@Inject(method = "getVoicePitch", at = @At("HEAD"), cancellable = true)
	private void villagernewsjavafied$babyVoice(CallbackInfoReturnable<Float> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof Villager && !self.isBaby() && !self.level().isClientSide() && BehaviorSensors.isBaby(self)) {
			cir.setReturnValue((self.getRandom().nextFloat() - self.getRandom().nextFloat()) * 0.2F + 1.5F);
		}
	}

	@Inject(method = "completeUsingItem", at = @At("HEAD"))
	private void villagernewsjavafied$ate(CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player && getUseItem().has(DataComponents.FOOD)) {
			PlayerActionReactions.ate(player);
		}
	}
}
