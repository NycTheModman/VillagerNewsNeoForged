package com.javafied.villagernews.client.bedrock;

import com.javafied.villagernews.behavior.PuppetHost;
import com.javafied.villagernews.content.VillagerVariantKeys;
import com.javafied.villagernews.names.AddonNames;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import team.unnamed.mocha.runtime.value.Value;

import java.util.Map;

/**
 * Hand port of the part of the add-on's script ({@code ebi.js}) that its
 * villager visuals depend on.
 *
 * <p>In Bedrock every real villager ({@code minecraft:villager_v2}) gets an
 * invisible-host / visible-puppet pair: the script spawns an
 * {@code oreville_vn:villager} puppet, and every tick ({@code ufogjv}) copies
 * the host's state into the puppet's properties ({@code ntshmr}) and
 * teleports the puppet above the host ({@code zvxovy}); the puppet's
 * {@code offset} animation then pulls the model back down onto the host.
 * Here the model is drawn on the real villager itself, so both halves are
 * reproduced from the villager directly.
 */
final class VillagerPuppetPort {
	/** Path of the add-on client entity the script spawns as the puppet (an ordinary villager's look). */
	static String puppet() {
		return AddonNames.character(VillagerVariantKeys.DEFAULT);
	}
	static final double ADULT_LIFT = 1.9;
	static final double BABY_LIFT = 0.98;

	private VillagerPuppetPort() {
	}

	/** {@code ntshmr}: the host state the script copies onto the puppet's properties. */
	static Map<String, Value> hostDrivenProperties(Entity host) {
		return Map.of(PuppetHost.packedStateProperty(), Value.of(PuppetHost.packedState(host)),
				PuppetHost.vehicleProperty(), Value.of(PuppetHost.vehicleIndex(host)),
				PuppetHost.tradeTierProperty(), Value.of(PuppetHost.tradeTier(host)));
	}

	/**
	 * {@code zvxovy}: how far above its host the script keeps the puppet, in
	 * blocks - the displacement the {@code offset} animation was written to
	 * undo. None while sleeping (puppet placed on the host) or riding (the
	 * puppet rides the vehicle instead).
	 */
	static double lift(Entity host) {
		if (host.isPassenger() || host instanceof LivingEntity living && living.isSleeping()) {
			return 0;
		}
		return host instanceof LivingEntity living && living.isBaby() ? BABY_LIFT : ADULT_LIFT;
	}
}
