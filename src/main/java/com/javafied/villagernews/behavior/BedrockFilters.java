package com.javafied.villagernews.behavior;

import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.dialog.DialogEngine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Evaluates Bedrock entity filters ({@code {"all_of": [{"test": "is_family",
 * "subject": "other", "value": "player"}, ...]}}) against Java entities -
 * the tests the add-on's sensors use. An unknown test is false (and logged
 * once), so a filter we can't answer never fires.
 */
public final class BedrockFilters {
	/** The entity running the filter, the one it's looking at, and the runner's Bedrock properties. */
	public record Context(LivingEntity self, Entity other, BehaviorProperties properties) {
	}

	private static final Set<String> WARNED = new HashSet<>();

	private static final Set<String> COOKED = Set.of("cooked_beef", "cooked_porkchop", "cooked_chicken", "cooked_mutton",
			"cooked_rabbit", "cooked_cod", "cooked_salmon", "baked_potato");
	/** Bedrock item tags the add-on uses, as Java tests. Anything else falls back to the Java tag of the same name. */
	private static final Map<String, Predicate<ItemStack>> ITEM_TAGS = Map.of(
			"minecraft:boats", stack -> stack.is(ItemTags.BOATS) || stack.is(ItemTags.CHEST_BOATS),
			"minecraft:egg", stack -> stack.is(net.minecraft.world.item.Items.EGG),
			"minecraft:is_tool", stack -> stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES)
					|| stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES),
			"minecraft:is_sword", stack -> stack.is(ItemTags.SWORDS),
			"minecraft:is_spear", stack -> stack.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,net.minecraft.resources.ResourceLocation.withDefaultNamespace("spears"))),
			"minecraft:is_trident", stack -> stack.is(net.minecraft.world.item.Items.TRIDENT),
			"minecraft:music_disc", stack -> stack.has(DataComponents.JUKEBOX_PLAYABLE),
			"minecraft:is_cooked", stack -> COOKED.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()));
	/** Bedrock effect ids that differ from Java's. */
	private static final Map<String, String> EFFECTS = Map.of("village_hero", "hero_of_the_village");

	private BedrockFilters() {
	}

	public static boolean test(JsonElement filter, Context context) {
		if (filter == null || filter.isJsonNull()) {
			return true;
		}
		if (filter.isJsonArray()) {
			return all(filter.getAsJsonArray(), context);
		}
		JsonObject object = filter.getAsJsonObject();
		if (object.has("all_of")) {
			return all(asArray(object.get("all_of")), context);
		}
		if (object.has("any_of")) {
			for (JsonElement child : asArray(object.get("any_of"))) {
				if (test(child, context)) {
					return true;
				}
			}
			return false;
		}
		if (object.has("none_of")) {
			for (JsonElement child : asArray(object.get("none_of"))) {
				if (test(child, context)) {
					return false;
				}
			}
			return true;
		}
		return single(object, context);
	}

	private static boolean all(JsonArray filters, Context context) {
		for (JsonElement child : filters) {
			if (!test(child, context)) {
				return false;
			}
		}
		return true;
	}

	private static JsonArray asArray(JsonElement e) {
		if (e.isJsonArray()) {
			return e.getAsJsonArray();
		}
		JsonArray array = new JsonArray();
		array.add(e);
		return array;
	}

	private static boolean single(JsonObject filter, Context context) {
		String test = filter.get("test").getAsString();
		String subjectName = filter.has("subject") ? filter.get("subject").getAsString() : "self";
		Entity subject = "other".equals(subjectName) ? context.other() : context.self();
		String operator = filter.has("operator") ? filter.get("operator").getAsString() : "equals";
		JsonElement value = filter.has("value") ? filter.get("value") : new JsonPrimitive(true);
		String domain = filter.has("domain") ? filter.get("domain").getAsString() : null;
		if (subject == null) {
			return false;
		}
		LivingEntity living = subject instanceof LivingEntity l ? l : null;
		Level level = subject.level();
		BlockPos pos = subject.blockPosition();
		return switch (test) {
			case "is_family" -> compare(families(subject).contains(value.getAsString()), operator, true);
			case "is_sleeping" -> compare(living != null && living.isSleeping(), operator, value);
			case "is_sneaking" -> compare(subject.isShiftKeyDown(), operator, value);
			case "is_moving" -> compare(isMoving(subject), operator, value);
			case "is_riding" -> compare(subject.isPassenger(), operator, value);
			case "is_baby" -> compare(BehaviorSensors.isBaby(subject), operator, value);
			case "on_ground" -> compare(subject.onGround(), operator, value);
			case "in_water" -> compare(subject.isInWater(), operator, value);
			case "in_lava" -> compare(subject.isInLava(), operator, value);
			case "is_daytime" -> compare(timeOfDay(level) < 12000, operator, value);
			case "is_underground" -> compare(!level.canSeeSky(pos), operator, value);
			case "is_in_village" -> compare(level instanceof ServerLevel server && server.isVillage(pos), operator, value);
			case "actor_health" -> compare(living == null ? 0 : living.getHealth(), operator, value);
			case "light_level" -> compare(level.getMaxLocalRawBrightness(pos), operator, value);
			case "hourly_clock_time" -> compare(timeOfDay(level), operator, value);
			case "is_difficulty" -> compare(level.getDifficulty().getSerializedName(), operator, value);
			case "distance_to_nearest_player" -> {
				Player nearest = level.getNearestPlayer(subject, 256);
				yield compare(nearest == null ? Double.MAX_VALUE : nearest.distanceTo(subject), operator, value);
			}
			case "bool_property", "int_property", "float_property", "enum_property" ->
					subject == context.self() && domain != null && compare(context.properties().get(domain), operator, value);
			case "has_mob_effect" -> compare(living != null && hasEffect(living, value.getAsString()), operator, true);
			case "has_equipment" -> compare(living != null && anyEquipment(living, domain,
					stack -> itemId(stack).equals(qualified(value.getAsString()))), operator, true);
			case "has_equipment_tag" -> compare(living != null && anyEquipment(living, domain,
					stack -> hasTag(stack, value.getAsString())), operator, true);
			// Java has one panic for both: fleeing danger, and keeping away from mobs (or the Untouchable from players).
			case "is_panicking", "is_avoiding_mobs" -> compare(living != null && DialogEngine.panicking(living), operator, value);
			// A merchant with something left to sell (the wandering trader notices when it's sold out).
			case "has_trade_supply" -> compare(subject instanceof AbstractVillager merchant
					&& merchant.getOffers().stream().anyMatch(offer -> !offer.isOutOfStock()), operator, value);
			default -> {
				if (WARNED.add(test)) {
					VillagerNewsJavafied.LOGGER.debug("Bedrock filter test '{}' isn't ported; treating it as false", test);
				}
				yield false;
			}
		};
	}

	/** Bedrock operators; strings compare case-insensitively, booleans and numbers by value. */
	static boolean compare(Object actual, String operator, Object expected) {
		int order;
		if (expected instanceof JsonPrimitive p) {
			expected = p.isBoolean() ? (Object) p.getAsBoolean() : p.isNumber() ? (Object) p.getAsDouble() : p.getAsString();
		}
		if (actual instanceof Boolean a) {
			boolean e = expected instanceof Boolean b ? b : expected instanceof Number n ? n.doubleValue() != 0
					: Boolean.parseBoolean(String.valueOf(expected));
			order = Boolean.compare(a, e);
		} else if (actual instanceof Number a) {
			double e = expected instanceof Number n ? n.doubleValue() : expected instanceof Boolean b ? (b ? 1 : 0)
					: Double.parseDouble(String.valueOf(expected));
			order = Double.compare(a.doubleValue(), e);
		} else {
			order = String.valueOf(actual).toLowerCase(Locale.ROOT).compareTo(String.valueOf(expected).toLowerCase(Locale.ROOT));
		}
		return switch (operator) {
			case "!=", "<>", "not" -> order != 0;
			case "<" -> order < 0;
			case "<=" -> order <= 0;
			case ">" -> order > 0;
			case ">=" -> order >= 0;
			default -> order == 0;
		};
	}

	private static Set<String> families(Entity entity) {
		Set<String> families = new HashSet<>();
		families.add(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath());
		if (entity instanceof Player) {
			families.add("player");
		}
		if (entity instanceof Villager) {
			families.add("villager");
		}
		if (entity instanceof Enemy) {
			families.add("monster");
		}
		if (entity instanceof LivingEntity) {
			families.add("mob");
		}
		return families;
	}

	/** Players' own velocity isn't known server-side; use how far they moved since last tick. */
	private static boolean isMoving(Entity entity) {
		double dx = entity.getX() - entity.xo;
		double dz = entity.getZ() - entity.zo;
		return dx * dx + dz * dz > 1e-4 || entity.getDeltaMovement().horizontalDistanceSqr() > 1e-4;
	}

	private static long timeOfDay(Level level) {
		return Math.floorMod(level.getDayTime(), 24000L);
	}

	private static boolean hasEffect(LivingEntity living, String bedrockId) {
		ResourceLocation id = ResourceLocation.withDefaultNamespace(EFFECTS.getOrDefault(bedrockId, bedrockId));
		return BuiltInRegistries.MOB_EFFECT.getHolder(id).map(effect -> living.hasEffect((Holder<MobEffect>) effect)).orElse(false);
	}

	private static final Map<String, List<EquipmentSlot>> SLOTS = Map.of(
			"hand", List.of(EquipmentSlot.MAINHAND), "mainhand", List.of(EquipmentSlot.MAINHAND),
			"offhand", List.of(EquipmentSlot.OFFHAND), "head", List.of(EquipmentSlot.HEAD),
			"torso", List.of(EquipmentSlot.CHEST), "chest", List.of(EquipmentSlot.CHEST),
			"leg", List.of(EquipmentSlot.LEGS), "legs", List.of(EquipmentSlot.LEGS), "feet", List.of(EquipmentSlot.FEET));

	private static boolean anyEquipment(LivingEntity living, String domain, Predicate<ItemStack> test) {
		List<EquipmentSlot> slots = SLOTS.getOrDefault(domain == null ? "any" : domain, List.of(EquipmentSlot.values()));
		for (EquipmentSlot slot : slots) {
			ItemStack stack = living.getItemBySlot(slot);
			if (!stack.isEmpty() && test.test(stack)) {
				return true;
			}
		}
		return false;
	}

	private static String itemId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static String qualified(String id) {
		return id.contains(":") ? id : "minecraft:" + id;
	}

	private static boolean hasTag(ItemStack stack, String tag) {
		Predicate<ItemStack> known = ITEM_TAGS.get(qualified(tag));
		if (known != null) {
			return known.test(stack);
		}
		ResourceLocation id = ResourceLocation.tryParse(qualified(tag));
		return id != null && stack.is(TagKey.<Item>create(net.minecraft.core.registries.Registries.ITEM, id));
	}
}
