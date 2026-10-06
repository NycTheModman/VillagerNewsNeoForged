package com.javafied.villagernews.mixin;

import com.javafied.villagernews.dialog.WorldReactions;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Villagers notice buttons being pressed. */
@Mixin(ButtonBlock.class)
abstract class ButtonBlockMixin {
	@Inject(method = "press", at = @At("TAIL"))
	private void villagernewsjavafied$pressed(BlockState state, Level level, BlockPos pos, Player player, CallbackInfo ci) {
		if (level instanceof ServerLevel server) {
			WorldReactions.buttonPressed(server, pos);
		}
	}
}
