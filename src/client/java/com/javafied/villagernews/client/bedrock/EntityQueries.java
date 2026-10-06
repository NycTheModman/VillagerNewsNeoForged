package com.javafied.villagernews.client.bedrock;

import com.javafied.villagernews.content.ModAttachments;

import com.google.gson.JsonElement;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import team.unnamed.mocha.runtime.value.Function;
import team.unnamed.mocha.runtime.value.ObjectProperty;
import team.unnamed.mocha.runtime.value.ObjectValue;
import team.unnamed.mocha.runtime.value.StringValue;
import team.unnamed.mocha.runtime.value.Value;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bedrock's {@code q.*} queries, answered from the Java entity the reskin is
 * drawn over. Only the queries the add-on actually uses are implemented;
 * anything else reads as 0, same as an unknown query in Bedrock.
 */
final class EntityQueries implements ObjectValue {
	/** Bedrock villager_v2 "minecraft:variant" order; index = profession. */
	private static final List<String> PROFESSIONS = List.of("none", "farmer", "fisherman", "shepherd", "fletcher",
			"librarian", "cartographer", "cleric", "armorer", "weaponsmith", "toolsmith", "butcher", "leatherworker",
			"mason", "nitwit");
	/** Bedrock villager_v2 "minecraft:mark_variant" order; index = biome type. */
	private static final List<String> BIOMES = List.of("plains", "desert", "jungle", "savanna", "snow", "swamp", "taiga");

	private final Entity entity;
	private final float partialTick;
	private final Map<String, JsonElement> properties;
	/** Property values the add-on's script would have written (see {@link VillagerPuppetPort}); win over defaults. */
	private final Map<String, Value> propertyOverrides;

	/** Whether its behavior definition makes it a baby for good (the Mayor), whatever the Java entity is. */
	private final boolean alwaysBaby;

	EntityQueries(Entity entity, float partialTick, Map<String, JsonElement> properties, Map<String, Value> propertyOverrides,
			boolean alwaysBaby) {
		this.alwaysBaby = alwaysBaby;
		this.entity = entity;
		this.partialTick = partialTick;
		this.properties = properties;
		this.propertyOverrides = propertyOverrides;
	}

	@Override
	public ObjectProperty getProperty(String name) {
		return ObjectProperty.property(value(name.toLowerCase(Locale.ROOT)), true);
	}

