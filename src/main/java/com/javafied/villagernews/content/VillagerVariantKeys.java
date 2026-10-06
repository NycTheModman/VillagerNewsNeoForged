package com.javafied.villagernews.content;

import java.util.List;

/**
 * The add-on's villager-family characters that share vanilla's real Villager
 * entity (confirmed by identical component-group fingerprints in the
 * add-on's behavior pack; its wandering trader character is a villager
 * reskin too). Stored on the entity by these readable names (see
 * {@code AddonNames}), so saved worlds don't depend on the add-on's ids.
 */
public final class VillagerVariantKeys {
	public static final String DEFAULT = "villager";

	public static final List<String> ALL = List.of("villager", "untouchable", "wandering_trader", "villager_5", "testificate_man",
			"villager_9", "mayor");

	private VillagerVariantKeys() {
	}

	public static boolean isValid(String key) {
		return ALL.contains(key);
	}
}
