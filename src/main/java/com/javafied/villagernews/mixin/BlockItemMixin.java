package com.javafied.villagernews.mixin;

import com.javafied.villagernews.dialog.PlayerActionReactions;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Villagers comment on the blocks players place. */
@Mixin(BlockItem.class)
abstract class BlockItemMixin {
	@Inject(method = "place", at = @At("RETURN"))
	private void villagernewsjavafied$placed(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (cir.getReturnValue().consumesAction() && context.getPlayer() != null && context.getLevel() instanceof ServerLevel level) {
			PlayerActionReactions.placed(level, context.getPlayer(), context.getClickedPos(), level.getBlockState(context.getClickedPos()));
		}
	}
}
