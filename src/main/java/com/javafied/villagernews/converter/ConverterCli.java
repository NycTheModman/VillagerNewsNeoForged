package com.javafied.villagernews.converter;

import com.javafied.villagernews.names.AddonNames;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the addon -> Java asset conversion. Usable both as a dev-time
 * command (see the {@code runConverter} Gradle task) and, later, from the
 * mod's runtime first-run flow.
 */
public final class ConverterCli {
	/** Resource pack format for Minecraft 1.21.1 (see the client jar's version.json: pack_version.resource_major). */
	private static final int PACK_FORMAT = 34;
	/**
	 * Bump whenever the converter's output changes shape, so packs converted by
	 * an older version of the mod get re-converted automatically.
	 */
	public static final int SCHEMA_VERSION = 13;
	/** The converted pack's icon (Java's name for a pack icon). */
	public static final String PACK_ICON = "pack.png";

	private ConverterCli() {
	}

	public static void main(String[] args) throws IOException {
		if (args.length < 2) {
			System.err.println("Usage: ConverterCli <mcaddon-or-extracted-dir> <output-dir>");
			System.exit(1);
			return;
		}
		Path source = Path.of(args[0]);
		Path outputDir = Path.of(args[1]);
		run(source, outputDir);
	}

	public static void run(Path source, Path outputDir) throws IOException {
		Files.createDirectories(outputDir);
		Path stagingDir = Files.createTempDirectory("villagernewsjavafied-convert-");
		Path assetsDir = outputDir.resolve("assets").resolve(ConverterUtil.MOD_ID);

		try {
			runInternal(source, outputDir, assetsDir, stagingDir);
		} finally {
			deleteRecursive(stagingDir);
		}
	}

	private static void runInternal(Path source, Path outputDir, Path assetsDir, Path stagingDir) throws IOException {
		AddonSource addon = AddonSource.locate(source, stagingDir);
		System.out.println("Resource pack: " + addon.resourcePack);
		System.out.println("Behavior pack: " + (addon.behaviorPack != null ? addon.behaviorPack : "(none)"));

		AddonNames names = AddonNames.forVersion(addon.version);
		System.out.println("Add-on version: " + addon.version + (names.known() ? "" : " - no names file for it; using " + names.version() + "'s"));

		Map<String, String> geometryIndex = GeometryConverter.convert(addon.resourcePack, assetsDir);
		int textureCount = TextureConverter.convert(addon.resourcePack, assetsDir);
		int soundCount = SoundConverter.convert(addon.resourcePack, assetsDir);
		int langKeyCount = LangConverter.convert(addon.resourcePack, assetsDir, names);
		int itemCount = ItemConverter.convert(addon.resourcePack, addon.behaviorPack, assetsDir, names);
		int bedrockFileCount = BedrockDataConverter.convert(addon.resourcePack, addon.behaviorPack, assetsDir);
		int dialogCount = ScriptDataConverter.convert(addon.behaviorPack, outputDir, names);
		int guidePageCount = GuideConverter.convert(addon.behaviorPack, assetsDir, names);

		List<String> skipped = new ArrayList<>();
		if (addon.behaviorPack != null) {
			collectSkipped(addon.behaviorPack.resolve("functions"), addon.behaviorPack, skipped);
			collectSkipped(addon.behaviorPack.resolve("scripts"), addon.behaviorPack, skipped);
		}

		writeManifest(outputDir, addon.version, geometryIndex, textureCount, soundCount, langKeyCount, skipped);
		writeSkipReport(outputDir, skipped);
		writePackMcmeta(outputDir);
		copyPackIcon(addon.resourcePack, outputDir);

		System.out.println("Geometries converted: " + geometryIndex.size());
		System.out.println("Textures converted: " + textureCount);
		System.out.println("Sounds copied: " + soundCount);
		System.out.println("Lang keys converted: " + langKeyCount);
		System.out.println("Item icons/models converted: " + itemCount);
		System.out.println("Bedrock definition files carried over: " + bedrockFileCount);
		System.out.println("Dialogs extracted from the behavior script: " + dialogCount);
		System.out.println("Handbook pages extracted from the behavior script: " + guidePageCount);
		System.out.println("Skipped (behavior logic, ported later): " + skipped.size() + " files - see skip-report.txt");
	}

	private static void collectSkipped(Path dir, Path packRoot, List<String> out) throws IOException {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (var stream = Files.walk(dir)) {
			for (Path file : stream.filter(Files::isRegularFile).toList()) {
				out.add(packRoot.relativize(file).toString());
			}
		}
	}

	private static void writeManifest(Path outputDir, String addonVersion, Map<String, String> geometryIndex,
			int textureCount, int soundCount, int langKeyCount,
			List<String> skipped) throws IOException {
		JsonObject manifest = new JsonObject();
		manifest.addProperty("converterSchemaVersion", SCHEMA_VERSION);
		if (addonVersion != null) {
			manifest.addProperty("addonVersion", addonVersion);
		}

		JsonObject counts = new JsonObject();
		counts.addProperty("geometries", geometryIndex.size());
		counts.addProperty("textures", textureCount);
		counts.addProperty("sounds", soundCount);
		counts.addProperty("langKeys", langKeyCount);
		counts.addProperty("skippedBehaviorFiles", skipped.size());
		manifest.add("counts", counts);

		JsonObject geometry = new JsonObject();
		geometryIndex.forEach(geometry::addProperty);
		manifest.add("geometry", geometry);

		ConverterUtil.writeJson(outputDir.resolve("manifest.json"), manifest);
	}

	private static void writeSkipReport(Path outputDir, List<String> skipped) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("Files not auto-converted - real game logic, ported by hand in follow-up passes:\n\n");
		for (String path : skipped) {
			sb.append(path).append('\n');
		}
		Files.writeString(outputDir.resolve("skip-report.txt"), sb.toString());
	}

	private static void writePackMcmeta(Path outputDir) throws IOException {
		JsonObject pack = new JsonObject();
		pack.addProperty("description", "Villager News: Javafied (converted from your own Bedrock add-on)");
		// Formats above 64 must declare a supported range instead of a bare pack_format.
		pack.addProperty("pack_format", PACK_FORMAT);
		
		JsonObject root = new JsonObject();
		root.add("pack", pack);
		ConverterUtil.writeJson(outputDir.resolve("pack.mcmeta"), root);
	}

	/**
	 * The add-on's own icon, as the converted pack's: shown in the resource
	 * pack list, and by Mod Menu for this mod (it lives only in the player's
	 * converted pack, never in the mod).
	 */
	private static void copyPackIcon(Path resourcePack, Path outputDir) throws IOException {
		Path icon = resourcePack == null ? null : resourcePack.resolve("pack_icon.png");
		if (icon != null && Files.isRegularFile(icon)) {
			Files.copy(icon, outputDir.resolve(PACK_ICON), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void deleteRecursive(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			return;
		}
		try (var stream = Files.walk(dir)) {
			for (Path path : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}
}
