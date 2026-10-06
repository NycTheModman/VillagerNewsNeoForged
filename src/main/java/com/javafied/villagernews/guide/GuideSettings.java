package com.javafied.villagernews.guide;

import com.javafied.villagernews.content.ModAttachments;

import com.javafied.villagernews.platform.ServerLifecycleEvents;
import com.javafied.villagernews.platform.ServerPlayConnectionEvents;
import com.javafied.villagernews.platform.ServerPlayNetworking;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;


import java.util.HashMap;
import java.util.Map;

/**
 * The handbook's world settings, which the add-on keeps per world: how chatty
 * villagers are, how often they pick their rarer lines, and whether the
 * special characters turn up in villages. (Its per-player settings - subtitles
 * and villager style - live on each player's client.) Operators, or the
 * owner of a singleplayer world, may change them.
 */
public final class GuideSettings {
	public static final String CHATTINESS = "chattiness";
	public static final String RARE_LINES = "rare_lines";
	public static final String SPECIAL_VILLAGERS = "special_villagers";

	public static final int MUTED = 0;
	public static final int SHY = 1;
	public static final int CHATTY = 2;
	public static final int SUPER_CHATTY = 3;
	public static final int RARE_NEVER = 0;
	public static final int RARE_DEFAULT = 1;
	public static final int RARE_OFTEN = 2;
	/** The script's chattiness multipliers, by setting: cooldowns are divided by them. */
	private static final double[] MULTIPLIERS = {0, 0.5, 1, 5};

	public record Values(int chattiness, int rareLines, boolean specialVillagers) {
		public static final Values DEFAULT = new Values(CHATTY, RARE_DEFAULT, true);

		public double multiplier() {
			return MULTIPLIERS[chattiness];
		}

		/** The world-wide "anyone" cooldown a line starts when it sets none itself: 3 seconds when shy, else none. */
		public double defaultGlobalAny() {
			return chattiness == SHY ? 3 : 0;
		}
	}

	private static volatile Values current = Values.DEFAULT;

	private GuideSettings() {
	}

	public static Values current() {
		return current;
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> current = load(server));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> current = Values.DEFAULT);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer()));
		ServerPlayNetworking.registerGlobalReceiver(GuidePayloads.Change.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (canEdit(player)) {
				change(player.level().getServer(), payload.setting(), payload.value());
			} else {
				send(player);
			}
		});
	}

	private static boolean canEdit(ServerPlayer player) {
		return player.hasPermissions(2)
				|| player.level().getServer().isSingleplayerOwner(player.getGameProfile());
	}

	private static void change(MinecraftServer server, String setting, int value) {
		Values v = current;
		Values updated = switch (setting) {
			case CHATTINESS -> new Values(clamp(value, MULTIPLIERS.length - 1), v.rareLines(), v.specialVillagers());
			case RARE_LINES -> new Values(v.chattiness(), clamp(value, RARE_OFTEN), v.specialVillagers());
			case SPECIAL_VILLAGERS -> new Values(v.chattiness(), v.rareLines(), value != 0);
			default -> v;
		};
		current = updated;
		Map<String, Integer> saved = new HashMap<>();
		saved.put(CHATTINESS, updated.chattiness());
		saved.put(RARE_LINES, updated.rareLines());
		saved.put(SPECIAL_VILLAGERS, updated.specialVillagers() ? 1 : 0);
		server.overworld().setData(ModAttachments.GUIDE_SETTINGS, Map.copyOf(saved));
		server.getPlayerList().getPlayers().forEach(GuideSettings::send);
	}

	private static Values load(MinecraftServer server) {
		Map<String, Integer> saved = server.overworld().getExistingData(ModAttachments.GUIDE_SETTINGS).orElse(Map.of());
		return new Values(clamp(saved.getOrDefault(CHATTINESS, CHATTY), MULTIPLIERS.length - 1),
				clamp(saved.getOrDefault(RARE_LINES, RARE_DEFAULT), RARE_OFTEN), saved.getOrDefault(SPECIAL_VILLAGERS, 1) != 0);
	}

	private static void send(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, GuidePayloads.Settings.TYPE)) {
			Values v = current;
			ServerPlayNetworking.send(player, new GuidePayloads.Settings(v.chattiness(), v.rareLines(), v.specialVillagers(), canEdit(player)));
		}
	}

	private static int clamp(int value, int max) {
		return Math.max(0, Math.min(max, value));
	}
}
