package com.javafied.villagernews.dialog;

import com.javafied.villagernews.dialog.DialogEngine.Options;
import com.javafied.villagernews.dialog.DialogEngine.State;

import com.javafied.villagernews.platform.ServerLivingEntityEvents;
import com.javafied.villagernews.platform.ServerTickEvents;
import com.javafied.villagernews.platform.UseEntityCallback;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Items;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Hand port of the add-on script's reactions to villager life: growing up,
 * babies being born, babies playing, villagers seeing someone get hurt or a
 * fellow villager die, iron golems picking a fight with a player, and babies
 * being asked to trade.
 */
public final class VillagerLifeReactions {
	private static final String GREW_UP = "grow_up";
	private static final String BORN = "baby_villager_is_born";
	private static final String PARENTS_WELCOME_BABY = "have_a_baby";
	private static final String BABY_SPRINTS = "baby_villager_sprints";
	private static final String BABY_SPRINTS_WEEKEND = "baby_sprints_on_the_weekend";
	private static final String BABIES_PLAY_CHASE = "play_chase";
	private static final String SAW_SOMETHING_HURT = "see_another_entity_get_hurt";
	private static final String VILLAGER_DIES = "villager_dies";
	private static final String SAW_VILLAGER_DIE = "see_another_villager_die";
	private static final String IRON_GOLEM_FIGHTS_PLAYER = "iron_golem_targets_the_player";
	private static final String BABY_ASKED_TO_TRADE = "try_to_trade_with_a_baby_villager";
	private static final String CURED = "cure_a_zombie_villager";
	private static final String CURED_BABY = "cure_a_baby_zombie_villager";
	private static final String SPAWNED_BY_EGG = "spawn_a_villager_with_a_spawn_egg";
	private static final String SPAWNED_BY_EGG_BABY = "spawn_a_baby_with_a_spawn_egg";
	private static final String CELEBRATING = "calm_down_after_danger";
	private static final String CELEBRATING_BABY = "calm_down_after_a_scare";
	private static final String CANNOT_TRADE = "cannot_trade";
	private static final String NITWIT_CANNOT_TRADE = "try_to_trade_with_a_nitwit";
	private static final String UNEMPLOYED_CANNOT_TRADE = "try_to_trade_with_an_unemployed_villager";
	// Time jumping (sleeping through the night, /time set): day, night, or just "time skipped" (adult/baby).
	private static final String SKIPPED_TO_DAY = "suddenly_turns_to_day";
	private static final String SKIPPED_TO_DAY_BABY = "suddenly_turn_to_day";
	private static final String SKIPPED_TO_NIGHT = "suddenly_turns_to_night";
	private static final String SKIPPED_TO_NIGHT_BABY = "suddenly_turn_to_night";
	private static final String TIME_SKIPPED = "time_skips";
	private static final String TIME_SKIPPED_BABY = "skip_time";
	private static final int TIME_SKIP_THRESHOLD = 4000;

	private static final java.util.Set<Villager> celebrating = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
	private static long lastTimeOfDay = -1;

