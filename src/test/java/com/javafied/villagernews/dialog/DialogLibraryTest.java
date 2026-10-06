package com.javafied.villagernews.dialog;

import com.javafied.villagernews.names.AddonNames;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Against the dialogs the converter extracted from the real add-on (dev/converted, gitignored). */
class DialogLibraryTest {
	private static DialogLibrary library;

	@BeforeAll
	static void load() throws IOException {
		Path file = Path.of("dev/converted/server/dialogs.json");
		assumeTrue(Files.exists(file), "no converted add-on");
		library = DialogLibrary.load(file);
	}

	@Test
	void extractsEveryDialog() {
		// The add-on's 523, and the 12 it asks for but never defines, put together from its own lines.
		assertEquals(523 + 12, library.size());
		assertFalse(library.hurtSounds(false).isEmpty());
		assertFalse(library.hurtSounds(true).isEmpty());
		for (List<String> conversation : library.conversations()) {
			for (String part : conversation) {
				assertNotNull(library.get(part), "conversation part " + part);
			}
		}
	}

	/** The sensors ask by the add-on's id, so each fill must be found under it. */
	@Test
	void fillsTheDialogsTheAddOnAsksForButLacks() {
		for (AddonNames.DialogFill fill : AddonNames.forVersion("1.0.4").dialogFills()) {
			assertNotNull(library.byAddonId(fill.id()), fill.name());
			assertFalse(library.get(fill.name()).lines().isEmpty(), fill.name());
		}
	}

	@Test
	void evaluatesComputedCooldowns() {
		// Script: csiavd:{didrid:2*(jtzadj.csiavd.didrid??0)} with jtzadj.csiavd.didrid = 80.
		assertEquals(160, library.get("open_a_chest").entityCooldown().same());
		assertEquals(10, library.get("open_a_chest").entityCooldown().any(), "unset half keeps its default");
		DialogLibrary.Line first = library.get("give_a_villager_a_nose").lines().getFirst();
		assertEquals(2, first.subtitles().size());
		assertEquals(0.87, first.subtitles().get(1).time(), 1e-9);
	}

	/** Every dialog the hand-ported triggers name must exist in the converted add-on. */
	@Test
	void portedTriggersReferenceRealDialogs() throws ReflectiveOperationException {
		List<String> ids = new ArrayList<>();
		for (Class<?> reactions : List.of(VillagerReactions.class, VillagerItemReactions.class, TradeReactions.class,
				PlayerActionReactions.class, VillagerLifeReactions.class, BlockUseReactions.class,
				VillagerRoutineReactions.class, WorldReactions.class, NoticeReactions.class,
				HurtReactions.class, WorkReactions.class, UntouchableReactions.class)) {
			for (Field field : reactions.getDeclaredFields()) {
				if (!Modifier.isStatic(field.getModifiers()) || field.getName().startsWith("TAG_") || field.getName().startsWith("ITEM_") || field.getName().startsWith("PROPERTY_")
						|| field.getName().startsWith("GROUP_") || field.getName().startsWith("BLOCK_") || field.getName().equals("TRADE_DIALOGS")) {
					continue;
				}
				field.setAccessible(true);
				Object value = field.get(null);
				if (value instanceof String id && !id.contains(":") && !id.equals("none")) {
					ids.add(id);
				} else if (value instanceof java.util.Map<?, ?> map) {
					for (Object v : map.values()) {
						if (v instanceof String id) {
							ids.add(id);
						} else if (v instanceof Record record) {
							addRecordStrings(record, ids);
						} else if (v instanceof List<?> parts) {
							parts.stream().filter(String.class::isInstance).map(String.class::cast).forEach(ids::add);
						}
					}
				} else if (value instanceof List<?> list) {
					for (Object element : list) {
						if (element instanceof Record record) {
							addRecordStrings(record, ids);
						}
					}
				}
			}
		}
		assertTrue(ids.size() >= 30, "found " + ids.size());
		for (LocalDate date = LocalDate.of(2026, 1, 1); date.getYear() == 2026; date = date.plusDays(1)) {
			ids.addAll(VillagerReactions.calendarDialogs(date));
		}
		for (String id : ids) {
			assertNotNull(library.get(id), "unknown dialog " + id);
		}
		assertTrue(VillagerReactions.calendarDialogs(LocalDate.of(2026, 12, 31))
				.containsAll(List.of("wander_on_new_years_eve", "wander_in_december")));
		assertTrue(library.conversations().stream().anyMatch(c -> "both_noses".equals(library.get(c.getFirst()).group())));
	}

	/** Dialog ids held in records (a category's dialog, a remark's adult/second lines). */
	private static void addRecordStrings(Record record, List<String> ids) throws ReflectiveOperationException {
		for (var component : record.getClass().getRecordComponents()) {
			if (component.getType() == String.class) {
				component.getAccessor().setAccessible(true);
				String value = (String) component.getAccessor().invoke(record);
				if (value != null) {
					ids.add(value);
				}
			}
		}
	}

	@Test
	void greetsByTheNearestReputationBand() {
		assertEquals("start_trading_with_neutral_reputation", TradeReactions.greeting(0));
		assertEquals("start_trading_with_neutral_reputation", TradeReactions.greeting(20));
		assertEquals("start_trading_with_high_reputation", TradeReactions.greeting(60));
		assertEquals("start_trading_with_extremely_high_reputation", TradeReactions.greeting(400));
		assertEquals("start_trading_with_extremely_low_reputation", TradeReactions.greeting(-500));
	}
}
