package com.javafied.villagernews.dialog;

import com.javafied.villagernews.dialog.DialogEngine.Options;
import com.javafied.villagernews.dialog.DialogEngine.State;

import com.javafied.villagernews.platform.ServerEntityEvents;
import com.javafied.villagernews.platform.ServerTickEvents;
import com.javafied.villagernews.platform.UseBlockCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hand port of the add-on script's reactions to a villager's surroundings and
 * world events: water, magma, ice, snow, fire, campfires (and the campfire
 * conversation), TNT, a player on a villager's bed, panicking, sharing and
 * picking up food and items, heading home after dark, getting a job, falling
 * blocks, buttons, doors, levers, fireworks and lightning.
 */
public final class WorldReactions {
	private static final String IN_SHALLOW_WATER = "stand_in_shallow_water";
	private static final String ON_MAGMA = "stand_on_magma";
	private static final String ON_ICE = "stand_on_ice";
	private static final String ON_SNOW = "stand_on_snow";
	private static final String NEAR_FIRE = "stand_near_fire";
	private static final String SEES_CAMPFIRE = "see_a_campfire";
	/** First part of the campfire conversation; its 2nd and 4th parts are said by the one who spoke just before. */
	private static final String CAMPFIRE_CONVERSATION = "two_villagers_at_a_campfire";
	private static final int CAMPFIRE_CONVERSATION_REST = 14000;
	private static final String SEES_TNT = "see_tnt";
	private static final String PLAYER_ON_BED = "stand_on_a_villagers_bed";
	private static final String PANICS = "villager_panics";
	private static final int PANIC_REST_TICKS = 200;
	private static final String SHARES_FOOD = "share_food_with_another_villager";
	private static final Map<String, String> RECEIVED_FOOD = Map.of("beetroot", "receive_beetroot", "bread", "receive_bread",
			"carrot", "receive_carrot",
			"potato", "receive_potatoes");
	private static final String RECEIVED_FOOD_ANY = "receive_food";
	private static final String PICKED_UP_VILLAGERS_ITEM = "pick_up_an_item_dropped_by_a_villager";
	private static final Map<String, String> BABY_RECEIVED_FOOD = Map.of("beetroot", "give_a_baby_beetroot", "bread",
			"give_a_baby_bread", "carrot", "give_a_baby_carrots",
			"potato", "give_a_baby_potatoes");
	private static final String BABY_RECEIVED_ANY = "receive_food_from_another_villager";
	private static final String PICKED_UP_ITEM = "pick_up_an_item";
	private static final String PICKED_UP_PLAYERS_ITEM = "pick_up_an_item_dropped_by_a_player";
	private static final Set<String> SHARED_FOOD = Set.of("bread", "carrot", "potato", "beetroot", "wheat_seeds",
			"beetroot_seeds", "torchflower_seeds", "pitcher_pod", "bone_meal");
	private static final String HOME_WITHOUT_BED = "return_home_without_a_bed";
	private static final String HOME = "return_home";
	private static final String HOME_OTHER_DIMENSION = "return_home_in_another_dimension";
	private static final String HOME_IN_END = "try_to_return_home_in_the_end";
	private static final String HOME_IN_NETHER = "try_to_return_home_in_the_nether";
	private static final int NO_BED_TICKS = 12000;
	private static final String GOT_A_JOB = "get_a_job";
	private static final String FALLING_BLOCK = "see_a_falling_block";
	private static final String BUTTON = "press_a_button";
	private static final String DOOR_IN_FACE = "close_a_door_in_a_villagers_face";
	private static final String LEVER = "flip_a_lever";
	private static final String SET_OFF_FIREWORK = "set_off_a_firework";
	private static final String SAW_FIREWORK = "see_a_firework";
	private static final String SAW_LIGHTNING = "see_lightning";
	private static final String CAUGHT_IN_RAIN = "caught_in_the_rain";
	private static final String TRADER_CAUGHT_IN_RAIN = "wandering_trader_caught_in_the_rain";
	private static final String SHEEP = "sheep";
	private static final String PICKS_UP_ARMOR = "pick_up_armor";
	private static final String PICKS_UP_ENCHANTED_ARMOR = "pick_up_enchanted_armor";
	private static final int ARMOR_NOTICE_TICKS = 30 * 20;
	private static final double ARMOR_REACH = 1.5;
	private static final String WOOLY_JOINS_IN = "interact_with_wooly";
	private static final String TRADER_DRINKS_POTION = "uses_an_invisibility_potion";
	private static final String TRADER_DRINKS_POTION_ONE_LLAMA = "uses_a_potion_with_one_llama";
	private static final String TRADER_DRINKS_POTION_TWO_LLAMAS = "uses_a_potion_with_two_llamas";

