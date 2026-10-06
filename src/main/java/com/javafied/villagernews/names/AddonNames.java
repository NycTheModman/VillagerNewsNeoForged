package com.javafied.villagernews.names;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Readable names for the add-on's (obfuscated) identifiers, so the mod's code
 * says {@code "mayor"} or {@code property("nose")} rather than
 * {@code "ilvfra"} or {@code "p:gcfsvg"}.
 *
 * <p>Each add-on version has a names file in the mod
 * ({@code villagernewsjavafied/names/villager_news-<version>.json}): our
 * names on one side, that version's ids on the other - identifiers only, no
 * add-on content. Dialogs are mostly named during conversion instead, from
 * the add-on's own guide titles (see {@code DialogNames}); the file lists the
 * rest. An add-on version without a names file uses the newest one, and says
 * so in the log.
 */
public final class AddonNames {
	private static final String DIR = "/villagernewsjavafied/names/";

	/** A kind of identifier, and its section in the names file. */
	public enum Kind {
		CHARACTER("characters"), ENTITY("entities"), ITEM("items"), PROPERTY("properties"), DIALOG("dialogs"), DIALOG_GROUP("dialog_groups"),
		DIALOG_TAG("dialog_tags"), SCRIPT_EVENT("script_events"), SCRIPT_KEY("script_keys"), GUIDE_PAGE("guide_pages"),
		VARIABLE("variables"), BONE("bones");

		private final String section;

		Kind(String section) {
			this.section = section;
		}

		public String section() {
			return section;
		}
	}

	private static volatile AddonNames current;
	/** Where the running game learns which add-on version was converted (null: unknown, as in the converter). */
	private static volatile java.util.function.Supplier<String> versionSource = () -> null;

	private final String version;
	private final boolean known;
	private final Map<Kind, Map<String, String>> ids = new EnumMap<>(Kind.class);
	private final Map<Kind, Map<String, String>> names = new EnumMap<>(Kind.class);
	private final List<DialogFill> dialogFills = new ArrayList<>();

	/**
	 * A dialog the add-on asks for but never defines (its sensors request it),
	 * put together from lines it does have: {@code lines} are those lines'
	 * sound ids, {@code like} the dialog whose cooldowns it takes.
	 */
	public record DialogFill(String name, String id, String like, List<String> lines) {
	}

	private AddonNames(String version, boolean known, JsonObject json) {
		this.version = version;
		this.known = known;
		JsonObject fills = json.getAsJsonObject("dialog_fills");
		if (fills != null) {
			for (Map.Entry<String, JsonElement> entry : fills.entrySet()) {
				JsonObject fill = entry.getValue().getAsJsonObject();
				List<String> lines = new ArrayList<>();
				fill.getAsJsonArray("lines").forEach(line -> lines.add(line.getAsString()));
				dialogFills.add(new DialogFill(entry.getKey(), fill.get("id").getAsString(),
						fill.has("like") ? fill.get("like").getAsString() : null, List.copyOf(lines)));
			}
		}
		for (Kind kind : Kind.values()) {
			Map<String, String> byName = new HashMap<>();
			Map<String, String> byId = new HashMap<>();
			JsonObject section = json.getAsJsonObject(kind.section());
			if (section != null) {
				for (Map.Entry<String, JsonElement> entry : section.entrySet()) {
					byName.put(entry.getKey(), entry.getValue().getAsString());
					byId.put(entry.getValue().getAsString(), entry.getKey());
				}
			}
			ids.put(kind, Map.copyOf(byName));
			names.put(kind, Map.copyOf(byId));
		}
	}

	/** The names for this add-on version - or, if the mod has none for it, the newest ones ({@link #known()} false). */
	public static AddonNames forVersion(String version) {
		List<String> versions = versions();
		boolean known = version != null && versions.contains(version);
		String chosen = known ? version : versions.getLast();
		return new AddonNames(chosen, known, read("villager_news-" + chosen + ".json"));
	}

	/** The names for the add-on the player converted. */
	public static AddonNames current() {
		AddonNames names = current;
		if (names == null) {
			names = forVersion(versionSource.get());
			current = names;
		}
		return names;
	}

	/** Called by the game with where to find the converted add-on's version; also drops the cached names (after a conversion). */
	public static void setVersionSource(java.util.function.Supplier<String> source) {
		versionSource = source;
		current = null;
	}

	public static void reload() {
		current = null;
	}

	/** The add-on version these names are for. */
	public String version() {
		return version;
	}

	/** Whether the mod has names for exactly the converted add-on's version. */
	public boolean known() {
		return known;
	}

	/** The dialogs to put together for gaps in the add-on (see {@link DialogFill}). */
	public List<DialogFill> dialogFills() {
		return List.copyOf(dialogFills);
	}

	/** The add-on's id for a readable name; null if these names don't have it. */
	public String id(Kind kind, String name) {
		return ids.get(kind).get(name);
	}

	/** The readable name for an add-on id; null if it has none. */
	public String name(Kind kind, String id) {
		return names.get(kind).get(id);
	}

	/** Readable name -> id, for one kind. */
	public Map<String, String> all(Kind kind) {
		return ids.get(kind);
	}

	// The current add-on's ids, by readable name (the name itself if it has no mapping, so a miss stays visible).

	public static String character(String name) {
		return orSelf(current().id(Kind.CHARACTER, name), name);
	}

	public static String item(String name) {
		return orSelf(current().id(Kind.ITEM, name), name);
	}

	public static String property(String name) {
		return orSelf(current().id(Kind.PROPERTY, name), name);
	}

	/** The readable name of an add-on id of this kind, or the id itself if it has none. */
	public static String nameOf(Kind kind, String id) {
		return orSelf(current().name(kind, id), id);
	}

	private static String orSelf(String value, String fallback) {
		return value != null ? value : fallback;
	}

	private static List<String> versions() {
		List<String> versions = new ArrayList<>();
		read("index.json").getAsJsonArray("versions").forEach(v -> versions.add(v.getAsString()));
		return versions;
	}

	private static JsonObject read(String file) {
		try (InputStream in = AddonNames.class.getResourceAsStream(DIR + file)) {
			if (in == null) {
				throw new IllegalStateException("Missing names file " + DIR + file);
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new IllegalStateException("Unreadable names file " + DIR + file, e);
		}
	}
}
