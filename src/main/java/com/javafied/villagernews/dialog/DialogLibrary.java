package com.javafied.villagernews.dialog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The add-on's voice lines, as the converter extracted them from its script
 * ({@code server/dialogs.json}). A {@link Dialog} is one reaction - "greet a
 * player", "complain about being pushed" - with several interchangeable
 * {@link Line}s, one of which is spoken each time. Dialogs go by readable
 * names ({@code start_work}), each keeping the add-on's own id.
 */
public final class DialogLibrary {
	public static final DialogLibrary EMPTY = new DialogLibrary(Map.of(), List.of(), List.of(), List.of());

	/** Seconds; {@code any} blocks every dialog, {@code same} only this one. */
	public record Cooldown(double any, double same) {
	}

	/** Seconds a dialog tag blocks other dialogs with the same tag: everywhere, and on the same speaker. */
	public record TagCooldown(double global, double entity) {
	}

	public record Subtitle(double time, String key) {
	}

	/**
	 * @param animation Bedrock animation played on the speaker (lip sync + gesture cue)
	 * @param sound     Bedrock sound event, e.g. {@code oreville_vn:qosovr}
	 * @param duration  seconds
	 */
	public record Line(String animation, String sound, double duration, double weight, List<Subtitle> subtitles) {
		public int durationTicks() {
			return (int) Math.floor(20 * duration);
		}
	}

	/**
	 * @param id      readable name ({@code start_work})
	 * @param addonId the add-on's id for it ({@code qawras})
	 * @param group   for the villagers' conversations about noses, which ones ({@code both_noses}); else null
	 */
	public record Dialog(String id, String addonId, String group, List<Line> lines, Cooldown globalCooldown,
			Cooldown entityCooldown, Map<String, TagCooldown> tags) {
	}

	/**
	 * The script's defaults. A dialog's world-wide "any" cooldown, when it sets
	 * none, depends on the chattiness setting ({@code NaN} here; see
	 * {@code GuideSettings.Values#defaultGlobalAny}).
	 */
	private static final Cooldown DEFAULT_GLOBAL = new Cooldown(Double.NaN, 10);
	private static final Cooldown DEFAULT_ENTITY = new Cooldown(10, 40);
	private static final TagCooldown DEFAULT_TAG = new TagCooldown(30, 15);

	private final Map<String, Dialog> dialogs;
	private final List<String> adultHurtSounds;
	private final List<String> babyHurtSounds;
	private final List<List<String>> conversations;

	private DialogLibrary(Map<String, Dialog> dialogs, List<String> adultHurtSounds, List<String> babyHurtSounds,
			List<List<String>> conversations) {
		this.dialogs = dialogs;
		this.adultHurtSounds = adultHurtSounds;
		this.babyHurtSounds = babyHurtSounds;
		this.conversations = conversations;
	}

	private volatile Map<String, Dialog> byAddonId;

	/** {@link #EMPTY} if the file doesn't exist (add-on not converted yet). */
	public static DialogLibrary load(Path file) throws IOException {
		if (!Files.exists(file)) {
			return EMPTY;
		}
		return parse(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
	}

	public static DialogLibrary parse(JsonObject root) {
		Map<String, Dialog> dialogs = new LinkedHashMap<>();
		JsonObject all = root.getAsJsonObject("dialogs");
		if (all != null) {
			for (Map.Entry<String, JsonElement> entry : all.entrySet()) {
				dialogs.put(entry.getKey(), dialog(entry.getKey(), entry.getValue().getAsJsonObject()));
			}
		}
		JsonObject hurt = root.getAsJsonObject("hurt_sounds");
		List<List<String>> conversations = new ArrayList<>();
		JsonArray conversationsJson = root.getAsJsonArray("conversations");
		if (conversationsJson != null) {
			for (JsonElement conversation : conversationsJson) {
				List<String> parts = new ArrayList<>();
				conversation.getAsJsonArray().forEach(part -> parts.add(part.getAsString()));
				conversations.add(List.copyOf(parts));
			}
		}
		return new DialogLibrary(Map.copyOf(dialogs), strings(hurt, "adult"), strings(hurt, "baby"), List.copyOf(conversations));
	}

	/** By readable name. */
	public Dialog get(String name) {
		return dialogs.get(name);
	}

	/** The dialog with this add-on id (as the behavior pack's script events name them), or null. */
	public Dialog byAddonId(String addonId) {
		if (byAddonId == null) {
			Map<String, Dialog> index = new java.util.HashMap<>();
			dialogs.values().forEach(d -> index.put(d.addonId(), d));
			byAddonId = index;
		}
		return byAddonId.get(addonId);
	}

	/** Every dialog, by readable name. */
	public java.util.Collection<Dialog> all() {
		return dialogs.values();
	}

	public boolean isEmpty() {
		return dialogs.isEmpty();
	}

	public int size() {
		return dialogs.size();
	}

	public List<String> hurtSounds(boolean baby) {
		return baby ? babyHurtSounds : adultHurtSounds;
	}

	/** Two-villager exchanges: the dialogs' ids in speaking order, the two villagers taking turns. */
	public List<List<String>> conversations() {
		return conversations;
	}

	private static Dialog dialog(String id, JsonObject json) {
		List<Line> lines = new ArrayList<>();
		for (JsonElement element : json.getAsJsonArray("lines")) {
			JsonObject line = element.getAsJsonObject();
			List<Subtitle> subtitles = new ArrayList<>();
			for (JsonElement s : line.getAsJsonArray("subtitles")) {
				subtitles.add(new Subtitle(s.getAsJsonObject().get("time").getAsDouble(),
						s.getAsJsonObject().get("text").getAsString()));
			}
			lines.add(new Line(line.get("animation").getAsString(), line.get("sound").getAsString(),
					line.get("duration").getAsDouble(), line.get("weight").getAsDouble(), List.copyOf(subtitles)));
		}
		Map<String, TagCooldown> tags = new LinkedHashMap<>();
		JsonObject tagsJson = json.getAsJsonObject("tags");
		if (tagsJson != null) {
			for (Map.Entry<String, JsonElement> tag : tagsJson.entrySet()) {
				JsonObject t = tag.getValue().getAsJsonObject();
				tags.put(tag.getKey(), new TagCooldown(number(t, "global", DEFAULT_TAG.global()),
						number(t, "entity", DEFAULT_TAG.entity())));
			}
		}
		String addonId = json.has("id") ? json.get("id").getAsString() : id;
		String group = json.has("group") ? json.get("group").getAsString() : null;
		return new Dialog(id, addonId, group, List.copyOf(lines), cooldown(json.getAsJsonObject("global_cooldown"), DEFAULT_GLOBAL),
				cooldown(json.getAsJsonObject("entity_cooldown"), DEFAULT_ENTITY), Map.copyOf(tags));
	}

	private static Cooldown cooldown(JsonObject json, Cooldown defaults) {
		return json == null ? defaults : new Cooldown(number(json, "any", defaults.any()), number(json, "same", defaults.same()));
	}

	private static double number(JsonObject json, String key, double fallback) {
		JsonElement value = json.get(key);
		return value == null || value.isJsonNull() ? fallback : value.getAsDouble();
	}

	private static List<String> strings(JsonObject json, String key) {
		JsonArray array = json == null ? null : json.getAsJsonArray(key);
		if (array == null) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		array.forEach(e -> out.add(e.getAsString()));
		return List.copyOf(out);
	}
}
