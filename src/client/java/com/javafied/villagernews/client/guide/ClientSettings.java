package com.javafied.villagernews.client.guide;

import com.javafied.villagernews.VillagerNewsJavafied;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.javafied.villagernews.platform.LoaderEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The handbook's per-player settings, which the add-on keeps per player and
 * which only change what this player sees: villagers' subtitles (off by
 * default, as in the add-on) and the villager style.
 */
public final class ClientSettings {
	private static final Path FILE = LoaderEnvironment.getInstance().getConfigDir().resolve(VillagerNewsJavafied.MOD_ID + "-client.json");

	private static boolean loaded;
	private static boolean subtitles;
	private static int style;

	private ClientSettings() {
	}

	public static boolean subtitles() {
		load();
		return subtitles;
	}

	/** 0 vanilla, 1 "Actions & Stuff", 2 its flat variant (the add-on's {@code p:pmpece}). */
	public static int style() {
		load();
		return style;
	}

	public static void setSubtitles(boolean value) {
		load();
		subtitles = value;
		save();
	}

	public static void setStyle(int value) {
		load();
		style = Math.max(0, Math.min(2, value));
		save();
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		if (!Files.exists(FILE)) {
			return;
		}
		try {
			JsonObject json = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
			subtitles = json.has("subtitles") && json.get("subtitles").getAsBoolean();
			style = json.has("style") ? Math.max(0, Math.min(2, json.get("style").getAsInt())) : 0;
		} catch (IOException | RuntimeException e) {
			VillagerNewsJavafied.LOGGER.warn("Couldn't read {}", FILE, e);
		}
	}

	private static void save() {
		JsonObject json = new JsonObject();
		json.addProperty("subtitles", subtitles);
		json.addProperty("style", style);
		try {
			Files.writeString(FILE, json.toString());
		} catch (IOException e) {
			VillagerNewsJavafied.LOGGER.warn("Couldn't save {}", FILE, e);
		}
	}
}
