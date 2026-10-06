package com.javafied.villagernews.dialog;

import com.javafied.villagernews.dialog.DialogEngine.Options;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hand port of the add-on script's work chatter: during working hours an
 * employed villager standing at its workstation talks about its trade (or
 * just about standing at a workstation), a librarian browses bookshelves, a
 * farmer tends crops - and one that hasn't been near its workstation for a
 * long while complains it can't reach it.
 */
public final class WorkReactions {
	private static final String START_WORK = "start_work";
	private static final Map<String, String> AT_WORK = Map.ofEntries(Map.entry("armorer", "armorer_at_work"),
			Map.entry("butcher", "butcher_at_work"), Map.entry("cartographer", "cartographer_at_work"), Map.entry("cleric", "cleric_at_work"),
			Map.entry("farmer", "farmer_at_work"), Map.entry("fisherman", "fisherman_at_work"), Map.entry("fletcher", "fletcher_at_work"),
			Map.entry("librarian", "librarian_at_work"), Map.entry("leatherworker", "leatherworker_at_work"), Map.entry("mason", "mason_at_work"),
			Map.entry("shepherd", "shepherd_at_work"), Map.entry("toolsmith", "toolsmith_at_work"), Map.entry("weaponsmith", "weaponsmith_at_work"));
	private static final Map<String, String> BLOCK_WORKSTATION = Map.ofEntries(Map.entry("armorer", "blast_furnace"),
			Map.entry("butcher", "smoker"), Map.entry("cartographer", "cartography_table"), Map.entry("cleric", "brewing_stand"),
			Map.entry("farmer", "composter"), Map.entry("fisherman", "barrel"), Map.entry("fletcher", "fletching_table"),
			Map.entry("librarian", "lectern"), Map.entry("leatherworker", "cauldron"), Map.entry("mason", "stonecutter"),
			Map.entry("shepherd", "loom"), Map.entry("toolsmith", "smithing_table"), Map.entry("weaponsmith", "grindstone"));
	private static final String NEAR_WORKSTATION = "stand_near_a_workstation";
	private static final String CANNOT_REACH_WORKSTATION = "cannot_reach_workstation";
	private static final String BROWSES_BOOKSHELF = "inspect_bookshelves";
	private static final String FARMING = "farming";
	private static final int SINCE_WORKSTATION_TICKS = 12000;
	private static final int SINCE_CANNOT_REACH_TICKS = 2400;
	private static final int FARMING_MEMORY_TICKS = 160;

	private static final Map<Villager, Long> lastAtWorkstation = new WeakHashMap<>();
	private static final Map<Villager, Long> lastCannotReach = new WeakHashMap<>();
	private static final Map<Villager, Long> lastFarmed = new WeakHashMap<>();

	private WorkReactions() {
	}

	/** The script's working hours for this: employed adults, day ticks 0-8000 and 10000-11000. */
	static boolean atWorkHours(Villager villager) {
		String profession = Speakers.profession(villager);
		long time = Math.floorMod(villager.level().getDayTime(), 24000L);
		return AT_WORK.containsKey(profession) && !Speakers.isBaby(villager) && (time < 8000 || time >= 10000 && time < 11000);
	}

	/**
	 * Called (through a mixin) when a villager restocks its trades, which Java
	 * does as it gets to its workstation for its shift: "Start Work", which the
	 * add-on ships but never triggers.
	 */
	public static void restocked(Villager villager) {
		if (villager.level() instanceof ServerLevel && Speakers.kindOf(villager) == Speakers.Kind.VILLAGER && !Speakers.isBaby(villager)) {
			Reactions.say(villager, START_WORK, Options.DEFAULT);
		}
	}

	/** @return whether a work line was asked for */
	static boolean chatter(ServerLevel level, Villager villager) {
		String profession = Speakers.profession(villager);
		long now = level.getServer().getTickCount();
		if (profession.equals("librarian") && still(villager) && lookingAt(level, villager, 1, state -> state.is(Blocks.BOOKSHELF))) {
			return Reactions.say(villager, BROWSES_BOOKSHELF, Options.DEFAULT);
		}
		if (profession.equals("farmer") && (tending(level, villager, now) || now - lastFarmed.getOrDefault(villager, -10000L) <= FARMING_MEMORY_TICKS)) {
			return Reactions.say(villager, FARMING, Options.DEFAULT);
		}
		if (atWorkstation(level, villager, profession)) {
			lastAtWorkstation.put(villager, now);
			String line = ThreadLocalRandom.current().nextDouble() < 0.3 ? NEAR_WORKSTATION : AT_WORK.get(profession);
			return Reactions.say(villager, line, Options.DEFAULT);
		}
		if (now - lastAtWorkstation.getOrDefault(villager, 0L) > SINCE_WORKSTATION_TICKS
				&& now - lastCannotReach.getOrDefault(villager, -SINCE_CANNOT_REACH_TICKS - 1L) > SINCE_CANNOT_REACH_TICKS) {
			lastCannotReach.put(villager, now);
			return Reactions.say(villager, CANNOT_REACH_WORKSTATION, Options.DEFAULT);
		}
		return false;
	}

	/** Standing still with its workstation in view within 3 blocks, or within a block around it. */
	private static boolean atWorkstation(ServerLevel level, Villager villager, String profession) {
		String station = BLOCK_WORKSTATION.get(profession);
		if (station == null || !still(villager)) {
			return false;
		}
		java.util.function.Predicate<BlockState> isStation = state -> BlockCategories.id(state).equals(station)
				|| station.equals("cauldron") && state.is(BlockTags.CAULDRONS);
		if (lookingAt(level, villager, 3, isStation)) {
			return true;
		}
		BlockPos feet = villager.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 1, 1))) {
			if (isStation.test(level.getBlockState(pos))) {
				return true;
			}
		}
		return false;
	}

	/** On farmland under crops, looking at crops within 3 blocks. */
	private static boolean tending(ServerLevel level, Villager villager, long now) {
		BlockPos below = villager.blockPosition().below();
		boolean onField = level.getBlockState(below).is(Blocks.FARMLAND) && level.getBlockState(below.above()).is(BlockTags.CROPS)
				|| level.getBlockState(villager.blockPosition()).is(BlockTags.CROPS);
		if (onField && lookingAt(level, villager, 3, state -> state.is(BlockTags.CROPS))) {
			lastFarmed.put(villager, now);
			return true;
		}
		return false;
	}

	private static boolean still(Villager villager) {
		return villager.getDeltaMovement().horizontalDistance() <= 0.02;
	}

	private static boolean lookingAt(ServerLevel level, Villager villager, double range,
			java.util.function.Predicate<BlockState> test) {
		Vec3 eye = villager.getEyePosition();
		Vec3 end = eye.add(villager.getViewVector(1).scale(range));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, villager));
		return hit.getType() == HitResult.Type.BLOCK && test.test(level.getBlockState(hit.getBlockPos()));
	}
}
