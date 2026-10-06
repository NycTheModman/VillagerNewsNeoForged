package com.javafied.villagernews.dialog;

import com.javafied.villagernews.content.ModAttachments;
import com.javafied.villagernews.dialog.DialogEngine.Options;
import com.javafied.villagernews.dialog.DialogEngine.State;
import com.javafied.villagernews.dialog.Speakers.Kind;

import com.javafied.villagernews.platform.ServerTickEvents;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hand port of the add-on script's "noticing" system: every 3 seconds each
 * speaker may notice one thing around it and comment - a player looking at
 * it (greetings by reputation, the calendar, the time of day; the special
 * characters have their own), a mob (dozens of kinds, adult and baby), a
 * pile of dropped items, a crowd, the ender dragon. The same thing isn't
 * remarked on again for 5 minutes (30 seconds for the dragon).
 */
public final class NoticeReactions {
	/** What a speaker says about something: grown-ups' line and the "second voice" (babies, the Mayor). */
	private record Remark(String adult, String second) {
		static Remark adult(String line) {
			return new Remark(line, null);
		}
	}

	private static final Map<String, Remark> MOBS = new HashMap<>();
	private static final Map<String, Remark> BABY_MOBS = new HashMap<>();

	static {
		String[][] adults = {{"allay", "allay"}, {"armor_stand", "place_an_armor_stand"}, {"bat", "bat"}, {"bogged", "bogged"},
				{"camel", "camel"}, {"cat", "cat"}, {"cave_spider", "spider"}, {"cod", "fish"}, {"copper_golem", "copper_golem"},
				{"cow", "cow"}, {"creaking", "creaking"}, {"creeper", "creeper"}, {"dolphin", "dolphin"}, {"drowned", "drowned"},
				{"ender_dragon", "ender_dragon"}, {"enderman", "enderman"}, {"frog", "frog"}, {"happy_ghast", "happy_ghast"},
				{"horse", "horse"}, {"husk", "husk"}, {"llama", "llama"}, {"panda", "panda"}, {"parrot", "parrot"},
				{"phantom", "phantom"}, {"pig", "pig"}, {"polar_bear", "polar_bear"}, {"pufferfish", "fish"},
				{"rabbit", "rabbit"}, {"salmon", "fish"}, {"skeleton", "skeleton"}, {"slime", "slime"}, {"sniffer", "sniffer"},
				{"snow_golem", "snow_golem"}, {"spider", "spider"}, {"stray", "stray"}, {"sulfur_cube", "sulfur_cube"},
				{"trader_llama", "llama"}, {"tropical_fish", "fish"}, {"turtle", "turtle"}, {"warden", "warden"},
				{"witch", "witch_nearby"}, {"wither", "wither"}, {"zombified_piglin", "zombie_piglin"}, {"zombie_villager", "zombie_villager"}};
		for (String[] entry : adults) {
			MOBS.put(entry[0], Remark.adult(entry[1]));
		}
		MOBS.put("iron_golem", new Remark("iron_golem", "see_an_iron_golem"));
		String[][] babies = {{"cat", "baby_cat"}, {"cow", "baby_cow"}, {"drowned", "baby_drowned"}, {"horse", "baby_horse"},
				{"husk", "baby_husk"}, {"panda", "baby_panda"}, {"pig", "baby_pig"}, {"zombified_piglin", "baby_zombie_piglin"},
				{"zombie_villager", "baby_zombie_villager"}};
		for (String[] entry : babies) {
			BABY_MOBS.put(entry[0], Remark.adult(entry[1]));
		}
	}

