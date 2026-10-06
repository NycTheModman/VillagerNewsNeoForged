package com.javafied.villagernews.mixin;

import com.javafied.villagernews.dialog.PlayerActionReactions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Villagers comment when a player's game mode changes. */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
	@Inject(method = "setGameMode", at = @At("RETURN"))
	private void villagernewsjavafied$gameModeChanged(GameType mode, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			PlayerActionReactions.gameModeChanged((ServerPlayer) (Object) this, mode);
		}
	}
}
