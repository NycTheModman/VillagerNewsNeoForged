package com.javafied.villagernews.dialog;

import com.javafied.villagernews.dialog.DialogEngine.Options;
import com.javafied.villagernews.dialog.DialogEngine.State;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The script's building blocks for triggers: "this villager says X", "this
 * villager says the adult or baby version", and "the nearest villager that
 * can, says X" - which is how most world events get a reaction.
 */
public final class Reactions {
	public static final double NEARBY = 16;

	private Reactions() {
	}

	/** The script's {@code ufjrcy}: queue a line for this speaker. */
	public static boolean say(LivingEntity speaker, String dialog, Options options) {
		DialogEngine engine = DialogEngine.get();
		return engine != null && dialog != null && speaker != null && engine.request(speaker, dialog, options);
	}

	/**
	 * The script's {@code hrdsiq}: the adult or the baby version, depending on
	 * the speaker. Either may be null (no line for that age).
	 */
	public static boolean sayByAge(LivingEntity speaker, String adult, String baby, Options options) {
		if (speaker == null) {
			return false;
		}
		boolean isBaby = Speakers.isBaby(speaker);
		return say(speaker, isBaby ? baby : adult, withAge(options, isBaby ? State.BABY : State.ADULT));
	}

	/** The script's {@code tgelfd}: the nearest villager (who said it longest ago) that can, says it. */
	public static LivingEntity nearest(ServerLevel level, Vec3 pos, String dialog, Options options) {
		return nearest(level, pos, dialog, options, NEARBY, null);
	}

	public static LivingEntity nearest(ServerLevel level, Vec3 pos, String dialog, Options options, double range, Entity except) {
		DialogEngine engine = DialogEngine.get();
		if (engine == null || dialog == null) {
			return null;
		}
		for (LivingEntity candidate : candidates(level, pos, dialog, options, range, except)) {
			if (engine.request(candidate, dialog, options)) {
				return candidate;
			}
		}
		return null;
	}

	/** The script's {@code nodrae}: like {@link #nearest}, each candidate saying its age's version. */
	public static LivingEntity nearestByAge(ServerLevel level, Vec3 pos, String adult, String baby, Options options,
			double range, Entity except) {
		DialogEngine engine = DialogEngine.get();
		if (engine == null) {
			return null;
		}
		for (LivingEntity candidate : candidates(level, pos, adult != null ? adult : baby, options, range, except)) {
			if (sayByAge(candidate, adult, baby, options)) {
				return candidate;
			}
		}
		return null;
	}

	private static List<LivingEntity> candidates(ServerLevel level, Vec3 pos, String dialog, Options options, double range,
			Entity except) {
		DialogEngine engine = DialogEngine.get();
		Set<Speakers.Kind> kinds = EnumSet.copyOf(Speakers.NEARBY_KINDS);
		kinds.retainAll(options.kinds());
		return level.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(range),
						e -> e != except && e.position().distanceTo(pos) <= range && kinds.contains(Speakers.kindOf(e))
								&& (options.interrupt() || engine.available(e, dialog, options)))
				.stream()
				.sorted(Comparator.comparingLong(e -> engine.lastSaid(e, dialog)))
				.toList();
	}

	/** Keeps the request's sleep/danger allowances, replacing its age. */
	private static Options withAge(Options options, State age) {
		Options aged = options.withStates(age);
		for (State extra : List.of(State.SLEEPING, State.EVEN_IN_DANGER)) {
			if (options.states().contains(extra)) {
				aged = aged.alsoWhen(extra);
			}
		}
		return aged;
	}
}