	private Value value(String name) {
		LivingEntity living = entity instanceof LivingEntity l ? l : null;
		return switch (name) {
			case "is_baby" -> Value.of(alwaysBaby || living != null && living.isBaby());
			case "is_alive" -> Value.of(entity.isAlive());
			case "is_on_ground" -> Value.of(entity.onGround());
			case "is_in_water" -> Value.of(entity.isInWater());
			case "is_riding" -> Value.of(entity.isPassenger());
			case "has_rider" -> Value.of(entity.isVehicle());
			case "is_sleeping" -> Value.of(living != null && living.isSleeping());
			case "is_on_fire" -> Value.of(entity.isOnFire());
			case "is_avoiding_mobs" -> Value.of(Boolean.TRUE.equals(entity.getExistingDataOrNull(ModAttachments.AVOIDING)));
			case "is_sheared" -> Value.of(entity instanceof Sheep sheep && sheep.isSheared());
			case "health" -> Value.of(living != null ? living.getHealth() : 0);
			case "life_time" -> Value.of((entity.tickCount + partialTick) / 20.0);
			case "frame_alpha" -> Value.of(partialTick);
			case "modified_move_speed" -> Value.of(living != null ? living.walkAnimation.speed(partialTick) : 0);
			case "modified_distance_moved" -> Value.of(living != null ? living.walkAnimation.position(partialTick) : 0);
			case "ground_speed" -> {
				Vec3 v = entity.getDeltaMovement();
				yield Value.of(Math.sqrt(v.x * v.x + v.z * v.z) * 20);
			}
			// Seconds the main hand's item has been in use (the microphone, held up to speak into), 0 if it isn't.
			case "main_hand_item_use_duration" -> Value.of(living != null && living.isUsingItem()
					&& living.getUsedItemHand() == InteractionHand.MAIN_HAND
					? (living.getTicksUsingItem()+partialTick) / 20.0 : 0);
			case "vertical_speed" -> Value.of(entity.getDeltaMovement().y * 20);
			case "target_x_rotation" -> Value.of(net.minecraft.util.Mth.lerp(partialTick,entity.xRotO,entity.getXRot()));
			case "target_y_rotation" -> Value.of(living != null
					? Mth.wrapDegrees(lerpDegrees(living.yHeadRotO, living.yHeadRot) - lerpDegrees(living.yBodyRotO, living.yBodyRot))
					: 0);
			case "body_y_rotation" -> Value.of(living != null
					? lerpDegrees(living.yBodyRotO, living.yBodyRot)
					: net.minecraft.util.Mth.rotLerp(partialTick,entity.yRotO,entity.getYRot()));
			// Bedrock picks one of six villager faces at spawn; Java has none, so derive a stable one per entity.
			case "skin_id" -> Value.of(Math.floorMod(entity.getUUID().hashCode(), 6));
			case "variant" -> Value.of(villager() == null ? 0 : indexOf(PROFESSIONS, net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager().getProfession()).getPath()));
			case "mark_variant" -> Value.of(villager() == null ? 0 : indexOf(BIOMES, net.minecraft.core.registries.BuiltInRegistries.VILLAGER_TYPE.getKey(villager().getType()).getPath()));
			case "trade_tier" -> Value.of(villager() == null ? 0 : villager().getLevel() - 1);
			case "property" -> function(args -> propertyValue(args.next().eval().getAsString()));
			case "has_property" -> function(args -> {
				String property = args.next().eval().getAsString();
				return Value.of(propertyOverrides.containsKey(property) || properties.containsKey(property));
			});
			case "is_name_any" -> function(args -> {
				String customName = entity.getCustomName() == null ? null : entity.getCustomName().getString();
				for (int i = 0; i < args.length(); i++) {
					if (args.next().eval().getAsString().equals(customName)) {
						return Value.of(true);
					}
				}
				return Value.of(false);
			});
			case "any" -> function(args -> {
				double subject = args.next().eval().getAsNumber();
				for (int i = 1; i < args.length(); i++) {
					if (args.next().eval().getAsNumber() == subject) {
						return Value.of(true);
					}
				}
				return Value.of(false);
			});
			case "position" -> function(args -> {
				int axis = (int) args.next().eval().getAsNumber();
				Vec3 pos = entity.getPosition(partialTick);
				return Value.of(axis == 0 ? pos.x : axis == 1 ? pos.y : pos.z);
			});
			// We only ever render the "fancy" path.
			case "graphics_mode_is_any" -> function(args -> Value.of(true));
			case "is_sneaking" -> Value.of(entity.isShiftKeyDown());
			case "is_gliding" -> Value.of(living != null && living.isFallFlying());
			// Bedrock counts a mob afloat in water as swimming (the add-on's bobbing at the surface).
			case "is_swimming" -> Value.of(entity.isSwimming() || entity instanceof Mob && entity.isInWater());
			// Degrees per second the body is turning (the add-on shuffles its feet turning on the spot).
			case "yaw_speed" -> Value.of(living != null ? Mth.wrapDegrees(living.yBodyRot - living.yBodyRotO) * 20 : 0);
			case "ride_body_y_rotation" -> {
				Entity vehicle = entity.getVehicle();
				yield Value.of(vehicle instanceof LivingEntity ridden ? lerpDegrees(ridden.yBodyRotO, ridden.yBodyRot)
						: vehicle != null ? net.minecraft.util.Mth.rotLerp(partialTick,vehicle.yRotO,vehicle.getYRot()) : 0);
			}
			case "position_delta" -> function(args -> {
				int axis = (int) args.next().eval().getAsNumber();
				return Value.of(axis == 0 ? entity.getX() - entity.xOld : axis == 1 ? entity.getY() - entity.yOld : entity.getZ() - entity.zOld);
			});
			case "main_hand_item_max_duration" -> Value.of(living != null && !living.getMainHandItem().isEmpty()
					? living.getMainHandItem().getUseDuration(living) / 20.0 : 0);
			case "is_owner_identifier_any" -> function(args -> {
				String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
				for (int i = 0; i < args.length(); i++) {
					if (args.next().eval().getAsString().equals(type)) {
						return Value.of(true);
					}
				}
				return Value.of(false);
			});
			case "relative_block_has_any_tag" -> function(args -> {
				Vec3 pos = entity.getPosition(partialTick);
				BlockPos block = BlockPos.containing(pos.x + args.next().eval().getAsNumber(), pos.y + args.next().eval().getAsNumber(),
						pos.z + args.next().eval().getAsNumber());
				for (int i = 3; i < args.length(); i++) {
					if (blockHasTag(block, args.next().eval().getAsString())) {
						return Value.of(true);
					}
				}
				return Value.of(false);
			});
			case "equipped_item_any_tag" -> function(args -> {
				ItemStack stack = living == null ? ItemStack.EMPTY : equipped(living, args.next().eval().getAsString());
				for (int i = 1; i < args.length(); i++) {
					if (itemHasTag(stack, args.next().eval().getAsString())) {
						return Value.of(true);
					}
				}
				return Value.of(false);
			});
			case "distance_from_camera" -> Value.of(camera().distanceTo(entity.getPosition(partialTick)));
			// The pitch (0) or yaw (1) that would face the camera from here, in the entity's own convention.
			case "rotation_to_camera" -> function(args -> {
				Vec3 to = camera().subtract(entity.getPosition(partialTick));
				double yaw = Math.toDegrees(Math.atan2(to.z, to.x)) - 90;
				double pitch = -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
				return Value.of(args.next().eval().getAsNumber() == 0 ? pitch : yaw);
			});
			default -> Value.nil();
		};
	}

	private Value propertyValue(String property) {
		Value override = propertyOverrides.get(property);
		if (override != null) {
			return override;
		}
		JsonElement value = properties.get(property);
		if (value == null || !value.isJsonPrimitive()) {
			return Value.nil();
		}
		if (value.getAsJsonPrimitive().isBoolean()) {
			return Value.of(value.getAsBoolean());
		}
		if (value.getAsJsonPrimitive().isNumber()) {
			return Value.of(value.getAsDouble());
		}
		return StringValue.of(value.getAsString());
	}

	private static Vec3 camera() {
		return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
	}

	/** Bedrock block tags, for the ones the add-on asks about (water, lava); others read as absent. */
	private boolean blockHasTag(BlockPos pos, String tag) {
		var fluid = entity.level().getFluidState(pos);
		return switch (tag.replace("minecraft:", "")) {
			case "water" -> fluid.is(FluidTags.WATER);
			case "lava" -> fluid.is(FluidTags.LAVA);
			default -> false;
		};
	}

	private static ItemStack equipped(LivingEntity living, String slot) {
		return switch (slot) {
			case "slot.weapon.mainhand" -> living.getMainHandItem();
			case "slot.weapon.offhand" -> living.getOffhandItem();
			case "slot.armor.head" -> living.getItemBySlot(EquipmentSlot.HEAD);
			case "slot.armor.chest" -> living.getItemBySlot(EquipmentSlot.CHEST);
			case "slot.armor.legs" -> living.getItemBySlot(EquipmentSlot.LEGS);
			case "slot.armor.feet" -> living.getItemBySlot(EquipmentSlot.FEET);
			default -> ItemStack.EMPTY;
		};
	}

	/** Bedrock's tool tier tags ({@code minecraft:diamond_tier}) by the item's material name, else the Java item tag of that id. */
	private static boolean itemHasTag(ItemStack stack, String tag) {
		if (stack.isEmpty()) {
			return false;
		}
		String name = tag.replace("minecraft:", "");
		if (name.endsWith("_tier")) {
			return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().startsWith(name.substring(0, name.length() - "tier".length()));
		}
		ResourceLocation id = ResourceLocation.tryParse(tag);
		return id != null && stack.is(TagKey.create(Registries.ITEM, id));
	}

	private VillagerData villager() {
		return entity instanceof Villager v ? v.getVillagerData() : null;
	}

	private float lerpDegrees(float previous, float current) {
		return previous + Mth.wrapDegrees(current - previous) * partialTick;
	}

	private static String keyPath(java.util.Optional<? extends net.minecraft.resources.ResourceKey<?>> key) {
		return key.map(k -> k.location().getPath()).orElse("");
	}

	private static int indexOf(List<String> list, String value) {
		return Math.max(0, list.indexOf(value));
	}

	private static Function<Object> function(java.util.function.Function<Function.Arguments, Value> body) {
		return (ctx, args) -> body.apply(args);
	}
}
