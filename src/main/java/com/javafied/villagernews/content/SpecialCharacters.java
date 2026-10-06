package com.javafied.villagernews.content;

import com.javafied.villagernews.behavior.BehaviorSensors;
import com.javafied.villagernews.names.AddonNames;

import com.javafied.villagernews.platform.ServerLivingEntityEvents;
import com.javafied.villagernews.platform.ServerEntityEvents;
import com.javafied.villagernews.platform.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.Villager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hand port of how the add-on places its special characters: one of each
 * lives somewhere in the world. When a village generates, one of its
 * villagers becomes a character not yet in the world - as long as it's over
 * 1000 blocks from world spawn and 150 from where another character
 * appeared. When a character dies, it may turn up again in another village.
 */
public final class SpecialCharacters {
	/** The Mayor, Testificate Man, Villager #5, Villager #9, the Untouchable Villager, and Wooly (a sheep). */
	private static final List<String> CHARACTERS = List.of("mayor", "testificate_man", "villager_5", "villager_9", "untouchable", "wooly");
	private static final String WOOLY = "wooly";
	private static final double MIN_DISTANCE_FROM_SPAWN = 1000;
	private static final double MIN_DISTANCE_APART = 150;

	/** Villagers to consider at the end of the tick - not while the entity manager is mid-load. */
	private static final List<Villager> pending = new ArrayList<>();

	private SpecialCharacters() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			// Saved before characters had readable names: "ilvfra" -> "mayor".
			String variant = entity.getExistingDataOrNull(ModAttachments.VILLAGER_VARIANT);
			if (variant != null && !variant.equals(AddonNames.nameOf(AddonNames.Kind.CHARACTER, variant))) {
				entity.setData(ModAttachments.VILLAGER_VARIANT, AddonNames.nameOf(AddonNames.Kind.CHARACTER, variant));
			}
			if (entity instanceof Villager villager && villager.hasData(ModAttachments.FROM_VILLAGE_GENERATION)) {
				villager.removeData(ModAttachments.FROM_VILLAGE_GENERATION);
				pending.add(villager);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (pending.isEmpty()) {
				return;
			}
			List<Villager> batch = List.copyOf(pending);
			pending.clear();
			for (Villager villager : batch) {
				if (villager.isAlive() && villager.level() instanceof ServerLevel level) {
					maybeBecomeCharacter(level, villager);
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.level() instanceof ServerLevel level) {
				forget(level, entity);
			}
		});
	}

	private static void maybeBecomeCharacter(ServerLevel level, Villager villager) {
		if (!com.javafied.villagernews.guide.GuideSettings.current().specialVillagers()) {
			return; // turned off in the handbook's settings
		}
		ServerLevel overworld = level.getServer().overworld();
		Map<String, String> placed = placed(overworld);
		List<String> missing = new ArrayList<>();
		for (String character : CHARACTERS) {
			if (!placed.containsKey(character) && BehaviorSensors.definitionOfCharacter(character) != null) {
				missing.add(character);
			}
		}
		if (missing.isEmpty() || !farEnough(level, villager.blockPosition(), placed)) {
			return;
		}
		String character = missing.get(ThreadLocalRandom.current().nextInt(missing.size()));
		Entity placedEntity = character.equals(WOOLY) ? replaceWithWooly(level, villager) : becomeCharacter(level, villager, character);
		if (placedEntity != null) {
			Map<String, String> updated = new HashMap<>(placed);
			updated.put(character, placedEntity.getStringUUID() + "," + placedEntity.getBlockX() + "," + placedEntity.getBlockZ());
			overworld.setData(ModAttachments.SPECIAL_CHARACTERS, Map.copyOf(updated));
		}
	}

	private static Entity becomeCharacter(ServerLevel level, Villager villager, String character) {
		villager.setData(ModAttachments.VILLAGER_VARIANT, character);
		villager.setCustomName(Component.translatable("entity.villagernewsjavafied." + character));
		if (SpecialTrades.hasOwnTrades(character)) {
			SpecialTrades.makeTrader(level, villager);
		}
		return villager;
	}

	private static Entity replaceWithWooly(ServerLevel level, Villager villager) {
		Sheep sheep = EntityType.SHEEP.create(level);
		if (sheep == null) {
			return null;
		}
		sheep.moveTo(villager.getX(), villager.getY(), villager.getZ(), villager.getYRot(), 0);
		sheep.setData(ModAttachments.VILLAGER_VARIANT, WOOLY);
		sheep.setCustomName(Component.translatable("entity.villagernewsjavafied." + WOOLY));
		sheep.setPersistenceRequired();
		villager.discard();
		level.addFreshEntity(sheep);
		return sheep;
	}

	private static boolean farEnough(ServerLevel level, BlockPos pos, Map<String, String> placed) {
		BlockPos spawn = level.getServer().overworld().getSharedSpawnPos();
		if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD && horizontal(spawn.getX(), spawn.getZ(), pos) < MIN_DISTANCE_FROM_SPAWN) {
			return false;
		}
		for (String value : placed.values()) {
			String[] parts = value.split(",");
			if (parts.length == 3 && horizontal(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), pos) < MIN_DISTANCE_APART) {
				return false;
			}
		}
		return true;
	}

	private static double horizontal(int x, int z, BlockPos pos) {
		return Math.hypot(pos.getX() - x, pos.getZ() - z);
	}

	/** Which characters are out in the world, by readable name (older worlds saved the add-on's ids). */
	private static Map<String, String> placed(ServerLevel overworld) {
		Map<String, String> placed = new HashMap<>();
		overworld.getExistingData(ModAttachments.SPECIAL_CHARACTERS).orElse(Map.<String, String>of())
				.forEach((character, where) -> placed.put(AddonNames.nameOf(AddonNames.Kind.CHARACTER, character), where));
		return placed;
	}

	private static void forget(ServerLevel level, Entity entity) {
		ServerLevel overworld = level.getServer().overworld();
		Map<String, String> placed = placed(overworld);
		String uuid = entity.getStringUUID();
		if (placed.values().stream().anyMatch(v -> v.startsWith(uuid + ","))) {
			Map<String, String> updated = new HashMap<>(placed);
			updated.values().removeIf(v -> v.startsWith(uuid + ","));
			overworld.setData(ModAttachments.SPECIAL_CHARACTERS, Map.copyOf(updated));
		}
	}
}
