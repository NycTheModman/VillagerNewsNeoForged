package com.javafied.villagernews.dialog;

import com.javafied.villagernews.dialog.DialogEngine.Options;
import com.javafied.villagernews.dialog.DialogEngine.State;

import com.javafied.villagernews.platform.ServerTickEvents;
import com.javafied.villagernews.platform.UseBlockCallback;
import com.javafied.villagernews.platform.UseEntityCallback;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hand port of the add-on script's reactions around a villager's day: going
 * to bed, snoring, waking up, riding boats and minecarts, hearing the bell,
 * being named with a name tag, and the evening gathering.
 */
public final class VillagerRoutineReactions {
	private static final String GOES_TO_BED = "go_to_bed";
	private static final String SNORES = "sleeping";
	private static final String WOKEN_AT_NIGHT = "wake_a_sleeping_villager";
	private static final String WOKE_UP = "wake_up_naturally";
	private static final int SNORE_AFTER_TICKS = 40;
	private static final int SNORE_MIN_TICKS = 200;
	private static final int SNORE_MAX_TICKS = 240;
	private static final String GOT_IN_BOAT = "sit_in_a_boat";
	private static final String TWO_IN_ONE_BOAT = "two_villagers_in_one_boat";
	private static final String BOAT_ON_WATER = "boat_on_water";
	private static final String BOAT_ON_LAND = "boat_on_land";
	private static final String MINECART_MOVING = "ride_in_a_moving_minecart";
	private static final String MINECART_STILL = "sit_in_a_minecart";
	private static final String HEARD_BELL = "hear_a_bell_ring";
	private static final String HEARD_BELL_BABY = "ring_a_bell_near_a_baby";
	private static final int BELL_DELAY_TICKS = 15;
	private static final double BELL_RANGE = 50;
	private static final String NAMED = "name_a_villager";
	private static final String NAMED_BABY = "name_a_baby_villager";
	/** Special names: the add-on's easter eggs. (Its key for jeb_ was mangled by its minifier; restored.) */
	private static final Map<String, String[]> NAMED_SPECIALLY = Map.of(
			"Dinnerbone", new String[] {"named_dinnerbone", NAMED_BABY}, "Grumm", new String[] {"named_dinnerbone", NAMED_BABY},
			"jeb_", new String[] {"named_jeb", NAMED_BABY}, "dragon", new String[] {NAMED, "baby_named_dragon"},
			"Dragon", new String[] {NAMED, "baby_named_dragon"});
	private static final String GATHERING = "villager_gathering";
	private static final String CANNOT_FIND_BELL = "cannot_find_the_bell";
	private static final String GOSSIP = "villagers_gossip";
	/** The gossip conversation's first part (its chain is in the library's conversations). */
	private static final String GOSSIP_CHAIN = "gossip_chain";

	private static final Map<LivingEntity, Long> sleepingSince = new WeakHashMap<>();
	private static final Map<LivingEntity, Long> nextSnore = new WeakHashMap<>();
	private static final Map<Villager, Integer> riding = new WeakHashMap<>();