	/** Meeting the special characters. (The add-on files the Mayor's under a slot it never reads; restored.) */
	private static final Map<Kind, String> MEET_CHARACTER = Map.of(Kind.TESTIFICATE_MAN, "meet_testificate_man", Kind.MAYOR, "meet_the_mayor",
			Kind.NUMBER_5, "meet_villager_5", Kind.NUMBER_9, "meet_villager_9");
	private static final String ANGRY_BEE = "angry_bee";
	private static final String BABY_BEE = "baby_bee";
	private static final String BEE = "bee";
	private static final String TAMED_BABY_WOLF = "tamed_baby_wolf";
	private static final String TAMED_WOLF = "tamed_wolf";
	private static final String BABY_WOLF = "baby_wolf";
	private static final String WOLF = "wolf";
	private static final String SHEARED_SHEEP = "sheared_sheep";
	private static final String BABY_SHEEP = "baby_sheep";
	private static final String SHEEP = "sheep";
	private static final String BABY_ZOMBIE = "baby_zombie";
	private static final String ZOMBIE = "zombie_nearby";
	private static final String JOCKEY = "jockey";
	private static final String BABY_CHICKEN = "baby_chicken";
	private static final String CHICKEN = "chicken";
	private static final String VILLAGER_IN_TESTIFICATE_HELMET = "villager_wears_testificate_mans_helmet";
	private static final String VILLAGER_IN_COSMETIC = "see_a_villager_wearing_a_cosmetic";
	private static final String DROPPED_ITEMS = "see_a_pile_of_dropped_items";
	private static final String BABY_VILLAGER = "see_a_baby_villager";
	private static final String UNKNOWN_MOB = "see_an_unknown_mob";
	private static final String CROWD = "crowd_too_many_villagers_together";

	// Greeting players who look at a villager, by reputation band (adult line / baby line).
	private static final String REPUTATION_EXTREMELY_HIGH = "extremely_high_reputation";
	private static final String REPUTATION_HIGH = "high_reputation";
	private static final String APPROACHED = "approach_a_villager";
	private static final String REPUTATION_LOW = "low_reputation";
	private static final String REPUTATION_EXTREMELY_LOW = "extremely_low_reputation";
	private static final String REPUTATION_EXTREMELY_LOW_ARMED = "extremely_low_reputation_with_a_sword";
	private static final String BABY_MEETS_PLAYER = "meet_a_player";
	private static final String BABY_MEETS_DISLIKED_PLAYER = "see_a_player_with_a_bad_reputation";
	/** Real-world calendar greetings. (The add-on's weekday and date keys were mangled by its minifier; restored.) */
	private static final Map<DayOfWeek, String> WEEKDAY_GREETING = Map.of(DayOfWeek.SUNDAY, "sunday",
			DayOfWeek.MONDAY, "monday", DayOfWeek.TUESDAY, "tuesday", DayOfWeek.WEDNESDAY, "wednesday",
			DayOfWeek.THURSDAY, "thursday", DayOfWeek.FRIDAY, "friday", DayOfWeek.SATURDAY, "saturday");
	private static final String WEEKEND_GREETING = "weekend";
	private static final String TOP_OF_THE_HOUR = "top_of_the_hour";
	private static final String FRIDAY_13TH = "friday_the_13th";
	private static final Map<String, String> HOLIDAY_GREETING = Map.of("1-1", "new_years_day", "2-14", "valentines_day", "5-17", "minecrafts_birthday",
			"10-31", "halloween", "12-24", "christmas_eve", "12-25", "christmas_day");
	private static final String MORNING = "morning";
	private static final String AFTERNOON = "afternoon";
	private static final String EVENING = "evening";
	private static final String NIGHT = "night";
	// The special characters greeting a player.
	private static final String MAYOR_GREETS = "mayor_greets";
	private static final String MAYOR_SEES_HIS_HAT = "player_wears_the_mayor_hat";
	private static final String WOOLY_GREETS = "wooly_greets";
	private static final String TESTIFICATE_GREETS = "testificate_man_greets";
	private static final String TESTIFICATE_SEES_HIS_HELMET = "player_wears_testificate_mans_helmet";
	private static final String NUMBER_5_GREETS = "villager_5_greets";
	private static final String NUMBER_5_SEES_HIS_MOUSTACHE = "player_wears_villager_5s_moustache";
	private static final String NUMBER_9_GREETS = "villager_9_greets";

	private static final int INTERVAL = 60;
	private static final double NEAR = 5;
	private static final double MONSTER_RANGE = 12;
	private static final double FAR = 128;
	private static final int PER_GROUP = 4;
	private static final int CROWD_SIZE = 32;
	private static final int REMEMBER_TICKS = 6000;
	private static final int REMEMBER_DRAGON_TICKS = 600;
	private static final double PLAYER_LOOK_RANGE = 8;
	private static final double PLAYER_LOOK_CONE = 0.97;

	private static final Map<LivingEntity, Map<Entity, Long>> noticed = new WeakHashMap<>();

