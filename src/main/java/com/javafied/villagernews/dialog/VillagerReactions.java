package com.javafied.villagernews.dialog;

import com.javafied.villagernews.dialog.DialogEngine.Options;
import com.javafied.villagernews.dialog.DialogEngine.Speech;

import com.javafied.villagernews.platform.ServerLivingEntityEvents;
import com.javafied.villagernews.platform.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.vehicle.Boat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hand port of the add-on script's villager triggers - the reasons a villager
 * speaks up. Dialogs go by their readable names (see {@link DialogLibrary}).
 */
public final class VillagerReactions {
	// Idle chatter, by context (the script's txggyx).
	private static final String OTHER_DIMENSION = "wander_in_another_dimension";
	private static final String IN_THE_END = "wander_in_the_end";
	private static final String IN_THE_NETHER = "wander_in_the_nether";
	private static final String UP_HIGH = "wander_high_above_the_ground";
	private static final String UNDERGROUND = "wander_deep_underground";
	private static final String COLD_BIOME = "wander_somewhere_cold";
	private static final String HOT_BIOME = "wander_somewhere_hot";
	private static final String NITWIT = "nitwit_wandering";
	private static final String UNEMPLOYED = "unemployed_villager_wandering";
	private static final String EMPLOYED = "villager_wandering";
	private static final String SEES_EXPERIENCE_ORBS = "see_xp_orbs";
	// Real-world calendar chatter (the script's siigqd).
	private static final Map<DayOfWeek, String> WEEKDAY = Map.of(DayOfWeek.SUNDAY, "wander_on_a_sunday", DayOfWeek.MONDAY, "wander_on_a_monday",
			DayOfWeek.TUESDAY, "wander_on_a_tuesday", DayOfWeek.WEDNESDAY, "wander_on_a_wednesday", DayOfWeek.THURSDAY, "wander_on_a_thursday",
			DayOfWeek.FRIDAY, "wander_on_a_friday", DayOfWeek.SATURDAY, "wander_on_a_saturday");
	private static final String WEEKEND = "wander_on_the_weekend";
	private static final String OCTOBER = "wander_in_october";
	private static final String DECEMBER = "wander_in_december";
	private static final String APRIL_FOOLS = "wander_on_april_fools_day";
	private static final String NEW_YEARS_EVE = "wander_on_new_years_eve";
	/** Conversations both villagers can have with their noses on (the others are about losing one). */
	private static final String GROUP_NOSES = "both_noses";
	// Dialog tags the hurt reaction respects.
	private static final String TAG_NO_HURT_VOICE = "no_hurt_voice";
	private static final String TAG_KEEPS_TALKING = "keeps_talking";
	private static final String GETS_HURT = "villager_gets_hurt";
	private static final String BABY_GETS_HURT = "baby_villager_gets_hurt";

	private static final Set<String> COLD_BIOMES = Set.of("snowy_beach", "snowy_taiga", "deep_cold_ocean",
			"deep_frozen_ocean", "frozen_ocean", "frozen_peaks", "frozen_river", "snowy_plains", "ice_spikes",
			"jagged_peaks", "snowy_slopes");
	private static final Set<String> HOT_BIOMES = Set.of("desert", "badlands", "wooded_badlands", "eroded_badlands");

