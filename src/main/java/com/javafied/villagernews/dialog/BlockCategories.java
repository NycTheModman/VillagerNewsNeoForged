package com.javafied.villagernews.dialog;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * The block categories the add-on's reactions care about ("placed glass",
 * "broke a workstation", ...), decided the Java way - by tag and name -
 * instead of by the add-on's lists of Bedrock ids.
 */
public final class BlockCategories {
	private static final Set<String> WORKSTATIONS = Set.of("smithing_table", "lectern", "composter", "cauldron",
			"water_cauldron", "lava_cauldron", "powder_snow_cauldron", "fletching_table", "grindstone", "barrel",
			"brewing_stand", "smoker", "stonecutter", "cartography_table", "loom", "blast_furnace");
	private static final Set<String> LIGHTS = Set.of("light", "torch", "wall_torch", "soul_torch", "soul_wall_torch",
			"copper_torch", "copper_wall_torch", "campfire", "soul_campfire", "shroomlight", "glowstone", "redstone_torch",
			"redstone_wall_torch", "jack_o_lantern", "redstone_lamp", "lantern", "soul_lantern", "sea_lantern", "end_rod");
	private static final Set<String> REDSTONE = Set.of("redstone_lamp", "repeater", "redstone_wire", "trapped_chest",
			"tripwire_hook", "observer", "lever", "detector_rail", "daylight_detector", "sculk_sensor",
			"calibrated_sculk_sensor", "jukebox", "redstone_torch", "redstone_wall_torch", "redstone_block", "comparator");
	private static final Set<String> OCEAN = Set.of("sea_lantern", "kelp", "kelp_plant", "dried_kelp_block", "conduit");
	private static final Set<String> ICE = Set.of("ice", "packed_ice", "blue_ice", "frosted_ice");
	private static final Set<String> END = Set.of("end_stone", "end_stone_bricks", "end_stone_brick_stairs",
			"end_stone_brick_slab", "end_stone_brick_wall", "end_rod", "chorus_plant", "chorus_flower", "dragon_egg");
	private static final Set<String> NETHER = Set.of("netherrack", "nether_gold_ore", "nether_quartz_ore", "ancient_debris",
			"gilded_blackstone", "basalt", "smooth_basalt", "polished_basalt", "soul_sand", "soul_soil", "bone_block",
			"glowstone", "magma_block", "shroomlight", "crying_obsidian", "respawn_anchor", "nether_wart", "nether_wart_block",
			"nether_sprouts", "twisting_vines", "twisting_vines_plant", "weeping_vines", "weeping_vines_plant");
	private static final Set<String> VALUABLE = Set.of("copper_block", "iron_block", "lapis_block", "emerald_block",
			"diamond_block", "gold_block");
	private static final Set<String> GOLD = Set.of("gold_block", "gold_ore", "deepslate_gold_ore", "raw_gold_block");
	private static final Set<String> CREATIVE_ONLY = Set.of("barrier", "structure_void", "light", "command_block",
			"chain_command_block", "repeating_command_block", "structure_block", "jigsaw", "test_block", "test_instance_block");
	private static final Set<String> PLANTS = Set.of("short_grass", "tall_grass", "fern", "large_fern", "vine", "dead_bush",
			"sweet_berry_bush", "bamboo", "sugar_cane", "cactus", "lily_pad", "moss_carpet", "azalea", "flowering_azalea");

	private BlockCategories() {
	}

	public static String id(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
	}

	public static boolean workstation(BlockState state) {
		return WORKSTATIONS.contains(id(state));
	}

	public static boolean light(BlockState state) {
		return LIGHTS.contains(id(state));
	}

	public static boolean redstone(BlockState state) {
		String id = id(state);
		return REDSTONE.contains(id) || id.endsWith("lightning_rod") || state.is(BlockTags.BUTTONS)
				|| state.is(BlockTags.PRESSURE_PLATES);
	}

