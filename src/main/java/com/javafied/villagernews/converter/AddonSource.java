package com.javafied.villagernews.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Locates the resource pack and (optional) behavior pack inside a user-supplied
 * {@code .mcaddon} file or an already-extracted folder, by reading each candidate
 * pack's {@code manifest.json}. Never bundled with the mod; always points at
 * content the user supplied themselves.
 */
public final class AddonSource {
	public final Path resourcePack;
	public final Path behaviorPack;
	/** The add-on's version, from its resource pack's manifest ("1.0.4"); null if it doesn't say. */
	public final String version;

	private AddonSource(Path resourcePack, Path behaviorPack, String version) {
		this.resourcePack = resourcePack;
		this.behaviorPack = behaviorPack;
		this.version = version;
	}

	public static AddonSource locate(Path input, Path stagingDir) throws IOException {
		Path root;
		if (Files.isDirectory(input)) {
			root = input;
		} else {
			root = stagingDir.resolve("extracted");
			unzip(input, root);
		}

		List<Path> candidates = new ArrayList<>();
		if (Files.exists(root.resolve("manifest.json"))) {
			candidates.add(root);
		}
		try (var stream = Files.list(root)) {
			for (Path p : stream.toList()) {
				if (Files.isDirectory(p) && Files.exists(p.resolve("manifest.json"))) {
					candidates.add(p);
				}
			}
		}

		Path rp = null;
		Path bp = null;
		for (Path candidate : candidates) {
			String moduleType = firstModuleType(ConverterUtil.readJson(candidate.resolve("manifest.json")));
			if ("resources".equals(moduleType)) {
				rp = candidate;
			} else if ("data".equals(moduleType) || "script".equals(moduleType)) {
				bp = candidate;
			}
		}

		if (rp == null) {
			throw new IOException("Could not find a resource pack (a manifest.json with a 'resources' module) under " + root
					+ ". Make sure you picked the Villager News .mcaddon (or its extracted folder).");
		}
		return new AddonSource(rp, bp, version(ConverterUtil.readJson(rp.resolve("manifest.json"))));
	}

	private static String version(JsonObject manifest) {
		JsonObject header = manifest.getAsJsonObject("header");
		if (header == null || !header.has("version")) {
			return null;
		}
		if (header.get("version").isJsonPrimitive()) {
			return header.get("version").getAsString(); // newer manifests: "1.0.4"
		}
		List<String> parts = new ArrayList<>();
		header.getAsJsonArray("version").forEach(part -> parts.add(part.getAsString()));
		return String.join(".", parts);
	}

	private static String firstModuleType(JsonObject manifest) {
		JsonArray modules = manifest.getAsJsonArray("modules");
		if (modules == null) {
			return null;
		}
		for (var element : modules) {
			JsonObject module = element.getAsJsonObject();
			if (module.has("type")) {
				return module.get("type").getAsString();
			}
		}
		return null;
	}

	private static void unzip(Path zipFile, Path targetDir) throws IOException {
		Files.createDirectories(targetDir);
		Path normalizedTarget = targetDir.normalize();
		try (InputStream fis = Files.newInputStream(zipFile); ZipInputStream zis = new ZipInputStream(fis)) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				Path outPath = normalizedTarget.resolve(entry.getName()).normalize();
				if (!outPath.startsWith(normalizedTarget)) {
					throw new IOException("Zip entry escapes target directory: " + entry.getName());
				}
				if (entry.isDirectory()) {
					Files.createDirectories(outPath);
				} else {
					Files.createDirectories(outPath.getParent());
					Files.copy(zis, outPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				}
				zis.closeEntry();
			}
		}
	}
}
