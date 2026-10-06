package com.javafied.villagernews.dialog;

import com.javafied.villagernews.behavior.BehaviorProperties;
import com.javafied.villagernews.behavior.BehaviorSensors;
import com.javafied.villagernews.content.ModAttachments;
import com.javafied.villagernews.names.AddonNames;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Who can speak, in the add-on's terms: ordinary villagers, each special
 * character, the wandering trader and Wooly. Many lines belong to one kind
 * (only the Mayor says his), and "the nearest villager reacts" skips the
 * trader and Wooly unless a line asks for them.
 */
public final class Speakers {
	public enum Kind {
		VILLAGER, MAYOR, TESTIFICATE_MAN, NUMBER_5, NUMBER_9, UNTOUCHABLE, TRADER, WOOLY
	}

	/** The script's default speaker families: everyone but Wooly. */
	public static final Set<Kind> DEFAULT_KINDS = Set.copyOf(EnumSet.complementOf(EnumSet.of(Kind.WOOLY)));
	/** Kinds the "nearest villager reacts" search considers (the script excludes the trader and Wooly there). */
	public static final Set<Kind> NEARBY_KINDS = Set.copyOf(EnumSet.complementOf(EnumSet.of(Kind.WOOLY, Kind.TRADER)));

	/** The special characters, by the name the villager variant attachment gives them. */
	private static final Map<String, Kind> VARIANTS = Map.of("mayor", Kind.MAYOR, "testificate_man", Kind.TESTIFICATE_MAN,
			"villager_5", Kind.NUMBER_5, "villager_9", Kind.NUMBER_9, "untouchable", Kind.UNTOUCHABLE, "wandering_trader", Kind.TRADER,
			"wooly", Kind.WOOLY);

	private Speakers() {
	}

	/** Null if this entity never speaks. */
	public static Kind kindOf(Entity entity) {
		if (entity instanceof WanderingTrader) {
			return Kind.TRADER;
		}
		String variant = entity.getExistingDataOrNull(ModAttachments.VILLAGER_VARIANT);
		if (entity instanceof Villager) {
			return variant == null ? Kind.VILLAGER : VARIANTS.getOrDefault(variant, Kind.VILLAGER);
		}
		if (entity instanceof Sheep && "wooly".equals(variant)) {
			return Kind.WOOLY;
		}
		return null;
	}

	/** The add-on lets players shear a villager's nose off (its {@code nose} property). */
	public static boolean hasNose(Entity entity) {
		return !"false".equals(BehaviorProperties.read(entity, AddonNames.property("nose")));
	}

	/** Bedrock's {@code is_baby}, which counts the Mayor: see {@link BehaviorSensors#isBaby}. */
	public static boolean isBaby(Entity entity) {
		return BehaviorSensors.isBaby(entity);
	}

	/** Ordinary villagers' profession, in Bedrock's names for the ones that differ ("none" for unemployed). */
	public static String profession(Entity entity) {
		if (!(entity instanceof Villager villager)) {
			return "";
		}
		return net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()).getPath();
	}
}
