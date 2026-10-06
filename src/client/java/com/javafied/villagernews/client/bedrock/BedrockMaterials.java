package com.javafied.villagernews.client.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bedrock materials from the add-on's {@code .material} files. Each is
 * {@code "name:parent"} plus added states/defines, bottoming out in a vanilla
 * base material (entity, entity_alphatest, entity_alphablend, sheep, ...).
 * Java has no material system, so the chain is only resolved far enough to
 * pick the closest render type.
 */
public final class BedrockMaterials {
	public enum Kind {
		/** Alpha-tested, the default; also how multi-texture "masked" materials are layered. */
		CUTOUT,
		TRANSLUCENT,
		EMISSIVE,
		/** Writes no colour (depth/stencil tricks) - nothing visible to draw. */
		HIDDEN
	}

	private record Def(String parent, Set<String> states, Set<String> defines) {
	}

	public static final BedrockMaterials EMPTY = new BedrockMaterials(Map.of());

	private final Map<String, Def> defs;

	private BedrockMaterials(Map<String, Def> defs) {
		this.defs = defs;
	}

	public static BedrockMaterials parse(JsonObject materials) {
		Map<String, Def> defs = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : materials.entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				continue; // e.g. "version"
			}
			String[] nameAndParent = entry.getKey().toLowerCase(Locale.ROOT).split(":", 2);
			JsonObject body = entry.getValue().getAsJsonObject();
			Set<String> states = new HashSet<>();
			Set<String> defines = new HashSet<>();
			addAll(body.get("states"), states);
			addAll(body.get("+states"), states);
			addAll(body.get("defines"), defines);
			addAll(body.get("+defines"), defines);
			defs.put(nameAndParent[0], new Def(nameAndParent.length > 1 ? nameAndParent[1] : null, states, defines));
		}
		return new BedrockMaterials(Map.copyOf(defs));
	}

	/** Whether this material is (or builds on) Bedrock's {@code sheep}: its texture's alpha marks what the dye colours. */
	public boolean isDyed(String material) {
		String name = material == null ? "" : material.toLowerCase(Locale.ROOT);
		for (int depth = 0; depth < 16; depth++) {
			if (name.equals("sheep")) {
				return true;
			}
			Def def = defs.get(name);
			if (def == null || def.parent() == null) {
				return false;
			}
			name = def.parent();
		}
		return false;
	}

	public Kind classify(String material) {
		Set<String> states = new HashSet<>();
		Set<String> defines = new HashSet<>();
		String name = material == null ? "entity_alphatest" : material.toLowerCase(Locale.ROOT);
		for (int depth = 0; depth < 16 && defs.containsKey(name); depth++) {
			Def def = defs.get(name);
			states.addAll(def.states());
			defines.addAll(def.defines());
			if (def.parent() == null) {
				break;
			}
			name = def.parent();
		}
		if (states.contains("DisableColorWrite")) {
			return Kind.HIDDEN;
		}
		if (defines.contains("USE_EMISSIVE") || defines.contains("USE_ONLY_EMISSIVE") || name.contains("emissive")) {
			return Kind.EMISSIVE;
		}
		if (states.contains("Blending") || name.contains("alphablend") || name.contains("translucent")) {
			return Kind.TRANSLUCENT;
		}
		return Kind.CUTOUT;
	}

	private static void addAll(JsonElement element, Set<String> out) {
		if (element instanceof JsonArray array) {
			array.forEach(e -> out.add(e.getAsString()));
		}
	}
}
