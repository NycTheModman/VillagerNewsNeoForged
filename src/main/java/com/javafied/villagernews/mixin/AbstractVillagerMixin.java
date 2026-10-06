package com.javafied.villagernews.mixin;

import com.javafied.villagernews.dialog.TradeReactions;

import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Trade window opening/closing and completed trades, which the add-on's villagers react to. */
@Mixin(AbstractVillager.class)
abstract class AbstractVillagerMixin {
	@Shadow
	public abstract Player getTradingPlayer();

	@Inject(method = "setTradingPlayer", at = @At("HEAD"))
	private void villagernewsjavafied$tradingPlayerChanged(Player player, CallbackInfo ci) {
		AbstractVillager self = (AbstractVillager) (Object) this;
		if (!self.level().isClientSide()) {
			TradeReactions.tradingChanged(self, getTradingPlayer(), player);
		}
	}

	@Inject(method = "notifyTrade", at = @At("TAIL"))
	private void villagernewsjavafied$traded(MerchantOffer offer, CallbackInfo ci) {
		AbstractVillager self = (AbstractVillager) (Object) this;
		if (!self.level().isClientSide()) {
			TradeReactions.traded(self);
		}
	}
}
