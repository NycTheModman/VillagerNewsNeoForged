package com.javafied.villagernews.behavior;

import com.javafied.villagernews.content.ModAttachments;
import com.javafied.villagernews.names.AddonNames;

import com.google.gson.JsonElement;

import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * An entity's Bedrock properties ({@code p:...}) as its behavior definition
 * declares them: stored (persistently) only when changed from the default,
 * and computed live for the ones the script derives from the host villager.
 *
 * <p>Saved under readable names where the names file has them - the
 * property's ({@code accessory}, not {@code p:mlxeez}) and, for values that
 * are add-on items, the item's ({@code mayor_hat}) - so a world keeps them
 * when the add-on's ids change. {@link #get}/{@link #set} speak the add-on's
 * ids; {@link #named}/{@link #setNamed} take our names.
 */
public final class BehaviorProperties {
	private final Entity entity;
	private final BehaviorDefinitions.Definition definition;

	public BehaviorProperties(Entity entity, BehaviorDefinitions.Definition definition) {
		this.entity = entity;
		this.definition = definition;
	}

	/** Boolean, Double or String; null if the entity doesn't declare it. */
	public Object get(String name) {
		if (PuppetHost.packedStateProperty().equals(name)) {
			return (double) PuppetHost.packedState(entity);
		}
		if (PuppetHost.tradeTierProperty().equals(name)) {
			return (double) PuppetHost.tradeTier(entity);
		}
		if (PuppetHost.vehicleProperty().equals(name)) {
			return (double) PuppetHost.vehicleIndex(entity);
		}
		BehaviorDefinitions.Property property = definition.properties().get(name);
		if (property == null) {
			return null;
		}
		String stored = read(entity, name);
		return stored != null ? typed(property.type(), stored)
				: property.defaultValue() == null ? null : typed(property.type(), property.defaultValue().getAsString());
	}

	public void set(String name, JsonElement value) {
		BehaviorDefinitions.Property property = definition.properties().get(name);
		if (property == null || !value.isJsonPrimitive()) {
			return;
		}
		Map<String, String> updated = new HashMap<>(entity.getExistingData(ModAttachments.BEHAVIOR_PROPERTIES).orElse(Map.of()));
		updated.remove(name); // saved by its add-on id before it had a name
		updated.put(storedKey(name), storedValue(value.getAsString()));
		entity.setData(ModAttachments.BEHAVIOR_PROPERTIES, Map.copyOf(updated));
	}

	/** By our name for the property ({@code "nose"}). */
	public Object named(String property) {
		return get(AddonNames.property(property));
	}

	public void setNamed(String property, JsonElement value) {
		set(AddonNames.property(property), value);
	}

	/** A property's saved value, in the add-on's terms; null if it's at its default. */
	public static String read(Entity entity, String addonProperty) {
		Map<String, String> saved = entity.getExistingData(ModAttachments.BEHAVIOR_PROPERTIES).orElse(Map.of());
		String stored = saved.get(storedKey(addonProperty));
		if (stored == null) {
			stored = saved.get(addonProperty);
		}
		return stored == null ? null : addonValue(stored);
	}

	/** The saved properties, in the add-on's terms (as its client-side scripts read them). */
	public static Map<String, String> readAll(Entity entity) {
		Map<String, String> out = new HashMap<>();
		entity.getExistingData(ModAttachments.BEHAVIOR_PROPERTIES).orElse(Map.<String, String>of())
				.forEach((key, value) -> out.put(AddonNames.property(key), addonValue(value)));
		return out;
	}

	private static String storedKey(String addonProperty) {
		return AddonNames.nameOf(AddonNames.Kind.PROPERTY, addonProperty);
	}

	private static String storedValue(String addonValue) {
		return AddonNames.nameOf(AddonNames.Kind.ITEM, addonValue);
	}

	private static String addonValue(String stored) {
		String item = AddonNames.current().id(AddonNames.Kind.ITEM, stored);
		return item != null ? item : stored;
	}

	private static Object typed(String type, String raw) {
		return switch (type) {
			case "bool" -> Boolean.parseBoolean(raw);
			case "int", "float" -> {
				try {
					yield Double.parseDouble(raw);
				} catch (NumberFormatException e) {
					yield 0.0;
				}
			}
			default -> raw;
		};
	}
}