	/** Search boxes around a villager for fire, campfires and TNT, rotating through three sizes (radius, height). */
	private static final int[][] SEARCH = {{3, 4}, {5, 3}, {7, 2}};

	private static final Map<Entity, Entity> thrownBy = new WeakHashMap<>();
	private static final Map<LivingEntity, Long> lastPanic = new WeakHashMap<>();
	private static final Set<LivingEntity> panicking = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<Villager, Long> lastWentToBed = new WeakHashMap<>();
	private static final Map<Villager, Long> lastCampfireTalk = new WeakHashMap<>();
	/** Armour lying about, with the tick it appeared. */
	private static final Map<ItemEntity, Long> droppedArmor = new WeakHashMap<>();
	/** Set while a villager is throwing an item, so the item can be traced back to it. */
	private static LivingEntity throwing;
	private static int round;

	private WorldReactions() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(WorldReactions::tick);
		// A villager's third line about sheep invites Wooly (if close by) to answer.
		DialogEngine.onFinished((speech, completed) -> {
			if (completed && speech.dialog().id().equals(SHEEP) && speech.line() == 2 && speech.speaker().level() instanceof ServerLevel level) {
				LivingEntity speaker = speech.speaker();
				level.getEntitiesOfClass(LivingEntity.class, speaker.getBoundingBox().inflate(4),
								e -> Speakers.kindOf(e) == Speakers.Kind.WOOLY && e.distanceTo(speaker) <= 4).stream().findFirst()
						.ifPresent(wooly -> Reactions.say(wooly, WOOLY_JOINS_IN, Options.DEFAULT.withKinds(Speakers.Kind.WOOLY)
								.facing(speaker).ignoringCooldowns(true, true, true).asUrgent()));
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item && isArmor(item.getItem())) {
				droppedArmor.put(item, level.getServer().getTickCount() + 0L);
			}
			if (entity instanceof ItemEntity item && throwing != null) {
				thrownBy.put(item, throwing);
			} else if (entity instanceof FireworkRocketEntity rocket) {
				Reactions.nearest(level, rocket.position(), SET_OFF_FIREWORK, Options.DEFAULT.facing(rocket));
			} else if (entity instanceof LightningBolt bolt) {
				Reactions.nearest(level, bolt.position(), SAW_LIGHTNING, Options.DEFAULT.facing(bolt), 128, null);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity.getRemovalReason() != Entity.RemovalReason.DISCARDED) {
				return;
			}
			if (entity instanceof FireworkRocketEntity rocket) {
				Vec3 at = rocket.position();
				Reactions.nearest(level, at, SAW_FIREWORK, Options.DEFAULT.withStates(State.BABY).facing(at), 64, null);
			} else if (entity instanceof FallingBlockEntity block) {
				Vec3 at = block.position();
				Reactions.nearest(level, at, FALLING_BLOCK, Options.DEFAULT.facing(at), 4, null);
			}
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && level instanceof ServerLevel server) {
				BlockState state = level.getBlockState(hit.getBlockPos());
				Vec3 at = Vec3.atCenterOf(hit.getBlockPos());
				if (state.is(BlockTags.DOORS)) {
					Reactions.nearest(server, at, DOOR_IN_FACE, Options.DEFAULT.facing(at), 2, null);
				} else if (state.is(Blocks.LEVER)) {
					Reactions.nearest(server, at, LEVER, Options.DEFAULT.facing(at));
				}
			}
			return InteractionResult.PASS;
		});
	}

	/** Called (through a mixin) around a villager throwing an item; null when it's done. */
	public static void throwing(LivingEntity thrower, net.minecraft.world.item.ItemStack stack) {
		throwing = thrower;
		if (thrower instanceof Villager villager && stack != null && SHARED_FOOD.contains(path(stack.getItem()))) {
			Reactions.say(villager, SHARES_FOOD, Options.DEFAULT);
		}
	}

	/** Called (through a mixin) when a villager picks up an item. */
	public static void pickedUp(Villager villager, ItemEntity item) {
		Entity thrower = thrownBy.getOrDefault(item, item.getOwner());
		if (thrower == null || thrower == villager || !thrower.isAlive()) {
			return;
		}
		String type = path(item.getItem().getItem());
		Options options = Options.DEFAULT.facing(thrower);
		if (RECEIVED_FOOD.containsKey(type) && thrower instanceof Villager) {
			List<String> adult = List.of(RECEIVED_FOOD_ANY, PICKED_UP_VILLAGERS_ITEM, RECEIVED_FOOD.get(type));
			List<String> baby = List.of(BABY_RECEIVED_ANY, BABY_RECEIVED_FOOD.get(type));
			Reactions.sayByAge(villager, pick(adult), pick(baby), options);
		} else if (SHARED_FOOD.contains(type)) {
			String baby = BABY_RECEIVED_FOOD.containsKey(type) ? pick(List.of(BABY_RECEIVED_ANY, BABY_RECEIVED_FOOD.get(type))) : BABY_RECEIVED_ANY;
			Reactions.sayByAge(villager, thrower instanceof Player ? PICKED_UP_PLAYERS_ITEM : PICKED_UP_ITEM, baby, options);
		}
	}

	/** Called (through a mixin) when a villager's profession changes. */
	public static void professionChanged(Villager villager, String before, String after) {
		if (before.equals("none") && !after.equals("none") && !after.equals("nitwit")) {
			Reactions.say(villager, GOT_A_JOB, Options.DEFAULT.ignoringCooldowns(true, true, true).asUrgent());
		}
	}

	/** Called (through a mixin) when the wandering trader gets an effect (drinking his invisibility potion). */
	public static void traderGotEffect(LivingEntity trader) {
		long llamas = trader.level().getEntitiesOfClass(LivingEntity.class, trader.getBoundingBox().inflate(16),
				e -> e instanceof net.minecraft.world.entity.Leashable leashed && leashed.getLeashHolder() == trader
						&& BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("trader_llama")).size();
		List<String> options = new java.util.ArrayList<>(List.of(TRADER_DRINKS_POTION));
		if (llamas == 1) {
			options.add(TRADER_DRINKS_POTION_ONE_LLAMA);
		} else if (llamas == 2) {
			options.add(TRADER_DRINKS_POTION_TWO_LLAMAS);
		}
		DialogEngine engine = DialogEngine.get();
		if (engine != null) {
			engine.speakNow(trader, pick(options), Options.DEFAULT);
		}
	}

	/** Called (through a mixin) when a button is pressed, by anyone or anything. */
	public static void buttonPressed(ServerLevel level, BlockPos pos) {
		Vec3 at = Vec3.atCenterOf(pos);
		Reactions.nearest(level, at, BUTTON, Options.DEFAULT.facing(at));
	}

	/** Called when a villager goes to bed, for "heading home without a bed". */
	static void wentToBed(Villager villager) {
		lastWentToBed.put(villager, villager.level().getServer().getTickCount() + 0L);
	}

	/** From the idle trigger: out walking fast at night - heading home. */
	static boolean headingHome(Villager villager) {
		long time = Math.floorMod(villager.level().getDayTime(), 24000L);
		boolean night = "nitwit".equals(Speakers.profession(villager)) ? time >= 14000 || time < 2000 : time >= 12000;
		if (!night || villager.isSleeping() || villager.getDeltaMovement().horizontalDistance() <= 0.15) {
			return false;
		}
		Long bed = lastWentToBed.get(villager);
		String dialog;
		if (bed != null && villager.level().getServer().getTickCount() - bed >= NO_BED_TICKS) {
			dialog = HOME_WITHOUT_BED;
		} else if (villager.level().dimension() == Level.OVERWORLD) {
			dialog = HOME;
		} else {
			// The add-on's own dimension checks are inverted; this is what they mean.
			dialog = pick(List.of(HOME_OTHER_DIMENSION, villager.level().dimension() == Level.END ? HOME_IN_END : HOME_IN_NETHER));
		}
		return Reactions.say(villager, dialog, Options.DEFAULT);
	}

	private static void tick(MinecraftServer server) {
		if (DialogEngine.get() == null) {
			return;
		}
		if (server.getTickCount() % 20 == 0) {
			int size = round++ % 4;
			for (ServerLevel level : server.getAllLevels()) {
				Set<Villager> seen = new HashSet<>();
				for (ServerPlayer player : level.players()) {
					for (Villager villager : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(Reactions.NEARBY))) {
						if (Speakers.kindOf(villager) == Speakers.Kind.VILLAGER && seen.add(villager)) {
							surroundings(level, villager, size);
						}
					}
					for (LivingEntity speaker : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(Reactions.NEARBY),
							e -> Speakers.kindOf(e) == Speakers.Kind.VILLAGER || Speakers.kindOf(e) == Speakers.Kind.TRADER)) {
						if (level.isRainingAt(speaker.blockPosition().above()) && DialogEngine.get().idle(speaker)) {
							Reactions.say(speaker, Speakers.kindOf(speaker) == Speakers.Kind.TRADER ? TRADER_CAUGHT_IN_RAIN : CAUGHT_IN_RAIN,
									Options.DEFAULT);
						}
					}
					if (level.getBlockState(player.getOnPos()).is(BlockTags.BEDS)) {
						Reactions.nearest(level, player.position(), PLAYER_ON_BED, Options.DEFAULT.facing(player), 2.5, null);
					}
				}
			}
			panics(server);
		}
		if (server.getTickCount() % 10 == 5 && !droppedArmor.isEmpty()) {
			armorNearVillagers(server.getTickCount());
		}
	}

	/**
	 * "Pick Up Armor": Java villagers can't pick armour up, so one standing
	 * next to a piece lying on the ground says it instead (the add-on ships
	 * the lines without a trigger).
	 */
	private static void armorNearVillagers(long now) {
		for (var it = droppedArmor.entrySet().iterator(); it.hasNext(); ) {
			var entry = it.next();
			ItemEntity item = entry.getKey();
			if (!item.isAlive() || now - entry.getValue() > ARMOR_NOTICE_TICKS) {
				it.remove();
				continue;
			}
			if (!item.onGround()) {
				continue;
			}
			String dialog = item.getItem().isEnchanted() ? PICKS_UP_ENCHANTED_ARMOR : PICKS_UP_ARMOR;
			boolean said = item.level().getEntitiesOfClass(Villager.class, item.getBoundingBox().inflate(ARMOR_REACH),
							v -> Speakers.kindOf(v) == Speakers.Kind.VILLAGER && !Speakers.isBaby(v) && !v.isSleeping()).stream()
					.sorted(java.util.Comparator.comparingDouble(v -> v.distanceToSqr(item)))
					.anyMatch(v -> Reactions.say(v, dialog, Options.DEFAULT.facing(item)));
			if (said) {
				it.remove();
			}
		}
	}

	private static boolean isArmor(net.minecraft.world.item.ItemStack stack) {
		return stack.is(ItemTags.HEAD_ARMOR) || stack.is(ItemTags.CHEST_ARMOR) || stack.is(ItemTags.LEG_ARMOR)
				|| stack.is(ItemTags.FOOT_ARMOR);
	}

	private static void surroundings(ServerLevel level, Villager villager, int size) {
		BlockPos feet = villager.blockPosition();
		BlockState at = level.getBlockState(feet);
		BlockState below = level.getBlockState(feet.below());
		boolean shallowWater = at.is(Blocks.WATER) && level.getBlockState(feet.above()).isAir() && !below.isAir()
				&& below.getFluidState().isEmpty() && !villager.isPassenger();
		if (shallowWater) {
			Reactions.say(villager, IN_SHALLOW_WATER, Options.DEFAULT);
		} else if (villager.onGround()) {
			if (below.is(Blocks.MAGMA_BLOCK)) {
				// Said once they've stepped off it.
				Reactions.say(villager, ON_MAGMA, Options.DEFAULT.alsoWhen(State.EVEN_IN_DANGER).waitingAtMost(100)
						.readyWhen(v -> !v.level().getBlockState(v.blockPosition().below()).is(Blocks.MAGMA_BLOCK)));
			} else if (BlockCategories.ice(below)) {
				Reactions.say(villager, ON_ICE, Options.DEFAULT);
			} else if (at.is(Blocks.SNOW) || below.is(Blocks.SNOW) || below.is(Blocks.SNOW_BLOCK)) {
				Reactions.say(villager, ON_SNOW, Options.DEFAULT);
			}
		}
		if (size == 0) {
			return;
		}
		int radius = SEARCH[size - 1][0];
		int height = SEARCH[size - 1][1];
		if (find(level, feet, radius, height, state -> state.is(BlockTags.FIRE)) != null) {
			Reactions.say(villager, NEAR_FIRE, Options.DEFAULT);
		}
		BlockPos campfire = find(level, feet, radius, height, state -> state.is(BlockTags.CAMPFIRES));
		if (campfire != null) {
			campfire(level, villager, campfire);
		}
		BlockPos tnt = find(level, feet, radius, height, state -> state.is(Blocks.TNT));
		if (tnt != null) {
			Reactions.say(villager, SEES_TNT, Options.DEFAULT.facing(Vec3.atCenterOf(tnt)));
		}
	}

	/** Other villagers at the campfire: a conversation (at most every 14000 ticks); otherwise a remark about the fire. */
	private static void campfire(ServerLevel level, Villager villager, BlockPos campfire) {
		Vec3 fire = Vec3.atCenterOf(campfire);
		Long last = lastCampfireTalk.get(villager);
		long now = level.getServer().getTickCount();
		DialogEngine engine = DialogEngine.get();
		Villager partner = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(fire, fire).inflate(6),
						v -> v != villager && v.position().distanceTo(fire) <= 6 && !engine.isTalking(v)).stream()
				.findFirst().orElse(null);
		if (partner != null && (last == null || now - last >= CAMPFIRE_CONVERSATION_REST)) {
			VillagerReactions.startConversation(engine, villager, partner, CAMPFIRE_CONVERSATION, Set.of(1, 3));
			lastCampfireTalk.put(villager, now);
			lastCampfireTalk.put(partner, now);
		} else {
			Reactions.say(villager, SEES_CAMPFIRE, Options.DEFAULT.facing(fire));
		}
	}

	private static BlockPos find(ServerLevel level, BlockPos center, int radius, int height,
			java.util.function.Predicate<BlockState> test) {
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -height, -radius), center.offset(radius, height, radius))) {
			if (test.test(level.getBlockState(pos))) {
				return pos.immutable();
			}
		}
		return null;
	}

	/** A villager that starts panicking says so a moment later (at most every 10 seconds). */
	private static void panics(MinecraftServer server) {
		long now = server.getTickCount();
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer player : level.players()) {
				for (Villager villager : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(Reactions.NEARBY))) {
					boolean panic = villager.getBrain().isActive(Activity.PANIC);
					if (panic && panicking.add(villager)) {
						Long last = lastPanic.get(villager);
						if (last == null || now - last >= PANIC_REST_TICKS) {
							lastPanic.put(villager, now);
							com.javafied.villagernews.platform.ServerTasks.schedule(server,new net.minecraft.server.TickTask(server.getTickCount() + 25,
									() -> Reactions.say(villager, PANICS, Options.DEFAULT)));
						}
					} else if (!panic) {
						panicking.remove(villager);
					}
				}
			}
		}
	}

	private static String path(net.minecraft.world.item.Item item) {
		return BuiltInRegistries.ITEM.getKey(item).getPath();
	}

	private static <T> T pick(List<T> options) {
		return options.get(ThreadLocalRandom.current().nextInt(options.size()));
	}
}
