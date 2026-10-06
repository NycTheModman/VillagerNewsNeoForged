package com.javafied.villagernews.names;

import com.javafied.villagernews.dialog.DialogLibrary;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AddonNamesTest {
	private static final Path CONVERTED = Path.of("dev/converted");

	@Test
	void unknownVersionsUseTheNewestNames() {
		AddonNames known = AddonNames.forVersion("1.0.4");
		assertTrue(known.known());
		assertEquals("ilvfra", known.id(AddonNames.Kind.CHARACTER, "mayor"));
		assertEquals("microphone", known.name(AddonNames.Kind.ITEM, "dsojot"));
		AddonNames future = AddonNames.forVersion("9.9.9");
		assertFalse(future.known());
		assertEquals(known.version(), future.version());
	}

	@Test
	void namesAreMinecraftStyle() {
		AddonNames names = AddonNames.forVersion("1.0.4");
		for (AddonNames.Kind kind : AddonNames.Kind.values()) {
			for (String name : names.all(kind).keySet()) {
				assertTrue(name.matches("[a-z0-9_]+"), kind + " name " + name);
			}
		}
	}

	/** Every dialog gets a readable name, and every one the names file lists is in the converted add-on. */
	@Test
	void everyDialogIsNamed() throws IOException {
		Path file = CONVERTED.resolve("server/dialogs.json");
		assumeTrue(Files.exists(file), "no converted add-on");
		DialogLibrary library = DialogLibrary.load(file);
		List<String> unnamed = new ArrayList<>();
		for (DialogLibrary.Dialog dialog : library.all()) {
			assertTrue(dialog.id().matches("[a-z0-9_]+"), dialog.id());
			if (dialog.id().equals(dialog.addonId())) {
				unnamed.add(dialog.id());
			}
		}
		assertEquals(List.of(), unnamed, "dialogs without a readable name");
		AddonNames names = AddonNames.forVersion("1.0.4");
		for (var listed : names.all(AddonNames.Kind.DIALOG).entrySet()) {
			assertEquals(listed.getValue(), library.get(listed.getKey()) == null ? null : library.get(listed.getKey()).addonId(),
					"names file entry " + listed.getKey());
		}
		assertEquals("qawras", library.get("start_work").addonId(), "named from its guide title");
	}

	/** The characters, items and properties the names file lists exist in the converted behavior pack. */
	@Test
	void everyNamedIdExists() throws IOException {
		Path entities = CONVERTED.resolve("server/entities");
		assumeTrue(Files.isDirectory(entities), "no converted add-on");
		StringBuilder all = new StringBuilder();
		try (Stream<Path> files = Files.list(entities)) {
			for (Path f : files.toList()) {
				all.append(Files.readString(f));
			}
		}
		String behaviors = all.toString();
		AddonNames names = AddonNames.forVersion("1.0.4");
		for (var property : names.all(AddonNames.Kind.PROPERTY).entrySet()) {
			assertTrue(behaviors.contains("\"" + property.getValue() + "\""), "property " + property.getKey());
		}
		for (var entity : names.all(AddonNames.Kind.ENTITY).entrySet()) {
			assertTrue(behaviors.contains("oreville_vn:" + entity.getValue() + "\""), "entity " + entity.getKey());
		}
		for (var character : names.all(AddonNames.Kind.CHARACTER).entrySet()) {
			assertTrue(behaviors.contains("oreville_vn:" + character.getValue() + "\""), "character " + character.getKey());
		}
		JsonObject lang = JsonParser.parseString(Files.readString(CONVERTED.resolve("assets/villagernewsjavafied/lang/en_us.json")))
				.getAsJsonObject();
		for (String item : names.all(AddonNames.Kind.ITEM).keySet()) {
			assertTrue(lang.has("item.villagernewsjavafied." + item), "item " + item);
		}
	}
}