	private VillagerLifeReactions() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!(entity.level() instanceof ServerLevel level) || !entity.isAlive() || damageTaken <= 0) {
				return;
			}
			if (entity instanceof IronGolem && source.getEntity() instanceof ServerPlayer player && !player.isCreative()) {
				Reactions.nearest(level, entity.position(), IRON_GOLEM_FIGHTS_PLAYER,
						Options.DEFAULT.facing(player).ignoringCooldowns(false, false, true));
			} else if (entity instanceof ServerPlayer player && source.getEntity() instanceof IronGolem && !player.isCreative()) {
				Reactions.nearest(level, entity.position(), IRON_GOLEM_FIGHTS_PLAYER,
						Options.DEFAULT.facing(player).ignoringCooldowns(false, false, true));
			}
			Reactions.nearest(level, entity.position(), SAW_SOMETHING_HURT, Options.DEFAULT.facing(entity), Reactions.NEARBY, entity);
		});
		// The add-on's handbook lists a dying villager's final reaction (a gasp, or a laugh), but its script never plays it.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			Speakers.Kind kind = Speakers.kindOf(entity);
			DialogEngine engine = DialogEngine.get();
			if (engine != null && entity instanceof Villager && kind != null) {
				engine.exclaim(entity, VILLAGER_DIES);
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.level() instanceof ServerLevel level && Speakers.kindOf(entity) != null && Speakers.kindOf(entity) != Speakers.Kind.WOOLY) {
				Reactions.nearest(level, entity.position(), SAW_VILLAGER_DIE, Options.DEFAULT.facing(entity.position()));
			}
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(entity instanceof Villager villager) || villager.isSleeping()
					|| !(level instanceof ServerLevel) || Speakers.kindOf(villager) != Speakers.Kind.VILLAGER) {
				return InteractionResult.PASS;
			}
			var held = player.getItemInHand(hand);
			if (Speakers.isBaby(villager)) {
				if (!held.is(Items.SHEARS) && !held.is(Items.NAME_TAG) && !held.is(Items.VILLAGER_SPAWN_EGG)) {
					Reactions.say(villager, BABY_ASKED_TO_TRADE, Options.DEFAULT.withStates(State.BABY).facing(player));
				}
			} else {
				String profession = Speakers.profession(villager);
				if (profession.equals("nitwit") || profession.equals("none")) {
					String special = profession.equals("nitwit") ? NITWIT_CANNOT_TRADE : UNEMPLOYED_CANNOT_TRADE;
					String dialog = java.util.concurrent.ThreadLocalRandom.current().nextBoolean() ? CANNOT_TRADE : special;
					Reactions.say(villager, dialog, Options.DEFAULT.facing(player));
				}
			}
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (DialogEngine.get() == null) {
				return;
			}
			if (server.getTickCount() % 25 == 0) {
				timeSkip(server);
			}
			if (server.getTickCount() % 20 == 0) {
				celebrations(server);
			}
		});
	}

	/** Called (through a mixin) the tick after a zombie villager was cured back into a villager. */
	public static void cured(Villager villager) {
		Reactions.sayByAge(villager, CURED, CURED_BABY, Options.DEFAULT);
	}

	/** Called (through a mixin) the tick after an ordinary villager was spawned from an egg or by command. */
	public static void spawnedByPlayer(Villager villager) {
		if (Speakers.kindOf(villager) == Speakers.Kind.VILLAGER) {
			Reactions.sayByAge(villager, SPAWNED_BY_EGG, SPAWNED_BY_EGG_BABY, Options.DEFAULT.forced().asUrgent());
		}
	}

	/** After a raid is won, celebrating villagers say so. */
	private static void celebrations(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer player : level.players()) {
				for (Villager villager : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(Reactions.NEARBY))) {
					boolean now = villager.getBrain().isActive(Activity.CELEBRATE);
					if (now && celebrating.add(villager)) {
						Reactions.sayByAge(villager, CELEBRATING, CELEBRATING_BABY, Options.DEFAULT);
					} else if (!now) {
						celebrating.remove(villager);
					}
				}
			}
		}
	}

	/** A jump of more than 4000 ticks in the time of day gets comments: about the new day or night, and the skip itself. */
	private static void timeSkip(MinecraftServer server) {
		long time = Math.floorMod(server.overworld().getDayTime(), 24000L);
		long previous = lastTimeOfDay;
		lastTimeOfDay = time;
		if (previous < 0) {
			return;
		}
		long jump = Math.abs(time - previous);
		if (Math.min(jump, 24000 - jump) <= TIME_SKIP_THRESHOLD) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Options options = Options.DEFAULT.facing(player);
			boolean day = time < 11000;
			boolean night = time > 13500 && time < 22500;
			if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() <= 0.7 && (day || night)) {
				Reactions.nearestByAge(player.serverLevel(), player.position(), day ? SKIPPED_TO_DAY : SKIPPED_TO_NIGHT,
						day ? SKIPPED_TO_DAY_BABY : SKIPPED_TO_NIGHT_BABY, options, Reactions.NEARBY, null);
			}
			Reactions.nearestByAge(player.serverLevel(), player.position(), TIME_SKIPPED, TIME_SKIPPED_BABY, options, Reactions.NEARBY, null);
		}
	}

	/** Called (through a mixin) when a baby villager grows up. */
	public static void grewUp(Villager villager) {
		Reactions.say(villager, GREW_UP, Options.DEFAULT);
	}

	/** Called (through a mixin) the tick after two villagers had a baby. */
	public static void born(ServerLevel level, Villager baby) {
		Reactions.say(baby, BORN, Options.DEFAULT.withStates(State.BABY));
		Reactions.nearest(level, baby.position(), PARENTS_WELCOME_BABY, Options.DEFAULT.facing(baby), 2, null);
	}

	/**
	 * From the idle trigger: a baby running around in the daytime says
	 * something - "let's play chase" if another baby is running close by.
	 */
	static boolean babyAtPlay(Villager baby) {
		if (!sprinting(baby)) {
			return false;
		}
		List<Villager> others = baby.level().getEntitiesOfClass(Villager.class, baby.getBoundingBox().inflate(8),
				v -> v != baby && v.distanceTo(baby) <= 8 && sprinting(v));
		DayOfWeek day = LocalDate.now().getDayOfWeek();
		String dialog = !others.isEmpty() ? BABIES_PLAY_CHASE
				: day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY ? BABY_SPRINTS_WEEKEND : BABY_SPRINTS;
		return Reactions.say(baby, dialog, Options.DEFAULT.withStates(State.BABY));
	}

	/** The add-on's "running" baby: fast (0.18 blocks/tick) and in the daytime (ticks 0-11000). */
	private static boolean sprinting(Villager villager) {
		long time = Math.floorMod(villager.level().getDayTime(), 24000L);
		return Speakers.isBaby(villager) && time < 11000 && villager.getDeltaMovement().horizontalDistance() >= 0.18;
	}
}
