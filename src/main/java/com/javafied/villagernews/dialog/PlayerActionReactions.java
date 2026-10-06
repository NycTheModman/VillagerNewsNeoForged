package com.javafied.villagernews.dialog;

import com.javafied.villagernews.dialog.DialogEngine.Options;

import com.javafied.villagernews.platform.ServerLivingEntityEvents;
import com.javafied.villagernews.platform.ServerEntityEvents;
import com.javafied.villagernews.platform.ServerTickEvents;
import com.javafied.villagernews.platform.PlayerBlockBreakEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Hand port of the add-on script's reactions to what players do near
 * villagers: placing and breaking blocks, changing game mode, dying, flying,
 * carrying a nearly broken tool, lighting TNT, their status effects, standing
 * still, and staring at a villager. Each is said by the nearest villager
 * that can ({@link Reactions#nearest}), facing the player.
 */
public final class PlayerActionReactions {
	// Placing blocks: an exact block first, then categories in the add-on's order, then the generic line.
	private static final Map<String, String> PLACED_BLOCK = Map.ofEntries(Map.entry("melon", "place_a_melon"),
			Map.entry("beacon", "place_a_beacon"), Map.entry("bookshelf", "place_a_bookshelf"),
			Map.entry("chiseled_bookshelf", "place_a_bookshelf"),
			Map.entry("jack_o_lantern", "place_a_jack_o_lantern"), Map.entry("end_stone", "place_end_stone"),
			Map.entry("bricks", "place_bricks"),
			Map.entry("jukebox", "place_a_jukebox"), Map.entry("lever", "place_a_lever"), Map.entry("observer", "place_an_observer"),
			Map.entry("chest", "place_a_chest"), Map.entry("trapped_chest", "place_a_trapped_chest"),
			Map.entry("tripwire_hook", "place_a_tripwire_hook"),
			Map.entry("detector_rail", "place_a_detector_rail"), Map.entry("daylight_detector", "place_a_daylight_detector"),
			Map.entry("sculk_sensor", "place_a_sculk_sensor"),
			Map.entry("calibrated_sculk_sensor", "place_a_sculk_sensor"), Map.entry("redstone_wire", "place_redstone_dust"),
			Map.entry("repeater", "place_a_redstone_repeater"),
			Map.entry("redstone_torch", "place_a_redstone_torch"), Map.entry("redstone_wall_torch",
			"place_a_redstone_torch"), Map.entry("redstone_lamp", "place_a_redstone_lamp"),
			Map.entry("copper_block", "place_a_copper_block"), Map.entry("lapis_block", "place_a_lapis_block"),
			Map.entry("emerald_block", "place_an_emerald_block"),
			Map.entry("diamond_block", "place_a_diamond_block"), Map.entry("pumpkin", "place_a_pumpkin"),
			Map.entry("carved_pumpkin", "place_a_pumpkin"),
			Map.entry("snow_block", "place_snow"), Map.entry("snow", "place_snow"), Map.entry("crafting_table",
			"place_a_crafting_table"),
			Map.entry("furnace", "place_a_furnace"), Map.entry("powder_snow", "place_powder_snow"));
	private static final String PLACED_LIGHTNING_ROD = "place_a_lightning_rod";
	private static final String PLACED_IRON_GOLEM_FRAME = "build_an_iron_golem_frame";

	private record Category(Predicate<BlockState> test, String dialog) {
	}

	private static final List<Category> PLACED_CATEGORIES = List.of(
			new Category(BlockCategories::light, "place_a_light_block"),
			new Category(state -> state.is(BlockTags.BUTTONS), "place_a_button"),
			// The add-on checks a suffix its minifier mangled, so in Bedrock this one never fires; restored.
			new Category(state -> state.is(BlockTags.PRESSURE_PLATES), "place_a_pressure_plate"),
			new Category(BlockCategories::redstone, "place_a_redstone_component"),
			new Category(state -> state.is(BlockTags.WOOL), "place_wool"),
			new Category(BlockCategories::ocean, "place_a_block_from_the_ocean"),
			new Category(BlockCategories::ice, "place_ice"),
			new Category(BlockCategories::purpur, "place_purpur"),
			new Category(BlockCategories::end, "place_a_block_from_the_end"),
			new Category(BlockCategories::nether, "place_a_block_from_the_nether"),
			new Category(BlockCategories::glass, "place_glass"),
			new Category(BlockCategories::concrete, "place_concrete"),
			new Category(BlockCategories::concretePowder, "place_concrete_powder"),
			new Category(state -> state.is(BlockTags.DIRT), "place_dirt"),
			new Category(BlockCategories::terracotta, "place_terracotta"),
			new Category(state -> false, PLACED_IRON_GOLEM_FRAME), // needs the position: see placed()
			new Category(state -> state.is(Blocks.IRON_BLOCK), "place_an_iron_block"),
			new Category(BlockCategories::valuable, "place_a_valuable_block"),
			new Category(BlockCategories::workstation, "place_a_workstation"),
			new Category(state -> state.is(BlockTags.BEDS), "place_a_bed"),
			new Category(BlockCategories::wood, "place_wood"),
			new Category(BlockCategories::gravity, "place_a_gravity_affected_block"),
			new Category(BlockCategories::creativeOnly, "place_a_creative_only_block"),
			new Category(BlockCategories::plant, "place_leaves_or_plants"),
			new Category(BlockCategories::gold, "place_a_gold_block"),
			new Category(BlockCategories::glazedTerracotta, "place_glazed_terracotta"),
			new Category(state -> true, "place_a_block"));

	// Breaking blocks.
	private static final String BROKE_MANY = "break_multiple_blocks";
	private static final String BROKE_BELL = "break_a_bell";
	private static final String BROKE_BED = "break_a_bed";
	private static final String BROKE_DOOR = "break_a_door";
	private static final String BROKE_WORKSTATION = "break_a_workstation";
	private static final String BROKE_WOOD = "break_wood";
	private static final String HARVESTED_CROPS = "harvest_crops";
	private static final String HARVESTED_NEAR_FARMER = "harvest_crops_near_a_farmer";
	private static final String BROKE_STONE = "break_stone";
	private static final String BROKE_DECORATION = "break_a_decorative_block";
	private static final String BROKE_BLOCK = "break_a_block";
	private static final int QUICK_BREAK_TICKS = 40;

	private static final String CHANGED_GAME_MODE = "change_gamemode";
	private static final String SWITCHED_TO_CREATIVE = "switch_to_creative";
	private static final String PLAYER_DIED = "player_dies";
	private static final String PLAYER_DIED_AGAIN = "player_dies_again";
	private static final String PLAYER_DIED_HARDCORE = "player_dies_in_hardcore";
	private static final int DIED_AGAIN_TICKS = 6000;
	private static final String GLIDING = "glide_with_elytra";
	private static final String FLYING = "fly_in_creative_mode";
	private static final String NEARLY_BROKEN_ITEM = "hold_a_nearly_broken_item";
	private static final String LIT_TNT = "light_tnt";
	private static final String HERO_OF_THE_VILLAGE = "hero_of_the_village";
	private static final String HERO_OF_THE_VILLAGE_BABY = "see_the_hero_of_the_village";
	private static final String MANY_EFFECTS = "multiple_status_effects";
	private static final String STANDING_STILL = "stand_completely_still";
	private static final int STANDING_STILL_TICKS = 2400;
	private static final String TRAMPLED_CROPS = "trample_crops";
	private static final String ATE_FOOD = "eat_food";
	private static final String SHEARED_SHEEP = "shear_a_sheep";
	private static final String SHEARED_WOOLY = "shear_wooly";
	private static final String USED_LEAD = "use_a_lead";
	private static final String STARING = "stare_at_a_villager";
	private static final double STARE_RANGE = 8;
	private static final double STARE_CONE = 0.97;
	private static final int STARE_TICKS = 80;

	private static final Map<Player, Long> lastBreak = new WeakHashMap<>();
	private static final Map<Player, Integer> quickBreaks = new WeakHashMap<>();
	/** By UUID: a respawned player is a new entity. */
	private static final Map<java.util.UUID, Long> lastDeath = new java.util.HashMap<>();
	private static final Map<Player, Vec3> stillAt = new WeakHashMap<>();
	private static final Map<Player, Long> stillSince = new WeakHashMap<>();

	/** A player looking at a villager: since when, and whether they held a sword throughout (then no comment). */
	private static final class Stare {
		final LivingEntity villager;
		final long since;
		boolean armed = true;

		Stare(LivingEntity villager, long since) {
			this.villager = villager;
			this.since = since;
		}
	}

	private static final Map<Player, Stare> stares = new WeakHashMap<>();

	private PlayerActionReactions() {
	}

	public static void init() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel server) {
				broke(server, player, state);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				died(player);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PrimedTnt tnt && tnt.getFuse() >= 79) {
				litTnt(level, tnt);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(PlayerActionReactions::tick);
		com.javafied.villagernews.platform.UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand == net.minecraft.world.InteractionHand.MAIN_HAND && level instanceof ServerLevel server
					&& player.getItemInHand(hand).is(net.minecraft.world.item.Items.LEAD)) {
				if (entity instanceof LivingEntity living && Speakers.kindOf(living) != null) {
					Reactions.say(living, USED_LEAD, toward(player));
				} else if (entity instanceof net.minecraft.world.entity.Leashable) {
					Reactions.nearest(server, player.position(), USED_LEAD, toward(player));
				}
			}
			return net.minecraft.world.InteractionResult.PASS;
		});
	}

	/** Called (through a mixin) when farmland under a crop is trampled back to dirt. */
	public static void trampled(ServerLevel level, BlockPos farmland) {
		Reactions.nearest(level, Vec3.atCenterOf(farmland), TRAMPLED_CROPS, Options.DEFAULT.facing(Vec3.atCenterOf(farmland.above())));
	}

	/** Called (through a mixin) when a player finishes eating. */
	public static void ate(ServerPlayer player) {
		Reactions.nearest(player.serverLevel(), player.position(), ATE_FOOD, toward(player));
	}

	/** Called (through a mixin) when a sheep is sheared: villagers react; Wooly complains himself. */
	public static void sheared(ServerLevel level, LivingEntity sheep) {
		if (Speakers.kindOf(sheep) == Speakers.Kind.WOOLY) {
			Player shearer = level.getNearestPlayer(sheep, 6);
			Options options = Options.DEFAULT.withKinds(Speakers.Kind.WOOLY).ignoringCooldowns(true, true, true).asUrgent();
			Reactions.say(sheep, SHEARED_WOOLY, shearer == null ? options : options.facing(shearer));
		} else if (Speakers.kindOf(sheep) == null) {
			Reactions.nearest(level, sheep.position(), SHEARED_SHEEP, Options.DEFAULT.facing(sheep));
		}
	}

	private static Options toward(Player player) {
		return Options.DEFAULT.facing(player);
	}

	/** Called (through a mixin) when a player places a block. */
	public static void placed(ServerLevel level, Player player, BlockPos pos, BlockState state) {
		List<String> options = new ArrayList<>();
		String id = BlockCategories.id(state);
		String exact = id.endsWith("lightning_rod") ? PLACED_LIGHTNING_ROD : PLACED_BLOCK.get(id);
		if (exact != null) {
			options.add(exact);
		}
		for (Category category : PLACED_CATEGORIES) {
			boolean matches = category.dialog().equals(PLACED_IRON_GOLEM_FRAME)
					? state.is(Blocks.IRON_BLOCK) && BlockCategories.completesIronGolemFrame(level, pos)
					: category.test().test(state);
			if (matches) {
				options.add(category.dialog());
			}
		}
		for (String dialog : options) {
			if (Reactions.nearest(level, player.position(), dialog, toward(player)) != null) {
				return;
			}
		}
	}

	private static void broke(ServerLevel level, Player player, BlockState state) {
		long now = level.getServer().getTickCount();
		Long last = lastBreak.get(player);
		int streak = last != null && now < last + QUICK_BREAK_TICKS ? quickBreaks.getOrDefault(player, 0) + 1 : 0;
		lastBreak.put(player, now);
		quickBreaks.put(player, streak);
		if (state.is(BlockTags.CROPS)) {
			level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, player.getBoundingBox().inflate(12),
							v -> "farmer".equals(Speakers.profession(v)) && v.distanceTo(player) <= 12).stream()
					.sorted((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player))).limit(3)
					.forEach(farmer -> Reactions.say(farmer, HARVESTED_NEAR_FARMER, toward(player)));
		}
		String dialog = streak > 3 ? BROKE_MANY
				: state.is(Blocks.BELL) ? BROKE_BELL
				: state.is(BlockTags.BEDS) ? BROKE_BED
				: BlockCategories.door(state) ? BROKE_DOOR
				: BlockCategories.workstation(state) ? BROKE_WORKSTATION
				: BlockCategories.wood(state) ? BROKE_WOOD
				: state.is(BlockTags.CROPS) ? HARVESTED_CROPS
				: BlockCategories.stone(state) ? BROKE_STONE
				: BlockCategories.decorative(state) ? BROKE_DECORATION
				: BROKE_BLOCK;
		Reactions.nearest(level, player.position(), dialog, toward(player));
	}

	/** Called (through a mixin) when a player's game mode changes. */
	public static void gameModeChanged(ServerPlayer player, GameType mode) {
		Reactions.nearest(player.serverLevel(), player.position(), mode == GameType.CREATIVE ? SWITCHED_TO_CREATIVE : CHANGED_GAME_MODE,
				toward(player));
	}

	private static void died(ServerPlayer player) {
		long now = player.serverLevel().getServer().getTickCount();
		Long last = lastDeath.put(player.getUUID(), now);
		String dialog = player.serverLevel().getLevelData().isHardcore() ? PLAYER_DIED_HARDCORE
				: last != null && last + DIED_AGAIN_TICKS > now ? PLAYER_DIED_AGAIN : PLAYER_DIED;
		Reactions.nearest(player.serverLevel(), player.position(), dialog, Options.DEFAULT.facing(player.position()));
	}

	private static void litTnt(ServerLevel level, PrimedTnt tnt) {
		Player player = level.getNearestPlayer(tnt, 6);
		if (player != null) {
			Reactions.nearest(level, tnt.position(), LIT_TNT, toward(player));
		}
	}

	private static void tick(MinecraftServer server) {
		if (DialogEngine.get() == null) {
			return;
		}
		int tick = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.serverLevel();
			if (tick % 10 == 0) {
				stare(player, tick, tick % 40 == 0);
			}
			if (tick % 40 == 0) {
				if (player.isFallFlying()) {
					Reactions.nearest(level, player.position(), GLIDING, toward(player));
				} else if (player.getAbilities().flying && !player.isSpectator()) {
					Reactions.nearest(level, player.position(), FLYING, toward(player));
				}
			}
			if (tick % 60 == 0 && !player.isSpectator()) {
				if (player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) {
					Reactions.nearestByAge(level, player.position(), HERO_OF_THE_VILLAGE, HERO_OF_THE_VILLAGE_BABY,
							toward(player), Reactions.NEARBY, null);
				}
				if (player.getActiveEffects().size() > 1) {
					Reactions.nearest(level, player.position(), MANY_EFFECTS, toward(player));
				}
			}
			if (tick % 200 == 0) {
				ItemStack held = player.getMainHandItem();
				if (held.isDamageableItem() && held.getDamageValue() > 0.8 * held.getMaxDamage()) {
					Reactions.nearest(level, player.position(), NEARLY_BROKEN_ITEM, toward(player));
				}
			}
			if (tick % 400 == 0) {
				standingStill(player, tick);
			}
		}
	}

	private static void standingStill(ServerPlayer player, long now) {
		Vec3 at = player.position();
		if (!at.equals(stillAt.get(player))) {
			stillAt.put(player, at);
			stillSince.put(player, now);
		} else if (stillSince.getOrDefault(player, now) + STANDING_STILL_TICKS <= now) {
			Reactions.nearest(player.serverLevel(), at, STANDING_STILL, Options.DEFAULT);
		}
	}

	/**
	 * Looking straight at a villager within 8 blocks for 4 seconds gets a
	 * comment - unless you held a sword the whole time.
	 */
	private static void stare(ServerPlayer player, long now, boolean lookForNew) {
		Stare stare = stares.get(player);
		if (stare != null) {
			if (player.isSpectator() || !looksAt(player, stare.villager)) {
				stares.remove(player);
				return;
			}
			if (stare.armed && !player.getMainHandItem().is(ItemTags.SWORDS)) {
				stare.armed = false;
			}
			if (stare.since + STARE_TICKS <= now) {
				if (!stare.armed) {
					Reactions.say(stare.villager, STARING, toward(player));
				}
				stares.remove(player);
			}
		} else if (lookForNew && !player.isSpectator()) {
			for (LivingEntity candidate : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
					player.getBoundingBox().inflate(STARE_RANGE), e -> Speakers.kindOf(e) != null)) {
				if (looksAt(player, candidate)) {
					stares.put(player, new Stare(candidate, now));
					break;
				}
			}
		}
	}

	private static boolean looksAt(Player player, LivingEntity target) {
		if (!target.isAlive() || target.level() != player.level()) {
			return false;
		}
		Vec3 to = target.position().add(0, 1, 0).subtract(player.getEyePosition());
		double distance = to.length();
		return distance > 0 && distance <= STARE_RANGE && player.getLookAngle().dot(to.scale(1 / distance)) >= STARE_CONE;
	}
}