	private VillagerRoutineReactions() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(VillagerRoutineReactions::tick);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && level instanceof ServerLevel server && level.getBlockState(hit.getBlockPos()).is(Blocks.BELL)) {
				Vec3 bell = Vec3.atCenterOf(hit.getBlockPos());
				later(server, BELL_DELAY_TICKS, () -> heardBell(server, bell));
			}
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			var held = player.getItemInHand(hand);
			if (level instanceof ServerLevel server && entity instanceof Villager villager && held.is(Items.NAME_TAG)
					&& held.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME) != null && Speakers.kindOf(villager) == Speakers.Kind.VILLAGER) {
				String name = held.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString();
				later(server, 1, () -> named(villager, player, name));
			}
			return InteractionResult.PASS;
		});
	}

	private static void later(ServerLevel level, int ticks, Runnable task) {
		MinecraftServer server = level.getServer();
		com.javafied.villagernews.platform.ServerTasks.schedule(server,new TickTask(server.getTickCount() + ticks, task));
	}

	private static void tick(MinecraftServer server) {
		if (DialogEngine.get() == null) {
			return;
		}
		long now = server.getTickCount();
		boolean ridingCheck = now % 80 == 0;
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer player : level.players()) {
				for (LivingEntity speaker : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(Reactions.NEARBY),
						e -> Speakers.kindOf(e) != null && Speakers.kindOf(e) != Speakers.Kind.WOOLY)) {
					sleep(speaker, now);
					if (ridingCheck && speaker instanceof Villager villager) {
						ride(villager);
					}
				}
			}
		}
	}

	/** Falling asleep: "time for bed"; asleep: a snore every 10-12 seconds; waking up: depends on the hour. */
	private static void sleep(LivingEntity speaker, long now) {
		Long since = sleepingSince.get(speaker);
		if (speaker.isSleeping()) {
			if (since == null) {
				sleepingSince.put(speaker, now);
				nextSnore.put(speaker, now + SNORE_AFTER_TICKS + snoreGap());
				if (Speakers.kindOf(speaker) == Speakers.Kind.VILLAGER) {
					WorldReactions.wentToBed((Villager) speaker);
					Reactions.say(speaker, GOES_TO_BED, Options.DEFAULT.alsoWhen(State.SLEEPING));
				}
			} else if (now >= nextSnore.getOrDefault(speaker, Long.MAX_VALUE)) {
				nextSnore.put(speaker, now + snoreGap());
				DialogEngine engine = DialogEngine.get();
				engine.speakNow(speaker, SNORES, Options.DEFAULT.alsoWhen(State.SLEEPING));
			}
		} else if (since != null) {
			sleepingSince.remove(speaker);
			nextSnore.remove(speaker);
			if (Speakers.kindOf(speaker) == Speakers.Kind.VILLAGER) {
				wokeUp((Villager) speaker);
			}
		}
	}

	private static long snoreGap() {
		return ThreadLocalRandom.current().nextInt(SNORE_MIN_TICKS, SNORE_MAX_TICKS + 1);
	}

	private static void wokeUp(Villager villager) {
		DialogEngine.get().stop(villager);
		later((ServerLevel) villager.level(), 4, () -> {
			if (!villager.isAlive()) {
				return;
			}
			long time = Math.floorMod(villager.level().getDayTime(), 24000L);
			boolean night = "nitwit".equals(Speakers.profession(villager)) ? time >= 14000 || time < 2000 : time >= 12000;
			if (night) {
				Reactions.say(villager, WOKEN_AT_NIGHT, Options.DEFAULT.forced());
			} else {
				Reactions.say(villager, WOKE_UP, Options.DEFAULT);
			}
		});
	}

	/** Every 4 seconds: getting into a boat (with a friend?), boating on water or land, minecarts moving or not. */
	private static void ride(Villager villager) {
		Entity vehicle = villager.getVehicle();
		int kind = vehicle instanceof Boat ? 1 : vehicle instanceof AbstractMinecart ? 2 : 0;
		Integer before = riding.put(villager, kind);
		if (kind == 1) {
			if (before == null || before != 1) {
				List<Entity> passengers = vehicle.getPassengers();
				boolean withFriend = passengers.size() == 2 && passengers.stream().allMatch(p -> p instanceof Villager);
				Entity friend = withFriend ? passengers.get(passengers.get(0) == villager ? 1 : 0) : null;
				if (!(withFriend && Reactions.say(villager, TWO_IN_ONE_BOAT, Options.DEFAULT.facing(friend)))) {
					Reactions.say(villager, GOT_IN_BOAT, Options.DEFAULT);
				}
			} else {
				Reactions.say(villager, vehicle.isInWater() ? BOAT_ON_WATER : BOAT_ON_LAND, Options.DEFAULT);
			}
		} else if (kind == 2) {
			Reactions.say(villager, vehicle.getDeltaMovement().horizontalDistance() > 0.03 ? MINECART_MOVING : MINECART_STILL,
					Options.DEFAULT);
		}
	}

	/** Every villager within 50 blocks reacts to the bell, a few ticks apart. */
	private static void heardBell(ServerLevel level, Vec3 bell) {
		DialogEngine engine = DialogEngine.get();
		if (engine == null) {
			return;
		}
		for (Villager villager : level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(bell, bell).inflate(BELL_RANGE),
				v -> !v.isSleeping() && v.position().distanceTo(bell) <= BELL_RANGE && Speakers.kindOf(v) == Speakers.Kind.VILLAGER)) {
			DialogEngine.Speech speech = engine.speech(villager);
			if (speech != null && (speech.dialog().id().equals(HEARD_BELL) || speech.dialog().id().equals(HEARD_BELL_BABY))) {
				continue;
			}
			later(level, ThreadLocalRandom.current().nextInt(5), () -> Reactions.sayByAge(villager, HEARD_BELL, HEARD_BELL_BABY,
					Options.DEFAULT.facing(bell).forced().asUrgent().waitingAtMost(14)));
		}
	}

	private static void named(Villager villager, net.minecraft.world.entity.player.Player player, String name) {
		if (!villager.isAlive() || villager.getCustomName() == null || !villager.getCustomName().getString().equals(name)) {
			return;
		}
		String[] lines = NAMED_SPECIALLY.getOrDefault(name, new String[] {NAMED, NAMED_BABY});
		Reactions.sayByAge(villager, lines[0], lines[1], Options.DEFAULT.facing(player));
	}

	/**
	 * From the idle trigger, in the evening (ticks 8000-10000): a villager
	 * standing about gossips with a neighbour, or mentions the gathering.
	 * @return whether this was the evening gathering (the other idle chatter is skipped then)
	 */
	static boolean eveningGathering(DialogEngine engine, Villager villager) {
		long time = Math.floorMod(villager.level().getDayTime(), 24000L);
		if (!gathers(villager, time)) {
			return false;
		}
		Villager partner = villager.level().getEntitiesOfClass(Villager.class, villager.getBoundingBox().inflate(2.5),
						v -> v != villager && v.distanceTo(villager) <= 2.5 && gathers(v, time) && !engine.isTalking(v)).stream()
				.findFirst().orElse(null);
		if (partner != null) {
			String starter = ThreadLocalRandom.current().nextBoolean() ? GOSSIP : GOSSIP_CHAIN;
			VillagerReactions.startConversation(engine, villager, partner, starter);
		} else {
			Reactions.say(villager, knowsBell(villager) ? GATHERING : CANNOT_FIND_BELL, Options.DEFAULT);
		}
		return true;
	}

	/**
	 * Whether the villager has a village bell to gather at: in Java, a meeting
	 * point it remembers in this dimension. (The script's own check was left
	 * as a stub that always finds one, so "Cannot Find the Bell" never played.)
	 */
	private static boolean knowsBell(Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT)
				.filter(point -> point.dimension() == villager.level().dimension()).isPresent();
	}

	private static boolean gathers(Villager villager, long time) {
		return !Speakers.isBaby(villager) && !"nitwit".equals(Speakers.profession(villager)) && time >= 8000 && time < 10000
				&& villager.getDeltaMovement().horizontalDistance() <= 0.02;
	}
}