	public static boolean ocean(BlockState state) {
		String id = id(state);
		return OCEAN.contains(id) || id.contains("prismarine") || id.contains("coral");
	}

	public static boolean ice(BlockState state) {
		return ICE.contains(id(state));
	}

	public static boolean purpur(BlockState state) {
		return id(state).startsWith("purpur_");
	}

	public static boolean end(BlockState state) {
		return END.contains(id(state)) || purpur(state);
	}

	public static boolean nether(BlockState state) {
		String id = id(state);
		return NETHER.contains(id) || id.contains("nether_brick") || id.contains("quartz") || id.contains("blackstone")
				|| id.startsWith("warped_") || id.startsWith("crimson_") || id.startsWith("stripped_warped_")
				|| id.startsWith("stripped_crimson_");
	}

	public static boolean glass(BlockState state) {
		return id(state).contains("glass");
	}

	public static boolean concrete(BlockState state) {
		return id(state).endsWith("_concrete");
	}

	public static boolean concretePowder(BlockState state) {
		return id(state).endsWith("_concrete_powder");
	}

	public static boolean terracotta(BlockState state) {
		return id(state).endsWith("terracotta");
	}

	public static boolean glazedTerracotta(BlockState state) {
		return id(state).endsWith("_glazed_terracotta");
	}

	public static boolean valuable(BlockState state) {
		return VALUABLE.contains(id(state));
	}

	public static boolean gold(BlockState state) {
		return GOLD.contains(id(state));
	}

	public static boolean creativeOnly(BlockState state) {
		return CREATIVE_ONLY.contains(id(state));
	}

	public static boolean gravity(BlockState state) {
		return state.getBlock() instanceof FallingBlock || state.is(Blocks.SCAFFOLDING) || state.is(Blocks.POINTED_DRIPSTONE);
	}

	public static boolean plant(BlockState state) {
		return state.is(BlockTags.LEAVES) || id(state).endsWith("sapling") || state.is(BlockTags.FLOWERS)
				|| state.is(BlockTags.CROPS) || PLANTS.contains(id(state));
	}

	/** The add-on's "wood" tag: logs, planks and the wooden building blocks. */
	public static boolean wood(BlockState state) {
		return state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_SLABS)
				|| state.is(BlockTags.WOODEN_STAIRS) || state.is(BlockTags.WOODEN_FENCES);
	}

	public static boolean stone(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.STONE_BRICKS) || id(state).contains("cobblestone");
	}

	public static boolean door(BlockState state) {
		return state.is(BlockTags.DOORS);
	}

	/** Pots, banners (item frames are entities in Java). */
	public static boolean decorative(BlockState state) {
		String id = id(state);
		return id.equals("flower_pot") || id.startsWith("potted_") || id.equals("decorated_pot") || state.is(BlockTags.BANNERS);
	}

	/**
	 * The add-on's check for a just-placed iron block completing the T of an
	 * iron golem: three iron blocks in a row along x or z, one more under the
	 * middle, the placed block anywhere in it.
	 */
	public static boolean completesIronGolemFrame(Level level, BlockPos placed) {
		for (boolean alongX : new boolean[] {true, false}) {
			for (int offset = -1; offset <= 1; offset++) {
				if (ironT(level, alongX ? placed.offset(offset, 0, 0) : placed.offset(0, 0, offset), alongX)) {
					return true;
				}
			}
			if (ironT(level, placed.above(), alongX)) {
				return true;
			}
		}
		return false;
	}

	private static boolean ironT(Level level, BlockPos middle, boolean alongX) {
		BlockPos side = alongX ? new BlockPos(1, 0, 0) : new BlockPos(0, 0, 1);
		return iron(level, middle) && iron(level, middle.subtract(side)) && iron(level, middle.offset(side)) && iron(level, middle.below());
	}

	private static boolean iron(Level level, BlockPos pos) {
		return level.getBlockState(pos).is(Blocks.IRON_BLOCK);
	}
}
