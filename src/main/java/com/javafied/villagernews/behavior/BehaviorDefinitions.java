package com.javafied.villagernews.behavior;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The add-on's behavior-pack entity definitions ({@code server/entities/}),
 * reduced to what the server interprets: property declarations, the
 * {@code entity_sensor} / {@code environment_sensor} components, and events.
 */
public final class BehaviorDefinitions {
	public static final BehaviorDefinitions EMPTY = new BehaviorDefinitions(Map.of());

	/** @param type bool, int, float or enum */
	public record Property(String type, JsonElement defaultValue) {
	}

	/** @param horizontal/vertical range in blocks; cooldown in seconds (negative: none) */
	public record Subsensor(double horizontal, double vertical, int minimumCount, int maximumCount, double cooldown,
			JsonElement filters, String event) {
	}

	public record Trigger(JsonElement filters, String event) {
	}

	/** @param components the entity's base components, as-is, for the few read directly (e.g. its trade table) */
	public record Definition(String identifier, Map<String, Property> properties, List<Subsensor> subsensors,
			List<Trigger> environmentTriggers, JsonObject events, JsonObject components) {
		/** A baby for good ({@code minecraft:is_baby} among its base components): the Mayor. */
		public boolean alwaysBaby() {
			return components.has("minecraft:is_baby");
		}
	}

	private final Map<String, Definition> byIdentifier;

	private BehaviorDefinitions(Map<String, Definition> byIdentifier) {
		this.byIdentifier = byIdentifier;
	}

	public static BehaviorDefinitions load(Path dir) throws IOException {
		if (!Files.isDirectory(dir)) {
			return EMPTY;
		}
		List<JsonObject> files = new ArrayList<>();
		try (var stream = Files.walk(dir)) {
			for (Path file : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
				files.add(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
			}
		}
		return parse(files);
	}

	public static BehaviorDefinitions parse(List<JsonObject> files) {
		Map<String, Definition> out = new HashMap<>();
		for (JsonObject file : files) {
			JsonObject entity = file.getAsJsonObject("minecraft:entity");
			if (entity == null) {
				continue;
			}
			Definition definition = definition(entity);
			out.put(definition.identifier().toLowerCase(Locale.ROOT), definition);
		}
		return new BehaviorDefinitions(Map.copyOf(out));
	}

	/** Accepts a full identifier or just its path ("villager"). */
	public Definition get(String identifier) {
		String key = identifier.toLowerCase(Locale.ROOT);
		Definition exact = byIdentifier.get(key);
		if (exact != null || key.contains(":")) {
			return exact;
		}
		for (Map.Entry<String, Definition> entry : byIdentifier.entrySet()) {
			if (entry.getKey().endsWith(":" + key)) {
				return entry.getValue();
			}
		}
		return null;
	}

	public int size() {
		return byIdentifier.size();
	}

	private static Definition definition(JsonObject entity) {
		JsonObject description = entity.getAsJsonObject("description");
		Map<String, Property> properties = new HashMap<>();
		JsonObject props = description.getAsJsonObject("properties");
		if (props != null) {
			for (Map.Entry<String, JsonElement> p : props.entrySet()) {
				JsonObject prop = p.getValue().getAsJsonObject();
				properties.put(p.getKey(), new Property(prop.has("type") ? prop.get("type").getAsString() : "int", prop.get("default")));
			}
		}
		JsonObject components = entity.has("components") ? entity.getAsJsonObject("components") : new JsonObject();
		List<Subsensor> subsensors = new ArrayList<>();
		JsonObject sensor = components.getAsJsonObject("minecraft:entity_sensor");
		if (sensor != null) {
			JsonArray list = sensor.has("subsensors") ? sensor.getAsJsonArray("subsensors") : wrap(sensor);
			for (JsonElement element : list) {
				subsensors.add(subsensor(element.getAsJsonObject()));
			}
		}
		List<Trigger> triggers = new ArrayList<>();
		JsonObject environment = components.getAsJsonObject("minecraft:environment_sensor");
		if (environment != null && environment.has("triggers")) {
			JsonElement t = environment.get("triggers");
			for (JsonElement element : t.isJsonArray() ? t.getAsJsonArray() : wrap(t.getAsJsonObject())) {
				JsonObject trigger = element.getAsJsonObject();
				if (trigger.has("event")) {
					triggers.add(new Trigger(trigger.get("filters"), trigger.get("event").getAsString()));
				}
			}
		}
		JsonObject events = entity.has("events") ? entity.getAsJsonObject("events") : new JsonObject();
		return new Definition(description.get("identifier").getAsString(), Map.copyOf(properties), List.copyOf(subsensors),
				List.copyOf(triggers), events, components);
	}

	private static Subsensor subsensor(JsonObject s) {
		double horizontal = 10;
		double vertical = 10;
		if (s.has("range")) {
			JsonElement range = s.get("range");
			if (range.isJsonArray()) {
				horizontal = range.getAsJsonArray().get(0).getAsDouble();
				vertical = range.getAsJsonArray().size() > 1 ? range.getAsJsonArray().get(1).getAsDouble() : horizontal;
			} else {
				horizontal = vertical = range.getAsDouble();
			}
		}
		return new Subsensor(horizontal, vertical,
				s.has("minimum_count") ? s.get("minimum_count").getAsInt() : 1,
				s.has("maximum_count") ? s.get("maximum_count").getAsInt() : -1,
				s.has("cooldown") ? s.get("cooldown").getAsDouble() : -1,
				s.get("event_filters"), s.get("event").getAsString());
	}

	private static JsonArray wrap(JsonObject o) {
		JsonArray a = new JsonArray();
		a.add(o);
		return a;
	}
}
