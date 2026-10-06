package com.javafied.villagernews.mixin;

import com.javafied.villagernews.dialog.PlayerActionReactions;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Villagers object to crops being trampled. */
@Mixin(FarmBlock.class)
abstract class FarmlandBlockMixin {
	@Inject(method = "turnToDirt", at = @At("HEAD"))
	private static void villagernewsjavafied$trampled(Entity entity, BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
		if (level instanceof ServerLevel server && level.getBlockState(pos.above()).is(BlockTags.CROPS)) {
			PlayerActionReactions.trampled(server, pos);
		}
	}
}
