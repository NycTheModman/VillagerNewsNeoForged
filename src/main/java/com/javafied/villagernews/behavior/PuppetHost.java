package com.javafied.villagernews.behavior;

import com.javafied.villagernews.names.AddonNames;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;

/**
 * The state the add-on's script copies from each real villager onto its
 * puppet every tick ({@code ntshmr}), which both the puppet's visuals and its
 * behavior read as properties. Here the villager is its own puppet, so the
 * values are simply computed from it.
 */
public final class PuppetHost {
	/** The {@code packed_state} property: sleeping, on ground, in water and vehicle packed as decimal digits. */
	public static String packedStateProperty() {
		return AddonNames.property("packed_state");
	}

	/**
	 * The {@code trade_tier} property: the villager's trading level, 0 (novice)
	 * to 4 (master), which its level badge shows. The add-on can only read it
	 * in Molang, so its script seats the villager on an invisible helper
	 * entity (the {@code trade_tier_probe}) whose seat script reads the rider's
	 * tier and reports it back; here it comes straight from the villager.
	 */
	public static String tradeTierProperty() {
		return AddonNames.property("trade_tier");
	}

	/** The villager's trading level as Bedrock counts it (Java's levels start at 1); -1 if it doesn't trade. */
	public static int tradeTier(Entity host) {
		return host instanceof Villager villager ? villager.getVillagerData().getLevel() - 1 : -1;
	}

	/** The {@code vehicle} property, see {@link #vehicleIndex}. */
	public static String vehicleProperty() {
		return AddonNames.property("vehicle");
	}

	private PuppetHost() {
	}

	public static int packedState(Entity host) {
		boolean sleeping = host instanceof LivingEntity living && living.isSleeping();
		return 1000 * (sleeping ? 1 : 0) + 100 * (host.onGround() ? 1 : 0) + 10 * (host.isInWater() ? 1 : 0) + vehicleIndex(host);
	}

	/** {@code whwndv}: 1 in a boat, 2 in a minecart, else 0. Java has a boat/raft per wood type. */
	public static int vehicleIndex(Entity host) {
		Entity vehicle = host.getVehicle();
		if (vehicle == null) {
			return 0;
		}
		String type = BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).getPath();
		if (type.endsWith("_boat") || type.endsWith("_raft")) {
			return 1;
		}
		return type.equals("minecart") ? 2 : 0;
	}
}
