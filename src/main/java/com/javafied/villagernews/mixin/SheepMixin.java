package com.javafied.villagernews.mixin;

import com.javafied.villagernews.dialog.PlayerActionReactions;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Villagers react to sheep being sheared. */
@Mixin(Sheep.class)
abstract class SheepMixin {
	@Inject(method = "shear", at = @At("TAIL"))
	private void villagernewsjavafied$sheared(SoundSource source, CallbackInfo ci) {
		if (((Sheep)(Object)this).level() instanceof ServerLevel level) PlayerActionReactions.sheared(level, (Sheep) (Object) this);
	}
}
