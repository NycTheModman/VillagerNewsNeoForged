package com.javafied.villagernews.converter;

import com.javafied.villagernews.names.AddonNames;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls the data tables out of the add-on's behavior script, so the mod's
 * hand-ported logic can use them without shipping any of them.
 *
 * <p>The script is minified and its property names are obfuscated, so the
 * keys below are the ones add-on 1.0 uses. Output ({@code server/dialogs.json},
 * outside {@code assets/} since only the server reads it):
 * <pre>{@code
 * { "dialogs": { "<id>": { "lines": [ { "animation", "sound", "duration", "weight",
 *                                        "subtitles": [ { "time", "text" } ] } ],
 *                           "global_cooldown": { "any", "same" },   // optional, seconds
 *                           "entity_cooldown": { "any", "same" },   // optional, seconds
 *                           "tags": { "<tag>": { "global", "entity" } } } },
 *   "hurt_sounds": { "adult": [ "<sound>" ], "baby": [ "<sound>" ] },
 *   "conversations": [ [ "<dialog id>", ... ] ] }   // parts, spoken alternately by two villagers
 * }</pre>
 *
 * <p>Dialogs are keyed by readable name ({@link DialogNames}), each keeping
 * its add-on id ({@code "id"}) and, for the villager conversations about
 * noses, its group ({@code "group"}); tags and conversation parts use names
 * too. The script's own property names come from the add-on version's names
 * file ({@link AddonNames.Kind#SCRIPT_KEY}).
 */
public final class ScriptDataConverter {
	/** {@code name=Dialog.<register>({...})}: every dialog the script defines. */
	private static final Pattern DIALOG = Pattern.compile("([A-Za-z_$][\\w$]*)=Dialog\\.[A-Za-z_$][\\w$]*\\((?=\\{id:)");
	/** The hurt-reaction table: {@code {<state>:[{soundId:..,<animation>:..}, ..], ..}}. */
	private static final Pattern HURT_SOUNDS = Pattern.compile("=(?=\\{[A-Za-z_$][\\w$]*:\\[\\{soundId:\"[^\"]*\",[A-Za-z_$][\\w$]*:\"[^\"]*\"\\})");

	/** The two-villager conversation table: {@code [{1:<dialog>,2:<dialog>,..},..]}. */
	private static final Pattern CONVERSATIONS = Pattern.compile("=(?=\\[\\{1:[A-Za-z_$][\\w$]*,2:[A-Za-z_$][\\w$]*)");

	private final AddonNames names;
	private final String linesKey;
	private final String subtitlesKey;
	private final String subtitleText;
	private final String globalCooldown;
	private final String entityCooldown;
	private final String cooldownAny;
	private final String cooldownSame;
	private final String tagGlobal;
	private final String tagEntity;
	/** The script's names for "adult" and "baby" entity states. */
	private final Map<String, String> states;

	private ScriptDataConverter(AddonNames names) {
		this.names = names;
		this.linesKey = key("dialog_lines");
		this.subtitlesKey = key("line_subtitles");
		this.subtitleText = key("subtitle_text");
		this.globalCooldown = key("dialog_global_cooldown");
		this.entityCooldown = key("dialog_entity_cooldown");
		this.cooldownAny = key("cooldown_any");
		this.cooldownSame = key("cooldown_same");
		this.tagGlobal = key("tag_global");
		this.tagEntity = key("tag_entity");
		this.states = Map.of(key("state_adult"), "adult", key("state_baby"), "baby");
	}

	private String key(String name) {
		String key = names.id(AddonNames.Kind.SCRIPT_KEY, name);
		return key != null ? key : name;
	}

	/** @return how many dialogs were extracted */
	public static int convert(Path behaviorPack, Path outputDir, AddonNames names) throws IOException {
		Path scripts = behaviorPack == null ? null : behaviorPack.resolve("scripts");
		JsonObject root = null;
		if (scripts != null && Files.isDirectory(scripts)) {
			try (var stream = Files.walk(scripts)) {
				for (Path file : stream.filter(p -> p.toString().endsWith(".js")).toList()) {
					JsonObject found = new ScriptDataConverter(names).extract(Files.readString(file));
					if (root == null || found.getAsJsonObject("dialogs").size() > root.getAsJsonObject("dialogs").size()) {
						root = found;
					}
				}
			}
		}
		if (root == null) {
			root = new JsonObject();
			root.add("dialogs", new JsonObject());
			root.add("hurt_sounds", new JsonObject());
			root.add("conversations", new JsonArray());
		}
		ConverterUtil.writeJson(outputDir.resolve("server").resolve("dialogs.json"), root);
		return root.getAsJsonObject("dialogs").size();
	}

	/** Everything the script holds, with dialogs under readable names. */
	JsonObject extract(String script) {
		JsonObject byId = new JsonObject();
		JsonObject hurtSounds = new JsonObject();
		JsonArray conversationsById = new JsonArray();
		extract(script, byId, hurtSounds, conversationsById);

		List<List<String>> chains = new java.util.ArrayList<>();
		conversationsById.forEach(c -> {
			List<String> chain = new java.util.ArrayList<>();
			c.getAsJsonArray().forEach(part -> chain.add(part.getAsString()));
			chains.add(chain);
		});
		List<String> unnamed = new java.util.ArrayList<>();
		Map<String, String> dialogNames = DialogNames.assign(script, byId.keySet(), chains, names, unnamed);
		if (!unnamed.isEmpty()) {
			System.out.println("Dialogs without a readable name (add them to the names file): " + unnamed);
		}

		JsonObject dialogs = new JsonObject();
		for (Map.Entry<String, JsonElement> dialog : byId.entrySet()) {
			JsonObject named = new JsonObject();
			named.addProperty("id", dialog.getKey());
			for (Map.Entry<String, String> group : names.all(AddonNames.Kind.DIALOG_GROUP).entrySet()) {
				if (dialog.getKey().startsWith(group.getValue())) {
					named.addProperty("group", group.getKey());
				}
			}
			dialog.getValue().getAsJsonObject().entrySet().forEach(e -> named.add(e.getKey(), e.getValue()));
			if (named.has("tags")) {
				JsonObject tags = new JsonObject();
				named.getAsJsonObject("tags").entrySet().forEach(t -> {
					String tag = names.name(AddonNames.Kind.DIALOG_TAG, t.getKey());
					tags.add(tag != null ? tag : t.getKey(), t.getValue());
				});
				named.add("tags", tags);
			}
			dialogs.add(dialogNames.get(dialog.getKey()), named);
		}
		fillGaps(dialogs);
		JsonArray conversations = new JsonArray();
		for (List<String> chain : chains) {
			JsonArray named = new JsonArray();
			chain.forEach(id -> named.add(dialogNames.getOrDefault(id, id)));
			conversations.add(named);
		}
		JsonObject root = new JsonObject();
		root.add("dialogs", dialogs);
		root.add("hurt_sounds", hurtSounds);
		root.add("conversations", conversations);
		return root;
	}

	/**
	 * The dialogs the add-on's sensors ask for but it never defines (what a
	 * player holds - an emerald, seeds, a potion - and the wandering trader
	 * selling out), put together from its own lines that fit: see the names
	 * file's {@code dialog_fills}. A fill whose lines aren't all there (the
	 * add-on changed) is left out, and so is one the add-on now defines.
	 */
	private void fillGaps(JsonObject dialogs) {
		Map<String, JsonObject> linesBySound = new HashMap<>();
		Set<String> ids = new HashSet<>();
		for (Map.Entry<String, JsonElement> dialog : dialogs.entrySet()) {
			ids.add(dialog.getValue().getAsJsonObject().get("id").getAsString());
			for (JsonElement line : dialog.getValue().getAsJsonObject().getAsJsonArray("lines")) {
				linesBySound.putIfAbsent(line.getAsJsonObject().get("sound").getAsString(), line.getAsJsonObject());
			}
		}
		List<String> skipped = new ArrayList<>();
		for (AddonNames.DialogFill fill : names.dialogFills()) {
			if (ids.contains(fill.id()) || dialogs.has(fill.name())) {
				continue;
			}
			JsonArray lines = new JsonArray();
			fill.lines().stream().map(linesBySound::get).filter(Objects::nonNull).forEach(line -> lines.add(line.deepCopy()));
			if (lines.size() != fill.lines().size()) {
				skipped.add(fill.name());
				continue;
			}
			JsonObject filled = new JsonObject();
			filled.addProperty("id", fill.id());
			filled.addProperty("filled", true);
			JsonObject like = fill.like() == null ? null : dialogs.getAsJsonObject(fill.like());
			if (like != null) {
				for (String key : new String[] {"tags", "global_cooldown", "entity_cooldown"}) {
					if (like.has(key)) {
						filled.add(key, like.get(key).deepCopy());
					}
				}
			}
			filled.add("lines", lines);
			dialogs.add(fill.name(), filled);
		}
		if (!skipped.isEmpty()) {
			System.out.println("Dialog fills left out (their lines aren't all in this add-on): " + skipped);
		}
	}

	void extract(String script, JsonObject dialogsOut, JsonObject hurtSoundsOut, JsonArray conversationsOut) {
		JsLiteral js = new JsLiteral(script);
		// A dialog referenced from inside a literal (e.g. a table of dialogs) resolves to its id.
		Map<String, String> dialogVariables = new HashMap<>();
		Matcher names = DIALOG.matcher(script);
		while (names.find()) {
			Matcher id = Pattern.compile("\\{id:\"([^\"]+)\"").matcher(script).region(names.end(), script.length());
			if (id.lookingAt()) {
				dialogVariables.put(names.group(1), id.group(1));
			}
		}
		js.setResolver(name -> dialogVariables.containsKey(name) ? new JsonPrimitive(dialogVariables.get(name)) : null);

		Matcher m = DIALOG.matcher(script);
		while (m.find()) {
			JsonObject raw = js.parseAt(m.end()).getAsJsonObject();
			dialogsOut.add(raw.get("id").getAsString(), dialog(raw));
		}

		Matcher hurt = HURT_SOUNDS.matcher(script);
		if (hurt.find()) {
			JsonObject table = js.parseAt(hurt.end()).getAsJsonObject();
			for (Map.Entry<String, JsonElement> state : table.entrySet()) {
				JsonArray sounds = new JsonArray();
				for (JsonElement entry : state.getValue().getAsJsonArray()) {
					sounds.add(entry.getAsJsonObject().get("soundId"));
				}
				hurtSoundsOut.add(states.getOrDefault(state.getKey(), state.getKey()), sounds);
			}
		}

		Matcher conversations = CONVERSATIONS.matcher(script);
		while (conversations.find()) {
			JsonElement table = js.parseAt(conversations.end());
			for (JsonElement conversation : table.getAsJsonArray()) {
				JsonObject parts = conversation.getAsJsonObject();
				JsonArray ordered = new JsonArray();
				for (int part = 1; parts.has(Integer.toString(part)); part++) {
					ordered.add(parts.get(Integer.toString(part)));
				}
				conversationsOut.add(ordered);
			}
		}
	}

	private JsonObject dialog(JsonObject raw) {
		JsonObject out = new JsonObject();
		JsonArray lines = new JsonArray();
		for (JsonElement element : raw.getAsJsonArray(linesKey)) {
			JsonObject line = element.getAsJsonObject();
			JsonObject outLine = new JsonObject();
			outLine.add("animation", line.get("animationName"));
			String sound = line.get("soundId").getAsString();
			// Add-on 1.0.4 retained a stale pre-obfuscation ID for nitwit wandering line 17 (beo.ogg).
			if (names.version().equals("1.0.4") && sound.equals("orevillele_vn:uookqp.17")) {
				sound = "oreville_vn:lekufv";
			}
			outLine.addProperty("sound", sound);
			outLine.add("duration", line.get("duration"));
			outLine.addProperty("weight", line.has("weight") ? line.get("weight").getAsDouble() : 1);
			JsonArray subtitles = new JsonArray();
			if (line.has(subtitlesKey)) {
				List<Map.Entry<String, JsonElement>> timed = line.getAsJsonObject(subtitlesKey).entrySet().stream()
						.sorted((a, b) -> Double.compare(Double.parseDouble(a.getKey()), Double.parseDouble(b.getKey())))
						.toList();
				for (Map.Entry<String, JsonElement> entry : timed) {
					JsonObject subtitle = new JsonObject();
					subtitle.addProperty("time", Double.parseDouble(entry.getKey()));
					subtitle.add("text", entry.getValue().getAsJsonObject().get(subtitleText));
					subtitles.add(subtitle);
				}
			}
			outLine.add("subtitles", subtitles);
			lines.add(outLine);
		}
		out.add("lines", lines);
		cooldown(raw, globalCooldown, "global_cooldown", out);
		cooldown(raw, entityCooldown, "entity_cooldown", out);
		if (raw.has("tags")) {
			JsonObject tags = new JsonObject();
			for (Map.Entry<String, JsonElement> tag : raw.getAsJsonObject("tags").entrySet()) {
				JsonObject in = tag.getValue().getAsJsonObject();
				JsonObject t = new JsonObject();
				if (in.has(tagGlobal)) {
					t.add("global", in.get(tagGlobal));
				}
				if (in.has(tagEntity)) {
					t.add("entity", in.get(tagEntity));
				}
				tags.add(tag.getKey(), t);
			}
			out.add("tags", tags);
		}
		return out;
	}

	private void cooldown(JsonObject raw, String key, String outKey, JsonObject out) {
		if (!raw.has(key)) {
			return;
		}
		JsonObject in = raw.getAsJsonObject(key);
		JsonObject c = new JsonObject();
		if (in.has(cooldownAny)) {
			c.add("any", in.get(cooldownAny));
		}
		if (in.has(cooldownSame)) {
			c.add("same", in.get(cooldownSame));
		}
		out.add(outKey, c);
	}
}
