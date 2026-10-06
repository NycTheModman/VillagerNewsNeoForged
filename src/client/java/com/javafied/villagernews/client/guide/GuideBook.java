package com.javafied.villagernews.client.guide;

import com.javafied.villagernews.VillagerNewsJavafied;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** The handbook as the converter read it out of the add-on (see {@code GuideConverter}). */
public record GuideBook(String title, String start, Map<String, String> titles, Ui ui, Map<String, Page> pages) {
	private static final ResourceLocation FILE = VillagerNewsJavafied.id("guide.json");

	public record Ui(String back, String searchLabel, String noResults, String clearSearch, int maxResults) {
	}

	public record Button(String label, String page) {
	}

	public record SearchEntry(String label, String section, String page) {
	}

	public record Entry(String header, String body) {
	}

	public record Control(String id, String type, String label, String description, List<String> items) {
	}

	public record Page(String type, String text, boolean back, List<Button> buttons, List<SearchEntry> search, List<Entry> entries,
			List<Control> controls) {
	}

	/**
	 * The add-on's search: triggers whose name contains the query (a word
	 * starting with it first), then those whose section does; at most
	 * {@code max}, in index order within each rank.
	 */
	public static List<SearchEntry> search(List<SearchEntry> index, String query, int max) {
		String q = query.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			return List.of();
		}
		record Ranked(SearchEntry entry, int rank, int order) {
		}
		List<Ranked> ranked = new ArrayList<>();
		for (int i = 0; i < index.size(); i++) {
			SearchEntry entry = index.get(i);
			String label = entry.label().toLowerCase(Locale.ROOT);
			String section = entry.section().toLowerCase(Locale.ROOT);
			int rank = label.contains(q) ? (wordStartsWith(label, q) ? 1 : 2) : section.contains(q) ? (wordStartsWith(section, q) ? 3 : 4) : 0;
			if (rank > 0) {
				ranked.add(new Ranked(entry, rank, i));
			}
		}
		return ranked.stream().sorted(Comparator.comparingInt(Ranked::rank).thenComparingInt(Ranked::order)).limit(max)
				.map(Ranked::entry).toList();
	}

	private static boolean wordStartsWith(String text, String prefix) {
		for (String word : text.split("\\s+")) {
			if (word.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	public String titleOf(String page) {
		return titles.getOrDefault(page, title);
	}

	/** Empty if the add-on hasn't been converted (or has no handbook). */
	public static Optional<GuideBook> load() {
		try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(FILE)) {
			return Optional.of(parse(JsonParser.parseReader(reader).getAsJsonObject()));
		} catch (IOException | RuntimeException e) {
			VillagerNewsJavafied.LOGGER.warn("No handbook in the converted add-on: {}", e.toString());
			return Optional.empty();
		}
	}

	static GuideBook parse(JsonObject json) {
		Map<String, String> titles = new HashMap<>();
		json.getAsJsonObject("titles").entrySet().forEach(e -> titles.put(e.getKey(), e.getValue().getAsString()));
		JsonObject ui = json.getAsJsonObject("ui");
		Map<String, Page> pages = new HashMap<>();
		for (Map.Entry<String, JsonElement> page : json.getAsJsonObject("pages").entrySet()) {
			JsonObject p = page.getValue().getAsJsonObject();
			List<Button> buttons = new ArrayList<>();
			list(p, "buttons").forEach(b -> buttons.add(new Button(str(b, "label"), str(b, "page"))));
			List<SearchEntry> search = new ArrayList<>();
			list(p, "search").forEach(s -> search.add(new SearchEntry(str(s, "label"), str(s, "section"), str(s, "page"))));
			List<Entry> entries = new ArrayList<>();
			list(p, "entries").forEach(e -> entries.add(new Entry(str(e, "header"), str(e, "body"))));
			List<Control> controls = new ArrayList<>();
			list(p, "controls").forEach(c -> {
				List<String> items = new ArrayList<>();
				c.getAsJsonArray("items").forEach(i -> items.add(i.getAsString()));
				controls.add(new Control(str(c, "id"), str(c, "type"), str(c, "label"), str(c, "description"), List.copyOf(items)));
			});
			pages.put(page.getKey(), new Page(str(p, "type"), str(p, "text"), p.has("back") && p.get("back").getAsBoolean(),
					List.copyOf(buttons), List.copyOf(search), List.copyOf(entries), List.copyOf(controls)));
		}
		return new GuideBook(str(json, "title"), str(json, "start"), Map.copyOf(titles),
				new Ui(str(ui, "back"), str(ui, "search_label"), str(ui, "no_results"), str(ui, "clear_search"),
						ui.has("max_results") ? ui.get("max_results").getAsInt() : 8),
				Map.copyOf(pages));
	}

	private static List<JsonObject> list(JsonObject json, String key) {
		List<JsonObject> out = new ArrayList<>();
		if (json.has(key)) {
			json.getAsJsonArray(key).forEach(e -> out.add(e.getAsJsonObject()));
		}
		return out;
	}

	private static String str(JsonObject json, String key) {
		return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : "";
	}
}