	/** The add-on's idle timer re-arms every 19-37 seconds per villager. */
	private static final int CHATTER_MIN_TICKS = 19 * 20;
	private static final int CHATTER_MAX_TICKS = 37 * 20;
	/** The trader's own timer: 23-41 seconds. */
	private static final int TRADER_MIN_TICKS = 23 * 20;
	private static final int TRADER_MAX_TICKS = 41 * 20;
	/** The special characters' idle lines (#9 half the time talks about holding his microphone instead). */
	private static final Map<Speakers.Kind, String> CHARACTER_IDLE = Map.of(Speakers.Kind.MAYOR, "mayor_idle",
			Speakers.Kind.TESTIFICATE_MAN, "testificate_man_idle", Speakers.Kind.NUMBER_5, "villager_5_idle", Speakers.Kind.NUMBER_9, "villager_9_idle",
			Speakers.Kind.WOOLY, "wooly_idle");
	private static final String NUMBER_9_MICROPHONE = "holding_the_microphone";
	private static final String TRADER_SEES_CUSTOMER = "wandering_trader_sees_customer";
	private static final String TRADER_INVISIBLE = "invisible_wandering_trader";
	private static final String TRADER_IDLE = "wandering_trader_idle";
	private static final String NO_NOSE = "no_nose_villager_wandering";
	/** Conversation groups by noses: both villagers have theirs / neither / one of them. */
	private static final String GROUP_NO_NOSES = "no_noses";
	private static final String GROUP_ONE_NOSE = "one_nose";
	/**
	 * "Ah, what happened to your nose?" - the script's table lists that line as
	 * its own answer, leaving the nose-less villager's reply unused; restored.
	 */
	private static final Map<String, List<String>> RESTORED_CONVERSATIONS = Map.of("one_nose_chat_3",
			List.of("one_nose_chat_3", "one_nose_chat_3_part_2"));
	private static final double CONVERSATION_DISTANCE = 2.5;
	private static final int CONVERSATION_REST_TICKS = 1400;

	private static final Map<LivingEntity, Long> nextChatter = new WeakHashMap<>();
	private static final Map<Villager, Conversation> conversations = new WeakHashMap<>();
	private static final Map<Villager, Long> lastConversation = new WeakHashMap<>();

	/**
	 * Two villagers taking turns through a conversation's parts; the parts at
	 * {@code sameSpeaker} indexes are said by whoever said the part before.
	 */
	private record Conversation(Villager first, Villager second, List<String> parts, Set<Integer> sameSpeaker) {
		Villager other(Villager v) {
			return v == first ? second : first;
		}

		void end() {
			conversations.remove(first, this);
			conversations.remove(second, this);
		}
	}

