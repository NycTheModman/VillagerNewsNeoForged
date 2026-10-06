package com.javafied.villagernews.dialog;

import com.javafied.villagernews.behavior.BehaviorDefinitions;
import com.javafied.villagernews.behavior.BehaviorProperties;
import com.javafied.villagernews.behavior.BehaviorSensors;
import com.javafied.villagernews.content.ModAttachments;
import com.javafied.villagernews.dialog.DialogEngine.Options;

import com.google.gson.JsonPrimitive;

import com.javafied.villagernews.platform.ServerTickEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Untouchable villager, after its behavior-pack entity: it keeps away
 * from players - running from any within 16 blocks, vanishing to somewhere
 * nearby when one gets within 4 - strikes a pose depending on which side the
 * player is on, and taunts them while it runs. Its taunt comes from the
 * add-on script, the rest from the entity's components and controllers.
 *
 * <p>Also keeps every villager's "avoiding" flag, Bedrock's
 * {@code q.is_avoiding_mobs}: the resource pack's running-away animations
 * (a panicking villager's too) need it on the client.
 */
public final class UntouchableReactions {
	private static final String TAUNT = "try_to_reach_the_untouchable_villager";
	/** Which pose it strikes (0 none, 1-5 by the player's side). */
	private static final String PROPERTY_POSE = "dodge_pose";
	private static final double FLEE_RANGE = 16;
	private static final double POSE_RANGE = 12;
	private static final double VANISH_RANGE = 4;
	/** Bedrock's teleport cube is 18 x 5 x 18 around it. */
	private static final double VANISH_SPREAD = 18;
	private static final int VANISH_HEIGHT = 2;
	/** Like a vanilla villager in a panic. */
	private static final float FLEE_SPEED = 0.75f;
	/** The controller taunts once it has been running for 6 seconds, then rests 12 before counting again. */
	private static final int TAUNT_AFTER_TICKS = 6 * 20;
	private static final int TAUNT_REST_TICKS = 12 * 20;
	private static final int POSE_EVERY_TICKS = 10;
	/** How far from players villagers' avoiding flag is kept up to date (they're drawn further out than they talk). */
	private static final double FLAG_RANGE = 48;

	private static final Map<Villager, Long> nextTaunt = new WeakHashMap<>();
	private static final Map<Villager, WalkTarget> fleeingTo = new WeakHashMap<>();
	private static final Set<Villager> flagged = Collections.newSetFromMap(new WeakHashMap<>());

	private UntouchableReactions() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(UntouchableReactions::tick);
	}

	private static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		if (now % 2 != 0) {
			return;
		}
		Set<Villager> seen = new HashSet<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer player : level.players()) {
				seen.addAll(level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(FLAG_RANGE)));
			}
		}
		for (Villager villager : seen) {
			boolean keepingAway = Speakers.kindOf(villager) == Speakers.Kind.UNTOUCHABLE
					&& keepAway((ServerLevel) villager.level(), villager, now);
			setAvoiding(villager, keepingAway || villager.getBrain().isActive(Activity.PANIC));
		}
		for (Villager villager : Set.copyOf(flagged)) {
			if (!seen.contains(villager)) {
				setAvoiding(villager, false);
			}
		}
	}

	/** @return whether it is running from a player */
	private static boolean keepAway(ServerLevel level, Villager villager, long now) {
		Player player = villager.isSleeping() || villager.isPassenger() ? null
				: level.getNearestPlayer(villager.getX(), villager.getY(), villager.getZ(), FLEE_RANGE, EntitySelector.NO_SPECTATORS);
		if (player == null) {
			nextTaunt.remove(villager);
			fleeingTo.remove(villager);
			setPose(villager, 0);
			return false;
		}
		double distance = villager.distanceTo(player);
		if (distance <= VANISH_RANGE) {
			vanish(level, villager, player);
			return true;
		}
		flee(villager, player);
		if (distance > POSE_RANGE) {
			setPose(villager, 0);
		} else if (now % POSE_EVERY_TICKS == 0) {
			setPose(villager, pose(villager, player));
		}
		long due = nextTaunt.computeIfAbsent(villager, v -> now + TAUNT_AFTER_TICKS);
		if (now >= due && villager.getDeltaMovement().horizontalDistance() > 0.05) {
			nextTaunt.put(villager, now + TAUNT_REST_TICKS + TAUNT_AFTER_TICKS);
			DialogEngine engine = DialogEngine.get();
			if (engine != null) {
				engine.speakNow(villager, TAUNT, Options.DEFAULT.ignoringCooldowns(true, true, true));
			}
		}
		return true;
	}

	/** Heads somewhere away from the player, unless already on the way. */
	private static void flee(Villager villager, Player player) {
		Brain<Villager> brain = villager.getBrain();
		WalkTarget current = brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
		if (current != null && current == fleeingTo.get(villager)) {
			return;
		}
		Vec3 away = LandRandomPos.getPosAway(villager, (int) FLEE_RANGE, 7, player.position());
		if (away != null) {
			WalkTarget target = new WalkTarget(away, FLEE_SPEED, 0);
			brain.setMemory(MemoryModuleType.WALK_TARGET, target);
			fleeingTo.put(villager, target);
		}
	}

	/** Bedrock's teleport component: a random spot in the cube around it - here, one not right by the player. */
	private static void vanish(ServerLevel level, Villager villager, Player player) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int attempt = 0; attempt < 16; attempt++) {
			double x = villager.getX() + (random.nextDouble() - 0.5) * VANISH_SPREAD;
			double y = villager.getY() + random.nextInt(-VANISH_HEIGHT, VANISH_HEIGHT + 1);
			double z = villager.getZ() + (random.nextDouble() - 0.5) * VANISH_SPREAD;
			if (player.distanceToSqr(x, y, z) > (VANISH_RANGE + 2) * (VANISH_RANGE + 2) && villager.randomTeleport(x, y, z, true)) {
				villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				fleeingTo.remove(villager);
				return;
			}
		}
	}

	/** The script's {@code njvfsf}: which way the player is from where it's looking, as a pose number. */
	static int pose(Villager villager, Player player) {
		Vec3 view = villager.getViewVector(1);
		double dx = player.getX() - villager.getX();
		double dz = player.getZ() - villager.getZ();
		double angle = Math.toDegrees(Math.atan2(view.x * dz - view.z * dx, view.x * dx + view.z * dz));
		if (angle < 0) {
			angle += 360;
		}
		return angle >= 75 && angle <= 105 ? 3 : angle >= 255 && angle <= 285 ? 5 : angle >= 105 && angle <= 165 ? 2
				: angle >= 195 && angle <= 255 ? 4 : 1;
	}

	private static void setPose(Villager villager, int pose) {
		BehaviorDefinitions.Definition definition = BehaviorSensors.definitionOf(villager);
		if (definition == null) {
			return;
		}
		BehaviorProperties properties = new BehaviorProperties(villager, definition);
		if (!(properties.named(PROPERTY_POSE) instanceof Double current) || current.intValue() != pose) {
			properties.setNamed(PROPERTY_POSE, new JsonPrimitive(pose));
		}
	}

	private static void setAvoiding(Villager villager, boolean avoiding) {
		if (avoiding && flagged.add(villager)) {
			villager.setData(ModAttachments.AVOIDING, true);
		} else if (!avoiding && flagged.remove(villager)) {
			villager.removeData(ModAttachments.AVOIDING);
		}
	}
}
