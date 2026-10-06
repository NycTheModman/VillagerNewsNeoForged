package com.javafied.villagernews.converter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Small shared helpers for the addon converter. Deliberately has no Minecraft dependencies. */
public final class ConverterUtil {
	/** The mod's own namespace. Everything this converter produces is registered under it. */
	public static final String MOD_ID = "villagernewsjavafied";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private ConverterUtil() {
	}

	public static JsonObject readJson(Path file) throws IOException {
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
	}

	public static void writeJson(Path file, JsonObject obj) throws IOException {
		Files.createDirectories(file.getParent());
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(obj, writer);
		}
	}

	/**
	 * Turns a Bedrock identifier like "geometry.oreville_vn.834522044" into a safe
	 * file-name fragment. Keeps '-' intact (unlike other separators, it is a safe
	 * filename character on every platform we care about) since these identifiers
	 * are often hash-derived and can be negative; collapsing "-98139246" down to
	 * "98139246" could silently collide with a genuinely positive identifier.
	 */
	public static String slug(String identifier) {
		String slug = identifier.replaceAll("[^a-zA-Z0-9-]+", "_");
		slug = slug.replaceAll("^_+|_+$", "");
		return slug.isEmpty() ? "unnamed" : slug;
	}
}
