package com.javafied.villagernews.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Converts Bedrock {@code sounds/sound_definitions.json} + its {@code .ogg}
 * files into a Java {@code sounds.json} plus copied (already Vorbis, no
 * transcoding needed) ogg files.
 */
public final class SoundConverter {
	private static final String SOUNDS_PREFIX = "sounds/";
	/** Bedrock entity -> Java sound event prefix, for vanilla mobs whose sounds the add-on silences. */
	private static final Map<String, String> VANILLA_ENTITIES = Map.of(
			"villager", "entity.villager.",
			"villager_v2", "entity.villager.",
			"wandering_trader", "entity.wandering_trader.");
	/** Bedrock's entity sound events -> Java's (trading is "haggle" in Bedrock); the rest have no Java counterpart. */
	private static final Map<String, String> EVENTS = Map.of(
			"ambient", "ambient",
			"hurt", "hurt",
			"death", "death",
			"haggle", "trade",
			"haggle.yes", "yes",
			"haggle.no", "no");

	/** The quietest a sound can be in Java's sounds.json (it must be above 0): -80 dB. */
	private static final double INAUDIBLE = 0.0001;

	private SoundConverter() {
	}

	public static int convert(Path resourcePack, Path outputAssetsDir) throws IOException {
		Path definitionsFile = resourcePack.resolve("sounds").resolve("sound_definitions.json");
		if (!Files.exists(definitionsFile)) {
			return 0;
		}
		Path soundsSrcDir = resourcePack.resolve("sounds");
		Path soundsOutDir = outputAssetsDir.resolve("sounds");

		JsonObject root = ConverterUtil.readJson(definitionsFile);
		JsonObject definitions = root.has("sound_definitions") ? root.getAsJsonObject("sound_definitions") : root;

		JsonObject outSounds = new JsonObject();
		int copied = 0;
		String anySound = null;

		for (String eventName : definitions.keySet()) {
			JsonObject definition = definitions.getAsJsonObject(eventName);
			JsonObject outDefinition = new JsonObject();
			if (definition.has("category")) {
				outDefinition.add("category", definition.get("category"));
			}

			JsonArray outSoundList = new JsonArray();
			JsonArray sounds = definition.has("sounds") ? definition.getAsJsonArray("sounds") : new JsonArray();
			for (JsonElement soundEntry : sounds) {
				String relativePath = soundEntry.isJsonObject()
						? soundEntry.getAsJsonObject().get("name").getAsString()
						: soundEntry.getAsString();
				if (!relativePath.startsWith(SOUNDS_PREFIX)) {
					continue;
				}
				String withoutPrefix = relativePath.substring(SOUNDS_PREFIX.length());
				Path srcOgg = soundsSrcDir.resolve(withoutPrefix + ".ogg");
				if (!Files.exists(srcOgg)) {
					continue;
				}
				Path dstOgg = soundsOutDir.resolve(withoutPrefix + ".ogg");
				Files.createDirectories(dstOgg.getParent());
				Files.copy(srcOgg, dstOgg, StandardCopyOption.REPLACE_EXISTING);
				copied++;
				// Unqualified names in sounds.json resolve to minecraft:, not to this pack's namespace.
				String sound = ConverterUtil.MOD_ID + ":" + withoutPrefix;
				outSoundList.add(sound);
				if (anySound == null || sound.compareTo(anySound) < 0) {
					anySound = sound;
				}
			}

			outDefinition.add("sounds", outSoundList);
			String javaEventName = eventName.replace(':', '.');
			outSounds.add(javaEventName, outDefinition);
		}

		ConverterUtil.writeJson(outputAssetsDir.resolve("sounds.json"), outSounds);
		if (anySound != null) {
			writeSilencedVanillaSounds(resourcePack, outputAssetsDir.resolveSibling("minecraft"), anySound);
		}
		return copied;
	}

	/**
	 * The add-on turns vanilla villager and wandering trader voices (idle,
	 * hurt, death, trading) down to volume 0 in its {@code sounds.json}, as
	 * its own voice lines take over. Mirror that: the events play a sound too
	 * quiet to hear and have no subtitle. (Java won't take a volume of 0 - it
	 * rejects the whole file - and an event with no sounds logs a warning
	 * each time it's played; any sound of the pack will do.)
	 */
	private static void writeSilencedVanillaSounds(Path resourcePack, Path minecraftAssetsDir, String anySound) throws IOException {
		Path file = resourcePack.resolve("sounds.json");
		if (!Files.exists(file)) {
			return;
		}
		JsonObject entities = ConverterUtil.readJson(file).getAsJsonObject("entity_sounds");
		entities = entities == null ? null : entities.getAsJsonObject("entities");
		if (entities == null) {
			return;
		}
		JsonObject overrides = new JsonObject();
		for (Map.Entry<String, String> entity : VANILLA_ENTITIES.entrySet()) {
			JsonObject definition = entities.getAsJsonObject(entity.getKey());
			JsonObject events = definition == null ? null : definition.getAsJsonObject("events");
			if (events == null) {
				continue;
			}
			for (String event : events.keySet()) {
				JsonElement volume = events.getAsJsonObject(event).get("volume");
				String javaEvent = EVENTS.get(event);
				if (javaEvent != null && volume != null && volume.getAsDouble() == 0) {
					overrides.add(entity.getValue() + javaEvent, silent(anySound));
				}
			}
		}
		if (!overrides.isEmpty()) {
			ConverterUtil.writeJson(minecraftAssetsDir.resolve("sounds.json"), overrides);
		}
	}

	private static JsonObject silent(String anySound) {
		JsonObject muted = new JsonObject();
		muted.addProperty("name", anySound);
		muted.addProperty("volume", INAUDIBLE);
		JsonArray sounds = new JsonArray();
		sounds.add(muted);
		JsonObject silent = new JsonObject();
		silent.addProperty("replace", true);
		silent.add("sounds", sounds);
		return silent;
	}
}
