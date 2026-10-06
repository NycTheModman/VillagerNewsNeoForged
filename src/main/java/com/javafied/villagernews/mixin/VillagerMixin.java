package com.javafied.villagernews.mixin;

import com.javafied.villagernews.content.ModAttachments;
import com.javafied.villagernews.content.SpecialTrades;
import com.javafied.villagernews.dialog.VillagerLifeReactions;
import com.javafied.villagernews.dialog.WorldReactions;

import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ServerLevelAccessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * The add-on's special characters trade from its own tables instead of
 * vanilla's; and villagers placed by village generation are marked, since
 * the add-on turns one of those into a special character.
 */
@Mixin(Villager.class)
abstract class VillagerMixin {
	@Inject(method = "finalizeSpawn", at = @At("HEAD"))
	private void villagernewsjavafied$markVillageVillager(ServerLevelAccessor level, DifficultyInstance difficulty,
			MobSpawnType reason, SpawnGroupData data, CallbackInfoReturnable<SpawnGroupData> cir) {
		Villager self = (Villager) (Object) this;
		if (reason == MobSpawnType.STRUCTURE) {
			self.setData(ModAttachments.FROM_VILLAGE_GENERATION, true);
		} else if (reason == MobSpawnType.CONVERSION && level.getLevel().getServer() != null) {
			var server = level.getLevel().getServer();
			com.javafied.villagernews.platform.ServerTasks.schedule(server,new TickTask(server.getTickCount() + 1, () -> {
				if (self.isAlive()) {
					VillagerLifeReactions.cured(self);
				}
			}));
		} else if ((reason == MobSpawnType.SPAWN_EGG || reason == MobSpawnType.COMMAND
				|| reason == MobSpawnType.DISPENSER) && level.getLevel().getServer() != null) {
			var server = level.getLevel().getServer();
			com.javafied.villagernews.platform.ServerTasks.schedule(server,new TickTask(server.getTickCount() + 1, () -> {
				if (self.isAlive() && self.level().getEntity(self.getId()) == self) {
					VillagerLifeReactions.spawnedByPlayer(self);
				}
			}));
		}
	}

	@Inject(method = "ageBoundaryReached", at = @At("TAIL"))
	private void villagernewsjavafied$grewUp(CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (!self.isBaby() && self.level() instanceof ServerLevel) {
			VillagerLifeReactions.grewUp(self);
		}
	}

	@Inject(method = "getBreedOffspring(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/AgeableMob;)Lnet/minecraft/world/entity/npc/Villager;",
			at = @At("RETURN"))
	private void villagernewsjavafied$born(ServerLevel level, AgeableMob partner, CallbackInfoReturnable<Villager> cir) {
		Villager baby = cir.getReturnValue();
		if (baby != null) {
			// Not in the world yet: react once it's been added.
			com.javafied.villagernews.platform.ServerTasks.schedule(level.getServer(),new TickTask(level.getServer().getTickCount() + 1, () -> {
				if (baby.isAlive() && baby.level().getEntity(baby.getId()) == baby) {
					VillagerLifeReactions.born(level, baby);
				}
			}));
		}
	}

	@Inject(method = "increaseMerchantCareer", at = @At("TAIL"))
	private void villagernewsjavafied$levelledUp(CallbackInfo ci) {
		com.javafied.villagernews.dialog.TradeReactions.levelledUp((Villager) (Object) this);
	}

	/** Restocking happens as a villager starts its shift at its workstation. */
	@Inject(method = "restock", at = @At("TAIL"))
	private void villagernewsjavafied$restocked(CallbackInfo ci) {
		com.javafied.villagernews.dialog.WorkReactions.restocked((Villager) (Object) this);
	}

	@Inject(method = "pickUpItem", at = @At("HEAD"))
	private void villagernewsjavafied$pickedUp(ItemEntity item, CallbackInfo ci) {
		WorldReactions.pickedUp((Villager) (Object) this, item);
	}

	@Inject(method = "setVillagerData", at = @At("HEAD"))
	private void villagernewsjavafied$professionChanged(VillagerData data, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (self.level() instanceof ServerLevel && self.tickCount > 0) {
			String before = profession(self.getVillagerData());
			String after = profession(data);
			if (!before.equals(after)) {
				WorldReactions.professionChanged(self, before, after);
			}
		}
	}

	private static String profession(VillagerData data) {
		return net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(data.getProfession()).getPath();
	}

	@Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true)
	private void villagernewsjavafied$specialTrades(CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		List<MerchantOffer> offers = SpecialTrades.offers(self);
		if (!offers.isEmpty()) {
			self.getOffers().addAll(offers);
			ci.cancel();
		}
	}
}
