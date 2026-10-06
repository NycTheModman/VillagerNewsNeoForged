package com.javafied.villagernews.behavior;

import com.javafied.villagernews.dialog.DialogLibrary;
import com.javafied.villagernews.names.AddonNames;

import com.google.gson.JsonPrimitive;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BehaviorDefinitionsTest {
	@Test
	void readsTheVillagersSensors() throws IOException {
		Path dir = Path.of("dev/converted/server/entities");
		assumeTrue(Files.isDirectory(dir), "no converted add-on");
		BehaviorDefinitions definitions = BehaviorDefinitions.load(dir);
		BehaviorDefinitions.Definition villager = definitions.get("villager");
		assertNotNull(villager);
		assertTrue(villager.subsensors().size() >= 40, "subsensors: " + villager.subsensors().size());
		assertFalse(villager.environmentTriggers().isEmpty());
		BehaviorDefinitions.Subsensor crowd = villager.subsensors().stream()
				.filter(s -> s.event().equals("icyznn")).findFirst().orElseThrow();
		assertEquals(8, crowd.horizontal());
		assertEquals(6, crowd.vertical());
		assertEquals(3, crowd.minimumCount());
		assertEquals(-1, crowd.maximumCount());

		// Most dialogs the sensors request exist; add-on 1.0 also requests 14 held-item reactions it never defines.
		DialogLibrary library = DialogLibrary.load(Path.of("dev/converted/server/dialogs.json"));
		Matcher m = Pattern.compile("gjlxaa (\\w+)").matcher(villager.events().toString());
		List<String> missing = new ArrayList<>();
		int requested = 0;
		while (m.find()) {
			requested++;
			if (library.byAddonId(m.group(1)) == null) {
				missing.add(m.group(1));
			}
		}
		assertTrue(requested >= 40);
		assertTrue(requested - missing.size() >= 25, "missing dialogs: " + missing);
	}

	@Test
	void theMayorIsABabyForGood() throws IOException {
		Path dir = Path.of("dev/converted/server/entities");
		assumeTrue(Files.isDirectory(dir), "no converted add-on");
		BehaviorDefinitions definitions = BehaviorDefinitions.load(dir);
		assertTrue(definitions.get(AddonNames.character("mayor")).alwaysBaby());
		assertFalse(definitions.get("villager").alwaysBaby());
	}

	@Test
	void comparesLikeBedrock() {
		assertTrue(BedrockFilters.compare(true, "equals", new JsonPrimitive(true)));
		assertTrue(BedrockFilters.compare(false, "!=", new JsonPrimitive(true)));
		assertTrue(BedrockFilters.compare(3.0, "<", new JsonPrimitive(5)));
		assertFalse(BedrockFilters.compare(30.0, "<", new JsonPrimitive(5)));
		assertTrue(BedrockFilters.compare("Hard", "equals", new JsonPrimitive("hard")));
		assertTrue(BedrockFilters.compare(1.0, "!=", new JsonPrimitive(0)));
	}
}
