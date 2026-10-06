package com.javafied.villagernews.dialog;

import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.behavior.BehaviorDefinitions;
import com.javafied.villagernews.behavior.BehaviorProperties;
import com.javafied.villagernews.behavior.BehaviorSensors;
import com.javafied.villagernews.content.ModAttachments;
import com.javafied.villagernews.content.ModItems;
import com.javafied.villagernews.dialog.DialogEngine.Options;
import com.javafied.villagernews.dialog.DialogEngine.State;
import com.javafied.villagernews.names.AddonNames;

import com.google.gson.JsonPrimitive;

import com.javafied.villagernews.platform.ServerTickEvents;
import com.javafied.villagernews.platform.UseEntityCallback;
import com.javafied.villagernews.platform.ServerPlayConnectionEvents;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hand port of the add-on script's item interactions: give a villager a hat,
 * the microphone or the moustache to wear, take it back with shears - or,
 * failing that, its nose - and hand the nose back. A sign to hold up (an axe
 * turns it to the next of its messages, shears take it back). Plus the
 * reaction to a player wearing a villager's nose, and the handbook every
 * player starts with.
 */
public final class VillagerItemReactions {
	/** The property holding what a villager wears ("none" or the item's add-on id). */
	private static final String PROPERTY_ACCESSORY = "accessory";
	/** Whether the villager still has its nose. */
	private static final String PROPERTY_NOSE = "nose";
	private static final String NONE = "none";
	/** Which sign a villager holds up: the wood's index in {@link #SIGN_WOODS}, -1 for none. */
	private static final String PROPERTY_SIGN = "sign";
	/** Which of the sign's messages faces out. */
	private static final String PROPERTY_SIGN_MESSAGE = "sign_message";
	private static final int SIGN_MESSAGES = 87;
	private static final List<String> SIGN_WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
			"mangrove", "cherry", "pale_oak", "bamboo", "crimson", "warped");
	private static final String GIVEN_SIGN = "give_a_villager_a_sign";

	private static final String TAKEN_ACCESSORY = "remove_a_cosmetic";
	private static final String TAKEN_NOSE = "shear_off_a_villagers_nose";
	private static final String NOSE_RETURNED = "give_a_villager_a_nose";
	private static final String ALREADY_HAS_NOSE = "try_to_give_a_second_nose";
	private static final String DRESSES_THEMSELVES = "give_a_villager_a_cosmetic";
	private static final String SAW_MY_NOSE = "wear_a_villager_nose";
	private static final String ITEM_MICROPHONE = "microphone";
	/** Reaction to being given each accessory (by item), for adults (said twice as often as {@link #DRESSES_THEMSELVES}) and babies. */
	private static final Map<String, String> ADULT_GIFT = Map.of("testificate_man_helmet", "give_a_villager_testificate_mans_helmet",
			ITEM_MICROPHONE, "give_a_villager_a_microphone", "moustache", "give_a_villager_a_moustache");
	private static final Map<String, String> BABY_GIFT = Map.of("mayor_hat", "give_a_baby_the_mayor_hat",
			"testificate_man_helmet", "give_a_baby_testificate_mans_helmet", ITEM_MICROPHONE, "give_a_baby_villager_9s_microphone",
			"moustache", "give_a_baby_villager_5s_moustache");
	/** What villagers can be given to wear. */
	private static final Set<String> ACCESSORIES = Set.of("mayor_hat", "testificate_man_helmet", ITEM_MICROPHONE, "moustache");

	private static final Map<Player, Boolean> wearingNose = new WeakHashMap<>();

	private VillagerItemReactions() {
	}

	public static void init() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(entity instanceof Villager villager) || villager.isSleeping()) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			boolean signs = Speakers.kindOf(villager) == Speakers.Kind.VILLAGER && !Speakers.isBaby(villager)
					&& (signWood(stack) >= 0 || (stack.is(ItemTags.AXES) || stack.is(Items.SHEARS)) && holdsSign(villager));
			if (!signs && !stack.is(Items.SHEARS) && !stack.is(ModItems.VILLAGER_NOSE.get()) && !ACCESSORIES.contains(itemPath(stack))) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server) {
				BehaviorDefinitions.Definition definition = BehaviorSensors.definitionOf(villager);
				if (definition == null) {
					return InteractionResult.PASS; // add-on not converted: leave vanilla alone
				}
				BehaviorProperties properties = new BehaviorProperties(villager, definition);
				if (!signs || !sign(server, player, villager, stack, properties)) {
					interact(server, player, villager, stack, properties);
				}
			}
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(VillagerItemReactions::watchNoseWearers);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> giveHandbookOnce(handler.getPlayer()));
	}

	private static void interact(ServerLevel level, Player player, Villager villager, ItemStack stack, BehaviorProperties properties) {
		String accessory = AddonNames.nameOf(AddonNames.Kind.ITEM, String.valueOf(properties.named(PROPERTY_ACCESSORY)));
		boolean hasNose = !Boolean.FALSE.equals(properties.named(PROPERTY_NOSE));
		if (stack.is(Items.SHEARS)) {
			if (!NONE.equals(accessory)) {
				dropFromHead(level, villager, new ItemStack(BuiltInRegistries.ITEM.get(VillagerNewsJavafied.id(accessory))));
				properties.setNamed(PROPERTY_ACCESSORY, new JsonPrimitive(NONE));
				react(villager, player, TAKEN_ACCESSORY, State.ADULT);
			} else if (hasNose && !Speakers.isBaby(villager)) {
				dropFromHead(level, villager, new ItemStack(ModItems.VILLAGER_NOSE.get()));
				properties.setNamed(PROPERTY_NOSE, new JsonPrimitive(false));
				react(villager, player, TAKEN_NOSE, State.ADULT);
			}
			return;
		}
		if (stack.is(ModItems.VILLAGER_NOSE.get())) {
			if (Speakers.isBaby(villager)) {
				return;
			}
			if (!hasNose) {
				stack.consume(1, player);
				properties.setNamed(PROPERTY_NOSE, new JsonPrimitive(true));
				react(villager, player, NOSE_RETURNED, State.ADULT);
			} else {
				DialogEngine engine = DialogEngine.get();
				DialogEngine.Speech speech = engine == null ? null : engine.speech(villager);
				if (speech == null || !speech.dialog().id().equals(ALREADY_HAS_NOSE)) {
					react(villager, player, ALREADY_HAS_NOSE, State.ADULT);
				}
			}
			return;
		}
		if (!NONE.equals(accessory)) {
			return;
		}
		String item = itemPath(stack);
		stack.consume(1, player);
		properties.setNamed(PROPERTY_ACCESSORY, new JsonPrimitive(AddonNames.item(item)));
		if (Speakers.isBaby(villager)) {
			react(villager, player, BABY_GIFT.get(item), State.BABY);
		} else {
			String special = ADULT_GIFT.get(item);
			List<String> options = item.equals(ITEM_MICROPHONE) ? List.of(special)
					: special == null ? List.of(DRESSES_THEMSELVES) : List.of(DRESSES_THEMSELVES, special, special);
			react(villager, player, options.get(ThreadLocalRandom.current().nextInt(options.size())), State.ADULT);
		}
	}

	/**
	 * The script's sign handling: hand a villager a sign (swapping out the one
	 * it holds, and the microphone - both go in its hands), turn it with an
	 * axe (sneak to turn it back), take it with shears.
	 * @return whether the sign handling took the interaction
	 */
	private static boolean sign(ServerLevel level, Player player, Villager villager, ItemStack stack, BehaviorProperties properties) {
		int held = properties.named(PROPERTY_SIGN) instanceof Double index ? index.intValue() : -1;
		boolean creative = player.getAbilities().instabuild;
		if (stack.is(Items.SHEARS)) {
			if (held < 0) {
				return false;
			}
			if (!creative) {
				dropFromHands(level, villager, signItem(held));
			}
			level.playSound(null, villager.getX(), villager.getY() + 0.5, villager.getZ(), SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 1f, 1f);
			properties.setNamed(PROPERTY_SIGN, new JsonPrimitive(-1));
			return true;
		}
		if (stack.is(ItemTags.AXES)) {
			int message = properties.named(PROPERTY_SIGN_MESSAGE) instanceof Double index ? index.intValue() : 0;
			properties.setNamed(PROPERTY_SIGN_MESSAGE, new JsonPrimitive(Math.floorMod(message + (player.isShiftKeyDown() ? -1 : 1), SIGN_MESSAGES)));
			level.playSound(null, villager.getX(), villager.getY() + 0.5, villager.getZ(), SoundEvents.HORSE_STEP_WOOD, SoundSource.NEUTRAL, 1f, 1f);
			return true;
		}
		int given = signWood(stack);
		if (given == held) {
			return true;
		}
		if (ITEM_MICROPHONE.equals(AddonNames.nameOf(AddonNames.Kind.ITEM, String.valueOf(properties.named(PROPERTY_ACCESSORY))))) {
			level.addFreshEntity(new ItemEntity(level, villager.getX(), villager.getEyeY(), villager.getZ(),
					new ItemStack(BuiltInRegistries.ITEM.get(VillagerNewsJavafied.id(ITEM_MICROPHONE)))));
			properties.setNamed(PROPERTY_ACCESSORY, new JsonPrimitive(NONE));
		}
		if (!creative) {
			stack.shrink(1);
			if (held >= 0) {
				dropFromHands(level, villager, signItem(held));
			}
		}
		level.playSound(null, villager.getX(), villager.getY() + 0.5, villager.getZ(), SoundEvents.HORSE_STEP_WOOD, SoundSource.NEUTRAL, 1f, 1f);
		properties.setNamed(PROPERTY_SIGN, new JsonPrimitive(given));
		if (held < 0) {
			properties.setNamed(PROPERTY_SIGN_MESSAGE, new JsonPrimitive(ThreadLocalRandom.current().nextInt(SIGN_MESSAGES)));
		}
		Reactions.say(villager, GIVEN_SIGN, Options.DEFAULT);
		return true;
	}

	/** The wood of a (standing, not hanging) sign item, -1 if it isn't one. */
	private static int signWood(ItemStack stack) {
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return id.getNamespace().equals("minecraft") && id.getPath().endsWith("_sign") && !id.getPath().endsWith("_hanging_sign")
				? SIGN_WOODS.indexOf(id.getPath().substring(0, id.getPath().length() - "_sign".length())) : -1;
	}

	private static ItemStack signItem(int wood) {
		return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(SIGN_WOODS.get(wood) + "_sign")));
	}

	private static boolean holdsSign(Villager villager) {
		String held = BehaviorProperties.read(villager, AddonNames.property(PROPERTY_SIGN));
		return held != null && !held.equals("-1");
	}

	private static void dropFromHands(ServerLevel level, Villager villager, ItemStack stack) {
		ItemEntity item = new ItemEntity(level, villager.getX(), villager.getY() + 0.5, villager.getZ(), stack);
		level.addFreshEntity(item);
	}

	private static String itemPath(ItemStack stack) {
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return id.getNamespace().equals(VillagerNewsJavafied.MOD_ID) ? id.getPath() : "";
	}

	/** The script's {@code yyofim}: said to the player, cutting off anything else, right away. */
	private static void react(Villager villager, Player player, String dialog, State state) {
		DialogEngine engine = DialogEngine.get();
		if (engine != null && dialog != null) {
			engine.request(villager, dialog, Options.DEFAULT.withStates(state).facing(player)
					.ignoringCooldowns(true, true, true).interrupting().asUrgent());
		}
	}

	private static void dropFromHead(ServerLevel level, Villager villager, ItemStack stack) {
		level.playSound(null, villager.getX(), villager.getEyeY(), villager.getZ(), SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 1f, 1f);
		level.addFreshEntity(new ItemEntity(level, villager.getX(), villager.getEyeY(), villager.getZ(), stack));
	}

	/** Putting a villager's nose on your own head gets a nearby villager asking after it. */
	private static void watchNoseWearers(MinecraftServer server) {
		DialogEngine engine = DialogEngine.get();
		if (engine == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean wearing = !player.isSpectator() && player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.VILLAGER_NOSE.get());
			Boolean before = wearingNose.put(player, wearing);
			if (wearing && Boolean.FALSE.equals(before)) {
				player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(DialogEngine.RANGE)).stream()
						.sorted((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player)))
						.filter(villager -> engine.request(villager, SAW_MY_NOSE, Options.DEFAULT.facing(player)))
						.findFirst();
			}
		}
	}

	/** The add-on hands every player the handbook the first time they join. */
	private static void giveHandbookOnce(ServerPlayer player) {
		if (Boolean.TRUE.equals(player.getExistingDataOrNull(ModAttachments.RECEIVED_HANDBOOK))) {
			return;
		}
		player.setData(ModAttachments.RECEIVED_HANDBOOK, true);
		if (!player.getInventory().contains(new ItemStack(ModItems.HANDBOOK.get()))) {
			player.getInventory().add(new ItemStack(ModItems.HANDBOOK.get()));
		}
	}
}
