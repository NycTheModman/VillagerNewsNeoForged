package com.javafied.villagernews.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts Bedrock {@code models/entity/*.json} geometry into one GeckoLib
 * {@code .geo.json} file per geometry identifier (GeckoLib, like its Blockbench
 * exporter, expects a single geometry per file, whereas Bedrock happily bundles
 * several per file).
 */
public final class GeometryConverter {
	private GeometryConverter() {
	}

	/** Returns a map of Bedrock geometry identifier -> path relative to the assets/&lt;modid&gt; root. */
	public static Map<String, String> convert(Path resourcePack, Path outputAssetsDir) throws IOException {
		Map<String, String> identifierToPath = new LinkedHashMap<>();
		Path modelsDir = resourcePack.resolve("models").resolve("entity");
		if (!Files.isDirectory(modelsDir)) {
			return identifierToPath;
		}

		// GeckoLib 5.x scans "geckolib/models/" (not "geo/") to avoid clashing with vanilla's own "models/" folder.
		Path outDir = outputAssetsDir.resolve("geckolib").resolve("models").resolve("entity");
		Files.createDirectories(outDir);

		try (var stream = Files.walk(modelsDir)) {
			for (Path file : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
				JsonObject root = ConverterUtil.readJson(file);
				JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
				if (geometries == null) {
					continue;
				}
				for (var element : geometries) {
					JsonObject geometry = element.getAsJsonObject();
					JsonObject description = geometry.getAsJsonObject("description");
					if (description == null || !description.has("identifier")) {
						continue;
					}
					String identifier = description.get("identifier").getAsString();
					String slug = ConverterUtil.slug(identifier);

					JsonObject outFile = new JsonObject();
					outFile.addProperty("format_version", "1.16.0");
					JsonArray singleEntry = new JsonArray();
					singleEntry.add(geometry);
					outFile.add("minecraft:geometry", singleEntry);

					Path outPath = outDir.resolve(slug + ".geo.json");
					ConverterUtil.writeJson(outPath, outFile);
					identifierToPath.put(identifier, "geckolib/models/entity/" + slug + ".geo.json");
				}
			}
		}
		return identifierToPath;
	}

	/** The geometry with this identifier anywhere in the pack's {@code models/}, as a fresh copy; null if absent. */
	public static JsonObject find(Path resourcePack, String identifier) throws IOException {
		Path modelsDir = resourcePack.resolve("models");
		if (identifier == null || !Files.isDirectory(modelsDir)) {
			return null;
		}
		try (var stream = Files.walk(modelsDir)) {
			for (Path file : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
				JsonArray geometries = ConverterUtil.readJson(file).getAsJsonArray("minecraft:geometry");
				if (geometries == null) {
					continue;
				}
				for (var element : geometries) {
					JsonObject description = element.getAsJsonObject().getAsJsonObject("description");
					if (description != null && identifier.equals(description.has("identifier") ? description.get("identifier").getAsString() : null)) {
						return element.getAsJsonObject().deepCopy();
					}
				}
			}
		}
		return null;
	}
}