	private VillagerReactions() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(VillagerReactions::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof Villager villager && damageTaken > 0) {
				hurt(villager, source);
			}
		});
		DialogEngine.onFinished(VillagerReactions::continueConversation);
	}

	private static void tick(MinecraftServer server) {
		DialogEngine engine = DialogEngine.get();
		if (engine == null || engine.library().isEmpty() || server.getTickCount() % 20 != 0) {
			return;
		}
		long now = engine.now();
		for (ServerLevel level : server.getAllLevels()) {
			Set<LivingEntity> nearPlayers = new HashSet<>();
			for (ServerPlayer player : level.players()) {
				nearPlayers.addAll(level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(DialogEngine.RANGE),
						e -> Speakers.kindOf(e) != null));
			}
			for (LivingEntity speaker : nearPlayers) {
				Long due = nextChatter.get(speaker);
				if (due == null || now >= due) {
					boolean trader = Speakers.kindOf(speaker) == Speakers.Kind.TRADER;
					nextChatter.put(speaker, now + ThreadLocalRandom.current().nextInt(trader ? TRADER_MIN_TICKS : CHATTER_MIN_TICKS,
							(trader ? TRADER_MAX_TICKS : CHATTER_MAX_TICKS) + 1));
					if (due != null) {
						chatter(engine, speaker);
					}
				}
			}
		}
	}

	/** The script's idle trigger ({@code vszwnq}), by who's idling. */
	private static void chatter(DialogEngine engine, LivingEntity speaker) {
		Speakers.Kind kind = Speakers.kindOf(speaker);
		if (kind == Speakers.Kind.VILLAGER) {
			villagerChatter(engine, (Villager) speaker);
		} else if (kind == Speakers.Kind.TRADER) {
			traderChatter(engine, speaker);
		} else if (CHARACTER_IDLE.containsKey(kind)) {
			String line = kind == Speakers.Kind.NUMBER_9 && ThreadLocalRandom.current().nextBoolean() ? NUMBER_9_MICROPHONE
					: CHARACTER_IDLE.get(kind);
			Options options = Options.DEFAULT.ignoringCooldowns(false, true, false).withKinds(kind);
			if (kind == Speakers.Kind.MAYOR) {
				options = options.withStates(DialogEngine.State.BABY);
			}
			engine.request(speaker, line, options);
		}
	}

	/** Ordinary villagers: the odd calendar remark, else a chat with a neighbour or a comment. */
	private static void villagerChatter(DialogEngine engine, Villager villager) {
		if (Speakers.isBaby(villager)) {
			VillagerLifeReactions.babyAtPlay(villager);
			return;
		}
		if (WorldReactions.headingHome(villager) || seesExperienceOrbs(villager)) {
			return;
		}
		if (villager.getVehicle() instanceof Boat || VillagerRoutineReactions.eveningGathering(engine, villager)) {
			return;
		}
		if (WorkReactions.atWorkHours(villager) && ThreadLocalRandom.current().nextBoolean()
				&& WorkReactions.chatter((ServerLevel) villager.level(), villager)) {
			return;
		}
		if (ThreadLocalRandom.current().nextDouble() > 0.8) {
			String dialog = pick(calendarDialogs(LocalDate.now()));
			if (dialog != null) {
				engine.request(villager, dialog, Options.DEFAULT.ignoringCooldowns(false, true, false));
			}
			return;
		}
		if (conversations.containsKey(villager)) {
			return;
		}
		Villager partner = partner(engine, villager);
		Long last = lastConversation.get(villager);
		if (partner != null && (last == null || engine.now() - last >= CONVERSATION_REST_TICKS)) {
			startConversation(engine, villager, partner);
			return;
		}
		engine.request(villager, contextDialog(villager), Options.DEFAULT.ignoringCooldowns(false, true, false));
	}

	/**
	 * The trader, every 23-41 seconds: "ah, a customer" if a player within 16
	 * blocks is in his view (120 degrees), a remark about being invisible,
	 * or just about the day.
	 */
	private static void traderChatter(DialogEngine engine, LivingEntity trader) {
		Player customer = null;
		for (Player player : trader.level().getEntitiesOfClass(Player.class, trader.getBoundingBox().inflate(16))) {
			net.minecraft.world.phys.Vec3 to = player.position().subtract(trader.position()).normalize();
			if (!player.isSpectator() && player.distanceTo(trader) <= 16 && trader.getViewVector(1).dot(to) >= Math.cos(Math.toRadians(60))) {
				customer = player;
				break;
			}
		}
		List<String> options = new ArrayList<>();
		if (customer != null) {
			options.add(TRADER_SEES_CUSTOMER);
		}
		if (trader.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
			options.add(TRADER_INVISIBLE);
		}
		String line = options.isEmpty() ? TRADER_IDLE : pick(options);
		engine.speakNow(trader, line, customer == null ? Options.DEFAULT : Options.DEFAULT.facing(customer));
	}

	/** The script's idle check for experience orbs: four or more within 12 blocks. */
	private static boolean seesExperienceOrbs(Villager villager) {
		List<net.minecraft.world.entity.ExperienceOrb> orbs = villager.level().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,
				villager.getBoundingBox().inflate(12), orb -> orb.distanceTo(villager) <= 12);
		return orbs.size() >= 4 && Reactions.say(villager, SEES_EXPERIENCE_ORBS, Options.DEFAULT.facing(villager.position()));
	}

	/** Weekday remarks, weekend, October, December, and a couple of special dates. The add-on meant to; see below. */
	public static List<String> calendarDialogs(LocalDate date) {
		// The add-on's minifier renamed the weekday and date keys of these tables but not the strings
		// looked up in them, so in Bedrock only the October/December/weekend ones ever play.
		// The weekday and date ones are restored here as intended.
		List<String> dialogs = new ArrayList<>();
		dialogs.add(WEEKDAY.get(date.getDayOfWeek()));
		if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
			dialogs.add(WEEKEND);
		}
		if (date.getMonth() == Month.OCTOBER) {
			dialogs.add(OCTOBER);
		}
		if (date.getMonth() == Month.DECEMBER) {
			dialogs.add(DECEMBER);
		}
		if (date.getMonth() == Month.APRIL && date.getDayOfMonth() == 1) {
			dialogs.add(APRIL_FOOLS);
		}
		if (date.getMonth() == Month.DECEMBER && date.getDayOfMonth() == 31) {
			dialogs.add(NEW_YEARS_EVE);
		}
		return dialogs;
	}

	/** Where the villager is and what it does for a living (the script's {@code txggyx}). */
	private static String contextDialog(Villager villager) {
		List<String> options = new ArrayList<>();
		Level level = villager.level();
		ResourceKey<Level> dimension = level.dimension();
		if (dimension != Level.OVERWORLD) {
			options.add(OTHER_DIMENSION);
			if (dimension == Level.END) {
				options.add(IN_THE_END);
			} else if (dimension == Level.NETHER) {
				options.add(IN_THE_NETHER);
			}
		}
		BlockPos pos = villager.blockPosition();
		if (villager.getY() > 190) {
			options.add(UP_HIGH);
		} else if (villager.getY() < 60 && !level.canSeeSky(pos.above())) {
			options.add(UNDERGROUND);
		}
		String biome = level.getBiome(pos).unwrapKey().map(ResourceKey::location).map(id -> id.getPath()).orElse("");
		if (COLD_BIOMES.contains(biome)) {
			options.add(COLD_BIOME);
		} else if (HOT_BIOMES.contains(biome)) {
			options.add(HOT_BIOME);
		}
		if (!Speakers.hasNose(villager)) {
			options.add(NO_NOSE);
		}
		options.add(switch (profession(villager)) {
			case "nitwit" -> NITWIT;
			case "none" -> UNEMPLOYED;
			default -> EMPLOYED;
		});
		return pick(options);
	}

	/**
	 * A villager within 2.5 blocks who's at work (the script's {@code guicgv}),
	 * not busy talking and not already in a conversation.
	 */
	private static Villager partner(DialogEngine engine, Villager villager) {
		Villager nearest = null;
		double best = Double.MAX_VALUE;
		for (Villager other : villager.level().getEntitiesOfClass(Villager.class,
				villager.getBoundingBox().inflate(CONVERSATION_DISTANCE))) {
			double distance = other.distanceTo(villager);
			if (other != villager && distance <= CONVERSATION_DISTANCE && distance < best) {
				nearest = other;
				best = distance;
			}
		}
		if (nearest == null || Speakers.isBaby(nearest) || engine.isTalking(nearest) || conversations.containsKey(nearest)
				|| !atWork(nearest)) {
			return null;
		}
		return nearest;
	}

	/** The script's working hours: day ticks 0-8000 and 10000-12000 (unemployed 10000-11000, nitwits 2000-12000). */
	private static boolean atWork(Villager villager) {
		long time = villager.level().getDayTime() % 24000;
		return switch (profession(villager)) {
			case "none" -> time < 8000 || time >= 10000 && time < 11000;
			case "nitwit" -> time >= 2000 && time < 12000;
			default -> time < 8000 || time >= 10000 && time < 12000;
		};
	}

	private static void startConversation(DialogEngine engine, Villager villager, Villager partner) {
		boolean mine = Speakers.hasNose(villager);
		boolean theirs = Speakers.hasNose(partner);
		String group = mine && theirs ? GROUP_NOSES : !mine && !theirs ? GROUP_NO_NOSES : GROUP_ONE_NOSE;
		List<List<String>> starters = engine.library().conversations().stream()
				.filter(parts -> engine.library().get(parts.getFirst()) != null
						&& group.equals(engine.library().get(parts.getFirst()).group())).toList();
		if (starters.isEmpty()) {
			return;
		}
		String first = starters.get(ThreadLocalRandom.current().nextInt(starters.size())).getFirst();
		// Between a villager with a nose and one without, the one with it opens ("Got your nose!").
		if (group.equals(GROUP_ONE_NOSE) && !mine) {
			startConversation(engine, partner, villager, first);
		} else {
			startConversation(engine, villager, partner, first);
		}
	}

	/** Starts the conversation beginning with this dialog (or just that one line, if it starts none). */
	static void startConversation(DialogEngine engine, Villager villager, Villager partner, String first) {
		startConversation(engine, villager, partner, first, Set.of());
	}

	static void startConversation(DialogEngine engine, Villager villager, Villager partner, String first, Set<Integer> sameSpeaker) {
		List<String> parts = RESTORED_CONVERSATIONS.getOrDefault(first, engine.library().conversations().stream()
				.filter(c -> c.getFirst().equals(first)).findFirst().orElse(List.of(first)));
		if (engine.speakNow(villager, first, Options.DEFAULT.facing(partner))) {
			Conversation conversation = new Conversation(villager, partner, parts, sameSpeaker);
			conversations.put(villager, conversation);
			conversations.put(partner, conversation);
			lastConversation.put(villager, engine.now());
			lastConversation.put(partner, engine.now());
		}
	}

	/**
	 * When one part of a conversation ends, the other villager answers with the
	 * next. (The add-on looks the next part up by a numbered dialog id its own
	 * minifier renamed, so in Bedrock the answer never comes; restored here.)
	 */
	private static void continueConversation(Speech speech, boolean completed) {
		if (!(speech.speaker() instanceof Villager speaker)) {
			return;
		}
		Conversation conversation = conversations.get(speaker);
		if (conversation == null) {
			return;
		}
		int part = conversation.parts().indexOf(speech.dialog().id());
		Villager other = conversation.other(speaker);
		boolean again = conversation.sameSpeaker().contains(part + 1);
		Villager listener = again ? speaker : other;
		Villager facing = again ? other : speaker;
		DialogEngine engine = DialogEngine.get();
		boolean answered = completed && engine != null && part >= 0 && part + 1 < conversation.parts().size()
				&& other.isAlive() && other.distanceTo(speaker) <= CONVERSATION_DISTANCE
				&& engine.speakNow(listener, conversation.parts().get(part + 1),
				Options.DEFAULT.facing(facing).ignoringCooldowns(true, true, false).asUrgent());
		if (!answered) {
			conversation.end();
		}
	}

	/**
	 * A pained voice line, and whatever it was saying gets cut off (the script's
	 * entityHurt handler). The script's table pairs each hurt sound with a line
	 * of "Villager Gets Hurt" (or the baby one) to lip-sync to, but names the
	 * animation wrongly, so in Bedrock the mouth never moves; saying the line
	 * itself - the same recording - restores that, with its subtitle.
	 */
	private static void hurt(Villager villager, DamageSource source) {
		DialogEngine engine = DialogEngine.get();
		if (engine == null || !villager.isAlive()) {
			return;
		}
		engine.markHurt(villager, source);
		Speech speech = engine.speech(villager);
		boolean keepsTalking = speech != null && speech.dialog().tags().containsKey(TAG_KEEPS_TALKING);
		if (speech != null && !keepsTalking) {
			engine.stop(villager);
		}
		if (speech != null && speech.dialog().tags().containsKey(TAG_NO_HURT_VOICE)) {
			return;
		}
		if (keepsTalking || !engine.exclaim(villager, Speakers.isBaby(villager) ? BABY_GETS_HURT : GETS_HURT)) {
			String sound = pick(engine.library().hurtSounds(Speakers.isBaby(villager)));
			if (sound != null) {
				villager.level().playSound(null, villager.getX(), villager.getY(), villager.getZ(),
						BedrockSoundIds.holder(sound), SoundSource.NEUTRAL, 1f, 1f);
			}
		}
	}

	/** Bedrock profession names mapped from Java's ("none" = unemployed). */
	private static String profession(Villager villager) {
		return net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()).getPath();
	}

	private static <T> T pick(List<T> options) {
		return options.isEmpty() ? null : options.get(ThreadLocalRandom.current().nextInt(options.size()));
	}
}
