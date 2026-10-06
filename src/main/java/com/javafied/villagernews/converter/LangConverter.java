package com.javafied.villagernews.converter;

import com.google.gson.JsonObject;

import com.javafied.villagernews.names.AddonNames;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Converts Bedrock {@code texts/<locale>.lang} (key=value, with Bedrock's
 * tab-then-"#" trailing comments) into Java {@code lang/<locale>.json}.
 */
public final class LangConverter {
	private LangConverter() {
	}

	public static int convert(Path resourcePack, Path outputAssetsDir, AddonNames names) throws IOException {
		Path textsDir = resourcePack.resolve("texts");
		if (!Files.isDirectory(textsDir)) {
			return 0;
		}
		Path langOutDir = outputAssetsDir.resolve("lang");
		Files.createDirectories(langOutDir);

		int totalKeys = 0;
		try (var stream = Files.list(textsDir)) {
			for (Path file : stream.filter(p -> p.toString().endsWith(".lang")).toList()) {
				String fileName = file.getFileName().toString();
				String locale = fileName.substring(0, fileName.length() - ".lang".length()).toLowerCase(Locale.ROOT);

				JsonObject out = new JsonObject();
				for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
					String trimmed = line.strip();
					if (trimmed.isEmpty() || trimmed.startsWith("#")) {
						continue;
					}
					int eq = trimmed.indexOf('=');
					if (eq < 0) {
						continue;
					}
					String key = trimmed.substring(0, eq).strip();
					String value = trimmed.substring(eq + 1);
					int tab = value.indexOf('\t');
					if (tab >= 0) {
						value = value.substring(0, tab);
					}
					value = value.strip();
					out.addProperty(toJavaKey(key, names), value);
				}

				ConverterUtil.writeJson(langOutDir.resolve(locale + ".json"), out);
				totalKeys += out.size();
			}
		}
		return totalKeys;
	}

	/**
	 * Bedrock keys like "entity.oreville_vn:mlkxjo.name" (and, e.g.,
	 * "item.spawn_egg.entity.oreville_vn:mlkxjo.name") carry the addon's own
	 * namespace immediately before a colon, with an arbitrary prefix before that,
	 * and always end in ".name". Java's actual description id (see
	 * {@code Util.makeDescriptionId}) is just "entity.&lt;modid&gt;.path" - no
	 * ".name" suffix at all (confirmed against vanilla's own lang file: it's
	 * "item.minecraft.diamond_sword", never "...diamond_sword.name") - so both
	 * the namespace swap and the suffix strip are needed for the result to
	 * actually match what Java looks up, as long as entities/items are
	 * registered under the same short id used as the path here (e.g.
	 * "villagernewsjavafied:mlkxjo").
	 */
	private static String toJavaKey(String bedrockKey, AddonNames names) {
		if (!bedrockKey.contains(":")) {
			return bedrockKey;
		}
		// The id after the namespace becomes the mod's readable one ("oreville_vn:ilvfra" -> "villagernewsjavafied.mayor").
		java.util.regex.Matcher id = java.util.regex.Pattern.compile("[A-Za-z0-9_]+:([A-Za-z0-9_]+)").matcher(bedrockKey);
		StringBuilder renamed = new StringBuilder();
		while (id.find()) {
			String name = names.name(AddonNames.Kind.ITEM, id.group(1));
			if (name == null) {
				name = names.name(AddonNames.Kind.CHARACTER, id.group(1));
			}
			id.appendReplacement(renamed, java.util.regex.Matcher.quoteReplacement(ConverterUtil.MOD_ID + "." + (name != null ? name : id.group(1))));
		}
		id.appendTail(renamed);
		String javaKey = renamed.toString();
		if (javaKey.endsWith(".name")) {
			javaKey = javaKey.substring(0, javaKey.length() - ".name".length());
		}
		return javaKey;
	}
}