	private NoticeReactions() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (DialogEngine.get() != null && server.getTickCount() % INTERVAL == 0) {
				tick(server);
			}
		});
	}

	private static void tick(MinecraftServer server) {
		DialogEngine engine = DialogEngine.get();
		long now = server.getTickCount();
		for (ServerLevel level : server.getAllLevels()) {
			java.util.Set<LivingEntity> speakers = new java.util.HashSet<>();
			for (ServerPlayer player : level.players()) {
				speakers.addAll(level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(Reactions.NEARBY),
						e -> Speakers.kindOf(e) != null && Speakers.kindOf(e) != Kind.TRADER));
			}
			for (LivingEntity speaker : speakers) {
				if (speaker.isAlive() && engine.idle(speaker)) {
					notice(level, speaker, now);
				}
			}
		}
	}

	private static void notice(ServerLevel level, LivingEntity speaker, long now) {
		Kind kind = Speakers.kindOf(speaker);
		if (level.getEntitiesOfClass(LivingEntity.class, speaker.getBoundingBox().inflate(NEAR),
				e -> Speakers.kindOf(e) != null && e.distanceTo(speaker) <= NEAR).size() >= CROWD_SIZE) {
			if (DialogEngine.get().speakNow(speaker, CROWD, Options.DEFAULT)) {
				return;
			}
		}
		Set<Kind> noticeable = noticeableKinds(kind);
		List<Entity> nearby = closest(level, speaker, NEAR, e -> !(e instanceof Enemy), noticeable);
		nearby.addAll(closest(level, speaker, FAR, e -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("happy_ghast"), noticeable).stream().limit(1).toList());
		List<Entity> monsters = closest(level, speaker, MONSTER_RANGE, e -> e instanceof Enemy, noticeable);
		List<Entity> dragon = closest(level, speaker, FAR, e -> e.getType() == EntityType.ENDER_DRAGON, noticeable).stream().limit(1).toList();
		Map<Entity, Long> memory = noticed.computeIfAbsent(speaker, e -> new WeakHashMap<>());
		for (List<Entity> group : List.of(nearby, monsters, dragon)) {
			for (Entity target : group) {
				boolean isDragon = target.getType() == EntityType.ENDER_DRAGON;
				Long last = memory.get(target);
				if (last != null && now - last < (isDragon ? REMEMBER_DRAGON_TICKS : REMEMBER_TICKS)) {
					continue;
				}
				if (remarkOn(level, speaker, kind, target, isDragon ? REMEMBER_DRAGON_TICKS : 80)) {
					memory.put(target, now);
					return;
				}
			}
		}
	}

	/** Other speakers a speaker takes notice of (the script excludes most of its own kinds). */
	private static Set<Kind> noticeableKinds(Kind speaker) {
		Set<Kind> kinds = EnumSet.noneOf(Kind.class);
		if (speaker != Kind.WOOLY) {
			kinds.add(Kind.WOOLY);
		}
		if (speaker == Kind.TESTIFICATE_MAN) {
			kinds.add(Kind.VILLAGER);
		}
		if (speaker == Kind.VILLAGER) {
			kinds.addAll(List.of(Kind.VILLAGER, Kind.NUMBER_5, Kind.NUMBER_9));
		}
		return kinds;
	}

	private static List<Entity> closest(ServerLevel level, LivingEntity speaker, double range,
			java.util.function.Predicate<Entity> filter, Set<Kind> noticeable) {
		List<Entity> found = new ArrayList<>(level.getEntities(speaker, speaker.getBoundingBox().inflate(range), e -> {
			Kind kind = Speakers.kindOf(e);
			return e.isAlive() && e.distanceTo(speaker) <= range && (kind == null || noticeable.contains(kind))
					&& !(e instanceof Player player && player.isSpectator()) && filter.test(e);
		}));
		found.sort(Comparator.comparingDouble(e -> e.distanceTo(speaker)));
		return new ArrayList<>(found.subList(0, Math.min(PER_GROUP, found.size())));
	}

	private static boolean remarkOn(ServerLevel level, LivingEntity speaker, Kind kind, Entity target, int timeout) {
		Remark remark = target instanceof Player player ? greeting(speaker, kind, player)
				: kind == Kind.WOOLY ? null : aboutMob(speaker, kind, target);
		if (remark == null || !canSee(level, speaker, target)) {
			return false;
		}
		Options options = Options.DEFAULT.facing(target).waitingAtMost(timeout);
		if (kind == Kind.MAYOR && remark.second() != null) {
			return Reactions.say(speaker, remark.second(), options.withKinds(Kind.MAYOR).withStates(State.BABY));
		}
		if (kind == Kind.WOOLY && remark.adult() != null) {
			return Reactions.say(speaker, remark.adult(), options.withKinds(Kind.WOOLY));
		}
		if (remark.adult() != null && remark.second() != null) {
			return Reactions.sayByAge(speaker, remark.adult(), remark.second(), options);
		}
		boolean second = kind == Kind.MAYOR || Speakers.isBaby(speaker);
		String line = second ? remark.second() : remark.adult();
		return line != null && Reactions.say(speaker, line, options.withStates(second ? State.BABY : State.ADULT));
	}

	/** Only players looking straight at the speaker get greeted. */
	private static Remark greeting(LivingEntity speaker, Kind kind, Player player) {
		Vec3 to = speaker.position().add(0, 1, 0).subtract(player.getEyePosition());
		double distance = to.length();
		if (distance == 0 || distance > PLAYER_LOOK_RANGE || player.getLookAngle().dot(to.scale(1 / distance)) < PLAYER_LOOK_CONE) {
			return null;
		}
		return switch (kind) {
			case MAYOR -> new Remark(null, wears(player, "mayor_hat") ? MAYOR_SEES_HIS_HAT : MAYOR_GREETS);
			case WOOLY -> Remark.adult(WOOLY_GREETS);
			case TESTIFICATE_MAN -> Remark.adult(wears(player, "testificate_man_helmet") ? TESTIFICATE_SEES_HIS_HELMET : TESTIFICATE_GREETS);
			case NUMBER_5 -> Remark.adult(wears(player, "moustache") ? NUMBER_5_SEES_HIS_MOUSTACHE : NUMBER_5_GREETS);
			case NUMBER_9 -> Remark.adult(NUMBER_9_GREETS);
			case VILLAGER -> villagerGreeting((Villager) speaker, player);
			default -> null;
		};
	}

	private static boolean wears(Player player, String item) {
		return BuiltInRegistries.ITEM.getKey(player.getItemBySlot(EquipmentSlot.HEAD).getItem())
				.equals(com.javafied.villagernews.VillagerNewsJavafied.id(item));
	}

	/** One of: the reputation greeting, a calendar greeting, the time-of-day greeting. */
	private static Remark villagerGreeting(Villager villager, Player player) {
		List<Remark> options = new ArrayList<>();
		int reputation = villager.getPlayerReputation(player);
		int band = nearest(reputation, 100, 50, 0, -150, -300);
		options.add(switch (band) {
			case 100 -> new Remark(REPUTATION_EXTREMELY_HIGH, BABY_MEETS_PLAYER);
			case 50 -> new Remark(REPUTATION_HIGH, BABY_MEETS_PLAYER);
			case 0 -> new Remark(APPROACHED, BABY_MEETS_PLAYER);
			case -150 -> new Remark(REPUTATION_LOW, BABY_MEETS_DISLIKED_PLAYER);
			default -> new Remark(player.getMainHandItem().is(ItemTags.SWORDS) ? REPUTATION_EXTREMELY_LOW_ARMED
					: REPUTATION_EXTREMELY_LOW, BABY_MEETS_DISLIKED_PLAYER);
		});
		for (String calendar : calendarGreetings()) {
			options.add(new Remark(calendar, BABY_MEETS_PLAYER));
		}
		long time = Math.floorMod(villager.level().getDayTime(), 24000L);
		options.add(Remark.adult(time < 6000 ? MORNING : time < 12000 ? AFTERNOON : time < 13000 ? EVENING : NIGHT));
		return options.get(ThreadLocalRandom.current().nextInt(options.size()));
	}

	static List<String> calendarGreetings() {
		LocalDate date = LocalDate.now();
		List<String> greetings = new ArrayList<>();
		greetings.add(WEEKDAY_GREETING.get(date.getDayOfWeek()));
		if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
			greetings.add(WEEKEND_GREETING);
		}
		if (LocalTime.now().getMinute() == 0) {
			greetings.add(TOP_OF_THE_HOUR);
		}
		if (date.getDayOfWeek() == DayOfWeek.FRIDAY && date.getDayOfMonth() == 13) {
			greetings.add(FRIDAY_13TH);
		}
		String holiday = HOLIDAY_GREETING.get(date.getMonthValue() + "-" + date.getDayOfMonth());
		if (holiday != null) {
			greetings.add(holiday);
		}
		return greetings;
	}

	private static int nearest(int value, int... bands) {
		int best = bands[0];
		for (int band : bands) {
			if (Math.abs(value - band) < Math.abs(value - best)) {
				best = band;
			}
		}
		return best;
	}

	private static Remark aboutMob(LivingEntity speaker, Kind speakerKind, Entity target) {
		ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
		Kind targetKind = Speakers.kindOf(target);
		boolean baby = Speakers.isBaby(target);
		if (targetKind == Kind.WOOLY || target instanceof Sheep) {
			return Remark.adult(target instanceof Sheep sheep && sheep.isSheared() ? SHEARED_SHEEP : baby ? BABY_SHEEP : SHEEP);
		}
		if (targetKind != null && targetKind != Kind.VILLAGER) {
			String meet = MEET_CHARACTER.get(targetKind);
			return meet == null ? null : Remark.adult(meet);
		}
		if (targetKind == Kind.VILLAGER) {
			String worn = com.javafied.villagernews.behavior.BehaviorProperties.read(target,
					com.javafied.villagernews.names.AddonNames.property("accessory"));
			String accessory = worn == null ? "none" : com.javafied.villagernews.names.AddonNames.nameOf(
					com.javafied.villagernews.names.AddonNames.Kind.ITEM, worn);
			if (speakerKind == Kind.TESTIFICATE_MAN && accessory.equals("testificate_man_helmet")) {
				return Remark.adult(VILLAGER_IN_TESTIFICATE_HELMET);
			}
			if (!accessory.equals("none")) {
				return Remark.adult(VILLAGER_IN_COSMETIC);
			}
			return baby ? Remark.adult(BABY_VILLAGER) : null;
		}
		if (!typeId.getNamespace().equals("minecraft")) {
			return target instanceof LivingEntity ? Remark.adult(UNKNOWN_MOB) : null;
		}
		String type = typeId.getPath();
		switch (type) {
			case "bee":
				return Remark.adult(target instanceof NeutralMob bee && bee.isAngry() ? ANGRY_BEE : baby ? BABY_BEE : BEE);
			case "wolf":
				boolean tamed = target instanceof TamableAnimal wolf && wolf.isTame();
				return Remark.adult(tamed ? (baby ? TAMED_BABY_WOLF : TAMED_WOLF) : baby ? BABY_WOLF : WOLF);
			case "zombie":
				return Remark.adult(target.getVehicle() != null && target.getVehicle().getType() == EntityType.CHICKEN ? JOCKEY
						: baby ? BABY_ZOMBIE : ZOMBIE);
			case "chicken":
				return Remark.adult(target.getPassengers().stream().anyMatch(p -> p.getType() == EntityType.ZOMBIE) ? JOCKEY
						: baby ? BABY_CHICKEN : CHICKEN);
			default:
				break;
		}
		Remark remark = baby ? BABY_MOBS.get(type) : MOBS.get(type);
		if (remark != null) {
			return remark;
		}
		if (target instanceof ItemEntity) {
			return target.level().getEntitiesOfClass(ItemEntity.class, target.getBoundingBox().inflate(5),
					item -> item.distanceTo(target) <= 5).size() >= 5 ? Remark.adult(DROPPED_ITEMS) : null;
		}
		return baby ? Remark.adult(BABY_VILLAGER) : null;
	}

	/** Nothing solid between the speaker's head and the target's. */
	private static boolean canSee(ServerLevel level, LivingEntity speaker, Entity target) {
		Vec3 from = speaker.getEyePosition();
		Vec3 to = target.getEyePosition();
		Vec3 direction = to.subtract(from);
		double distance = direction.length();
		if (distance < 0.5) {
			return true;
		}
		Vec3 end = from.add(direction.scale((distance - 0.5) / distance));
		return level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, speaker)).getType()
				== HitResult.Type.MISS;
	}
}
