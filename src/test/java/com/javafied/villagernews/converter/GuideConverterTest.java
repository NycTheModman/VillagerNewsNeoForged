package com.javafied.villagernews.converter;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Against the real add-on's script (dev/addon-src, gitignored). */
class GuideConverterTest {
	private static JsonObject guide;

	@BeforeAll
	static void load() throws IOException {
		Path src = Path.of("dev/addon-src");
		assumeTrue(Files.isDirectory(src), "no local add-on copy");
		try (Stream<Path> files = Files.walk(src)) {
			Path script = files.filter(p -> p.toString().endsWith(".js") && p.toString().contains("scripts")).findFirst().orElse(null);
			assumeTrue(script != null, "no behavior script");
			guide = GuideConverter.extract(Files.readString(script), com.javafied.villagernews.names.AddonNames.forVersion("1.0.4"));
		}
		assertNotNull(guide, "handbook not found");
		Files.writeString(Path.of("build/guide-test.json"), new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(guide));
	}

	@Test
	void everyButtonLeadsToAPage() {
		JsonObject pages = guide.getAsJsonObject("pages");
		assertTrue(pages.has(guide.get("start").getAsString()), "start page");
		assertTrue(pages.size() > 300, "pages: " + pages.size());
		for (Map.Entry<String, JsonElement> page : pages.entrySet()) {
			JsonObject p = page.getValue().getAsJsonObject();
			for (String list : new String[] {"buttons", "search"}) {
				if (p.has(list)) {
					for (JsonElement button : p.getAsJsonArray(list)) {
						String target = button.getAsJsonObject().get("page").getAsString();
						assertTrue(pages.has(target), page.getKey() + " -> missing page " + target);
						assertFalse(button.getAsJsonObject().get("label").getAsString().isEmpty(), page.getKey() + " unlabelled button");
					}
				}
			}
		}
	}

	@Test
	void readsEveryKindOfPage() {
		JsonObject pages = guide.getAsJsonObject("pages");
		JsonObject home = pages.getAsJsonObject(guide.get("start").getAsString());
		assertEquals("menu", home.get("type").getAsString());
		assertEquals(4, home.getAsJsonArray("buttons").size());
		assertFalse(home.get("back").getAsBoolean());
		long types = pages.entrySet().stream().map(e -> e.getValue().getAsJsonObject().get("type").getAsString()).distinct().count();
		assertEquals(5, types, "menu, search, entries, trigger, settings");
		JsonObject search = pages.entrySet().stream().map(e -> e.getValue().getAsJsonObject())
				.filter(p -> p.get("type").getAsString().equals("search")).findFirst().orElseThrow();
		assertTrue(search.getAsJsonArray("search").size() > 300, "trigger index");
		assertTrue(search.get("text").getAsString().contains("orevillestudios.com"), "template literal substituted");
		JsonObject settings = pages.getAsJsonObject("settings");
		assertEquals(5, settings.getAsJsonArray("controls").size());
		assertEquals(4, settings.getAsJsonArray("controls").get(1).getAsJsonObject().getAsJsonArray("items").size());
		JsonObject ui = guide.getAsJsonObject("ui");
		for (String key : new String[] {"back", "search_label", "no_results", "clear_search", "max_results"}) {
			assertTrue(ui.has(key), "ui " + key);
		}
		assertFalse(guide.get("title").getAsString().isEmpty());
		assertTrue(guide.getAsJsonObject("titles").size() > 300, "titles");
	}
}
