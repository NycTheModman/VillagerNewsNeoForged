package com.javafied.villagernews.converter;

import com.javafied.villagernews.names.AddonNames;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the add-on's handbook out of its behavior script: every page, its
 * text and buttons, the trigger index behind its search box, its settings and
 * the words on its controls. The script builds the handbook as a tree of
 * pages, each made by one of a few page builders (a menu, a list of entries,
 * a menu with a search box, one trigger's page, the settings); the mod's
 * {@code GuideScreen} is a hand port of those builders, and everything they
 * show comes from here.
 *
 * <p>Output, {@code assets/<modid>/guide.json} (text keeps the add-on's
 * Bedrock {@code §} formatting):
 * <pre>{@code
 * { "title": "<default title>", "start": "<page>", "titles": { "<page>": "<title>" },
 *   "ui": { "back", "search_label", "no_results", "clear_search", "max_results" },
 *   "pages": { "<page>": { "type": "menu" | "search" | "entries" | "trigger" | "settings",
 *                          "text": "..", "back": true,
 *                          "buttons": [ { "label", "page" } ],                  // menu, search
 *                          "search": [ { "label", "section", "page" } ],         // search
 *                          "entries": [ { "header", "body" } ],                  // entries
 *                          "controls": [ { "id", "type": "toggle" | "dropdown",  // settings
 *                                          "label", "description", "items": [ ".." ] } ] } } }
 * }</pre>
 *
 * <p>The script is minified with obfuscated property names; the builders'
 * argument names come from the add-on version's names file. The page builders
 * themselves are told apart by what they do, and the tables they read are
 * found through their code.
 */
public final class GuideConverter {
	/** The handbook: {@code title = map(page, e => titles[e] ?? default); return <ui>.<form>({closeButton: ...}}. */
	private static final Pattern FORM = Pattern.compile(
			"=map\\([\\w$]+,[\\w$]+=>([\\w$]+)\\[[\\w$]+\\]\\?\\?([\\w$]+)\\);return [\\w$]+\\.[\\w$]+\\((?=\\{closeButton:)");
	/** A page: {@code key: ({link: e}) => builder({...})} or {@code key: () => builder({...})}. */
	private static final Pattern PAGE = Pattern.compile("^\\((?:\\{[^}]*\\})?\\)=>([\\w$]+)\\(");
	/** Buttons made from a list: {@code Object.fromEntries(list.map(n => [label(n), {...}]))}. */
	private static final Pattern GENERATED_BUTTONS = Pattern.compile(
			"^Object\\.fromEntries\\((.+?)\\.map\\(\\(?([\\w$]+)\\)?=>\\[([\\w$]+)\\(\\2\\),");
	/** Titles taken from a table's entries: {@code Object.fromEntries(Object.entries(t).map(([e, n]) => [e, n.title]))}. */
	private static final Pattern TITLES_FROM_TABLE = Pattern.compile(
			"^Object\\.fromEntries\\(Object\\.entries\\(([\\w$]+)\\)\\.map\\(\\(\\[([\\w$]+),([\\w$]+)\\]\\)=>\\[\\2,\\3\\.(\\w+)\\]\\)\\)");

	// The page builders' (obfuscated) argument names, from the add-on version's names file.
	private final String startKey;
	private final String textKey;
	private final String descriptionKey;
	private final String pageKey;
	private final String searchKey;
	/** The settings page's controls, in the order the add-on lays them out. */
	private static final List<String> SETTINGS = List.of("subtitles", "chattiness", "rare_lines", "special_villagers", "style");

	private final String script;
	private final JsLiteral js;

	private GuideConverter(String script, AddonNames names) {
		this.script = script;
		this.js = new JsLiteral(script);
		this.startKey = key(names, "guide_start");
		this.textKey = key(names, "guide_text");
		this.descriptionKey = key(names, "guide_description");
		this.pageKey = key(names, "guide_key");
		this.searchKey = key(names, "guide_search");
	}

	private static String key(AddonNames names, String name) {
		String key = names.id(AddonNames.Kind.SCRIPT_KEY, name);
		return key != null ? key : name;
	}

	/** @return how many pages the handbook has (0 if the script has none the converter recognises) */
	public static int convert(Path behaviorPack, Path assetsDir, AddonNames names) throws IOException {
		Path scripts = behaviorPack == null ? null : behaviorPack.resolve("scripts");
		if (scripts == null || !Files.isDirectory(scripts)) {
			return 0;
		}
		try (var stream = Files.walk(scripts)) {
			for (Path file : stream.filter(p -> p.toString().endsWith(".js")).toList()) {
				JsonObject guide = extract(Files.readString(file), names);
				if (guide != null) {
					ConverterUtil.writeJson(assetsDir.resolve("guide.json"), guide);
					return guide.getAsJsonObject("pages").size();
				}
			}
		}
		return 0;
	}

	/** Null if the script has no handbook. */
	static JsonObject extract(String script, AddonNames names) {
		Matcher form = FORM.matcher(script);
		return form.find() ? new GuideConverter(script, names).guide(form) : null;
	}

	private JsonObject guide(Matcher form) {
		JsonObject formArgs = js.parseLenientAt(form.end()).getAsJsonObject();
		JsonObject out = new JsonObject();
		out.addProperty("title", string(js.constant(form.group(2)), ""));
		out.addProperty("start", string(formArgs.get(startKey), "home"));
		out.add("titles", titles(form.group(1)));

		JsonObject ui = new JsonObject();
		JsonObject pages = new JsonObject();
		for (Map.Entry<String, JsonElement> page : formArgs.getAsJsonObject("pages").entrySet()) {
			JsonObject converted = page(page.getValue().getAsJsonObject(), ui);
			if (converted != null) {
				pages.add(page.getKey(), converted);
			}
		}
		out.add("ui", ui);
		out.add("pages", pages);
		return out;
	}

	private JsonObject titles(String table) {
		JsonObject titles = new JsonObject();
		JsonElement parsed = constantLenient(table);
		if (parsed == null || !parsed.isJsonObject()) {
			return titles;
		}
		for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
			if (!entry.getKey().startsWith("$spread")) {
				if (entry.getValue().isJsonPrimitive()) {
					titles.add(entry.getKey(), entry.getValue());
				}
				continue;
			}
			Matcher m = TITLES_FROM_TABLE.matcher(raw(entry.getValue()));
			JsonElement source = m.lookingAt() ? js.constant(m.group(1)) : null;
			if (source != null && source.isJsonObject()) {
				for (Map.Entry<String, JsonElement> row : source.getAsJsonObject().entrySet()) {
					JsonElement title = row.getValue().isJsonObject() ? row.getValue().getAsJsonObject().get(m.group(4)) : null;
					if (title != null && title.isJsonPrimitive()) {
						titles.add(row.getKey(), title);
					}
				}
			}
		}
		return titles;
	}

	private JsonObject page(JsonObject raw, JsonObject ui) {
		if (!raw.has("$expr")) {
			return null;
		}
		Matcher m = PAGE.matcher(raw(raw));
		if (!m.lookingAt()) {
			return null;
		}
		String builder = definition(m.group(1));
		int builderAt = definitionStart(m.group(1));
		JsonElement parsedArgs = js.parseLenientAt(raw.get("$at").getAsInt() + m.end());
		if (builder == null || !parsedArgs.isJsonObject()) {
			return null;
		}
		JsonObject args = parsedArgs.getAsJsonObject();
		collectUi(builder, args, ui);

		JsonObject page = new JsonObject();
		if (builder.contains(".Toggle(") || builder.contains(".Dropdown(")) {
			page.addProperty("type", "settings");
			page.add("controls", controls(builder, builderAt));
			page.addProperty("back", true);
		} else if (builder.contains(".entries)")) {
			page.addProperty("type", "entries");
			page.addProperty("text", string(args.get(descriptionKey), ""));
			page.add("entries", entries(builder, args.get("entries")));
			page.addProperty("back", !args.has("backButton") || isTrue(args.get("backButton")));
		} else if (args.has(searchKey)) {
			page.addProperty("type", "search");
			page.addProperty("text", string(args.get(textKey), ""));
			page.add("buttons", buttons(args.get("buttons")));
			page.add("search", searchIndex(args.get(searchKey)));
			page.addProperty("back", true);
		} else if (args.has("buttons") || builder.contains(".buttons")) {
			page.addProperty("type", "menu");
			page.addProperty("text", string(args.get(textKey), ""));
			page.add("buttons", buttons(args.get("buttons")));
			page.addProperty("back", isTrue(args.get("backButton")));
		} else if (args.has(pageKey)) {
			page.addProperty("type", "trigger");
			page.addProperty("text", triggerText(builder, string(args.get(pageKey), "")));
			page.addProperty("back", true);
		} else {
			return null;
		}
		return page;
	}

	/** An entry list's entries: a header, and the body with its colour prefix (the builder's default if it has none). */
	private JsonArray entries(String builder, JsonElement list) {
		Matcher prefix = Pattern.compile("\\$\\{[\\w$]+\\.([\\w$]+)\\?\\?\"([^\"]*)\"\\}\\$\\{[\\w$]+\\.body\\}").matcher(builder);
		boolean hasPrefix = prefix.find();
		JsonArray out = new JsonArray();
		if (list == null || !list.isJsonArray()) {
			return out;
		}
		for (JsonElement element : list.getAsJsonArray()) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject entry = element.getAsJsonObject();
			String lead = hasPrefix ? string(entry.get(prefix.group(1)), prefix.group(2)) : "";
			JsonObject converted = new JsonObject();
			converted.addProperty("header", string(entry.get("header"), ""));
			converted.addProperty("body", lead + string(entry.get("body"), ""));
			out.add(converted);
		}
		return out;
	}

	/** Buttons, in order: literal ones ({@code label: {link, <key>: page}}) and ones made from a list. */
	private JsonArray buttons(JsonElement buttons) {
		JsonArray out = new JsonArray();
		if (buttons == null || !buttons.isJsonObject()) {
			return out;
		}
		JsonObject object = buttons.getAsJsonObject();
		if (object.has("$expr")) {
			generatedButtons(object, out);
			return out;
		}
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			if (entry.getKey().startsWith("$spread")) {
				generatedButtons(entry.getValue().getAsJsonObject(), out);
			} else if (entry.getValue().isJsonObject() && entry.getValue().getAsJsonObject().has(pageKey)) {
				out.add(button(entry.getKey(), string(entry.getValue().getAsJsonObject().get(pageKey), "")));
			}
		}
		return out;
	}

	private void generatedButtons(JsonObject raw, JsonArray out) {
		Matcher m = GENERATED_BUTTONS.matcher(raw(raw));
		if (!m.lookingAt()) {
			return;
		}
		JsonElement list = js.parseAt(raw.get("$at").getAsInt() + m.start(1));
		Function<String, String> label = labelFunction(m.group(3));
		if (list == null || !list.isJsonArray() || label == null) {
			return;
		}
		for (JsonElement id : list.getAsJsonArray()) {
			String page = string(id, "");
			String text = label.apply(page);
			if (text != null) {
				out.add(button(text, page));
			}
		}
	}

	private static JsonObject button(String label, String page) {
		JsonObject button = new JsonObject();
		button.addProperty("label", label);
		button.addProperty("page", page);
		return button;
	}

	/**
	 * The trigger index behind the search box - a port of how the script
	 * builds it: for each category, each of its sections' triggers (or, for a
	 * section that is a list of entries, each entry, leading to that section).
	 */
	private JsonArray searchIndex(JsonElement raw) {
		JsonArray out = new JsonArray();
		if (raw == null || !raw.isJsonObject() || !raw.getAsJsonObject().has("$expr")) {
			return out;
		}
		Matcher call = Pattern.compile("^([\\w$]+)\\(([\\w$]+),[\\w$]+\\)").matcher(raw(raw.getAsJsonObject()));
		String indexer = call.lookingAt() ? functionDefinition(call.group(1)) : null;
		JsonElement categories = call.lookingAt() ? js.constant(call.group(2)) : null;
		if (indexer == null || categories == null || !categories.isJsonArray()) {
			return out;
		}
		Matcher tables = Pattern.compile("of ([\\w$]+)\\[[\\w$]+\\]\\)\\{const [\\w$]+=([\\w$]+)\\[[\\w$]+\\];if\\(void 0===[\\w$]+\\)for\\(const [\\w$]+ of ([\\w$]+)\\[[\\w$]+\\]\\)")
				.matcher(indexer);
		Matcher labels = Pattern.compile("label:([\\w$]+)\\([\\w$]+\\),section:([\\w$]+)\\([\\w$]+\\)").matcher(indexer);
		if (!tables.find() || !labels.find()) {
			return out;
		}
		JsonObject sections = object(js.constant(tables.group(1)));
		JsonObject entryLists = object(js.constant(tables.group(2)));
		JsonObject triggers = object(js.constant(tables.group(3)));
		Function<String, String> triggerLabel = labelFunction(labels.group(1));
		Function<String, String> sectionLabel = labelFunction(labels.group(2));
		if (triggerLabel == null || sectionLabel == null) {
			return out;
		}
		Set<String> seen = new HashSet<>();
		for (JsonElement category : categories.getAsJsonArray()) {
			JsonElement inCategory = sections.get(string(category, ""));
			if (inCategory == null || !inCategory.isJsonArray()) {
				continue;
			}
			for (JsonElement sectionId : inCategory.getAsJsonArray()) {
				String section = string(sectionId, "");
				JsonElement entries = entryLists.get(section);
				if (entries != null && entries.isJsonArray()) {
					for (JsonElement entry : entries.getAsJsonArray()) {
						String header = string(entry.getAsJsonObject().get("header"), "");
						if (seen.add(section + ":" + header)) {
							out.add(searchEntry(header, sectionLabel.apply(section), section));
						}
					}
				} else if (triggers.get(section) != null && triggers.get(section).isJsonArray()) {
					for (JsonElement triggerId : triggers.get(section).getAsJsonArray()) {
						String trigger = string(triggerId, "");
						if (seen.add(trigger)) {
							out.add(searchEntry(triggerLabel.apply(trigger), sectionLabel.apply(section), trigger));
						}
					}
				}
			}
		}
		return out;
	}

	private static JsonObject searchEntry(String label, String section, String page) {
		JsonObject entry = new JsonObject();
		entry.addProperty("label", label == null ? "" : label);
		entry.addProperty("section", section == null ? "" : section);
		entry.addProperty("page", page);
		return entry;
	}

	/** A trigger's page shows its body: {@code Label({text: body(args.<key>)})}. */
	private String triggerText(String builder, String key) {
		Matcher m = Pattern.compile("text:([\\w$]+)\\([\\w$]+\\." + Pattern.quote(pageKey) + "\\)").matcher(builder);
		if (!m.find()) {
			return "";
		}
		String body = functionDefinition(m.group(1));
		Matcher lookup = body == null ? null
				: Pattern.compile("\\{return ([\\w$]+)\\[[\\w$]+\\]\\.(\\w+)\\}").matcher(body);
		if (lookup == null || !lookup.find()) {
			return "";
		}
		JsonElement row = object(js.constant(lookup.group(1))).get(key);
		return row != null && row.isJsonObject() ? string(row.getAsJsonObject().get(lookup.group(2)), "") : "";
	}

	/** The settings page's controls: {@code e.Toggle({label, description, ...})}, {@code e.Dropdown({..., items: [{value, label}]})}. */
	private JsonArray controls(String builder, int builderAt) {
		JsonArray out = new JsonArray();
		Matcher m = Pattern.compile("\\.(Toggle|Dropdown)\\((?=\\{)").matcher(builder);
		while (m.find() && out.size() < SETTINGS.size()) {
			JsonElement parsed = js.parseLenientAt(builderAt + m.end());
			if (!parsed.isJsonObject()) {
				continue;
			}
			JsonObject args = parsed.getAsJsonObject();
			JsonObject control = new JsonObject();
			control.addProperty("id", SETTINGS.get(out.size()));
			control.addProperty("type", m.group(1).equals("Toggle") ? "toggle" : "dropdown");
			control.addProperty("label", string(args.get("label"), ""));
			control.addProperty("description", string(args.get("description"), ""));
			JsonArray items = new JsonArray();
			if (args.get("items") != null && args.get("items").isJsonArray()) {
				for (JsonElement item : args.getAsJsonArray("items")) {
					items.add(string(item.getAsJsonObject().get("label"), ""));
				}
			}
			control.add("items", items);
			out.add(control);
		}
		return out;
	}

	/** The words on the handbook's shared controls: the back button and the search box. */
	private void collectUi(String builder, JsonObject args, JsonObject ui) {
		Matcher back = Pattern.compile("([\\w$]+)\\(\\{[\\w$]+:!0,").matcher(builder);
		if (!ui.has("back") && back.find()) {
			String definition = definition(back.group(1));
			Matcher text = definition == null ? null : Pattern.compile("text:[\\w$]+\\.[\\w$]+\\?\\?\"([^\"]*)\"").matcher(definition);
			if (text != null && text.find()) {
				ui.addProperty("back", text.group(1));
			}
		}
		Matcher search = Pattern.compile("([\\w$]+)\\(\\{" + Pattern.quote(searchKey) + ":").matcher(builder);
		if (!ui.has("search_label") && args.has(searchKey) && search.find()) {
			String definition = definition(search.group(1));
			if (definition == null) {
				return;
			}
			find(definition, "label:[\\w$]+\\.label\\?\\?\"([^\"]*)\"", ui, "search_label");
			find(definition, "=[\\w$]+\\.[\\w$]+\\?\\?\"([^\"]*)\",[\\w$]+=[\\w$]+=>", ui, "no_results");
			find(definition, "text:\"([^\"]*)\",[\\w$]+:[\\w$]+,key:\"[\\w$]+\",[\\w$]+\\(\\)", ui, "clear_search");
			Matcher max = Pattern.compile("const [\\w$]+=[\\w$]+\\.[\\w$]+\\?\\?([\\w$]+)").matcher(definition);
			if (max.find() && js.constant(max.group(1)) != null && js.constant(max.group(1)).isJsonPrimitive()) {
				ui.addProperty("max_results", js.constant(max.group(1)).getAsInt());
			}
		}
	}

	private static void find(String text, String regex, JsonObject out, String key) {
		Matcher m = Pattern.compile(regex).matcher(text);
		if (m.find()) {
			out.addProperty(key, m.group(1));
		}
	}

	/**
	 * A one-argument label function: {@code function f(e){return table[e]}} or
	 * {@code function f(e){const n=table[e];return n.short??n.title}}.
	 */
	private Function<String, String> labelFunction(String name) {
		String body = functionDefinition(name);
		if (body == null) {
			return null;
		}
		Matcher direct = Pattern.compile("^function [\\w$]+\\(([\\w$]+)\\)\\{return ([\\w$]+)\\[\\1\\]\\}").matcher(body);
		if (direct.lookingAt()) {
			JsonObject table = object(js.constant(direct.group(2)));
			return id -> table.has(id) ? string(table.get(id), null) : null;
		}
		Matcher fields = Pattern.compile("^function [\\w$]+\\(([\\w$]+)\\)\\{const ([\\w$]+)=([\\w$]+)\\[\\1\\];return \\2\\.(\\w+)\\?\\?\\2\\.(\\w+)\\}")
				.matcher(body);
		if (fields.lookingAt()) {
			JsonObject table = object(js.constant(fields.group(3)));
			return id -> {
				JsonElement row = table.get(id);
				if (row == null || !row.isJsonObject()) {
					return null;
				}
				String preferred = string(row.getAsJsonObject().get(fields.group(4)), null);
				return preferred != null ? preferred : string(row.getAsJsonObject().get(fields.group(5)), null);
			};
		}
		return null;
	}

	/** {@code function name(...) {...}}'s source. */
	private String functionDefinition(String name) {
		int at = script.indexOf("function " + name + "(");
		if (at < 0) {
			return null;
		}
		int open = script.indexOf('{', script.indexOf(')', at));
		return script.substring(at, js.closingBracket(open) + 1);
	}

	/** {@code name=<expression>}'s expression source. */
	private String definition(String name) {
		int at = definitionStart(name);
		return at < 0 ? null : script.substring(at, js.endOfExpression(at));
	}

	private int definitionStart(String name) {
		Matcher m = Pattern.compile("(?<![\\w$.])" + Pattern.quote(name) + "=(?!=)").matcher(script);
		return m.find() ? m.end() : -1;
	}

	/** The top-level constant, read leniently (it may hold code). */
	private JsonElement constantLenient(String name) {
		Matcher m = Pattern.compile("(?<![\\w$.])" + Pattern.quote(name) + "=(?=\\{)").matcher(script);
		return m.find() ? js.parseLenientAt(m.end()) : null;
	}

	private static String raw(JsonElement element) {
		return element.isJsonObject() && element.getAsJsonObject().has("$expr") ? element.getAsJsonObject().get("$expr").getAsString() : "";
	}

	private static JsonObject object(JsonElement element) {
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	private static boolean isTrue(JsonElement element) {
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean() && element.getAsBoolean();
	}

	private static String string(JsonElement element, String fallback) {
		return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
	}
}
