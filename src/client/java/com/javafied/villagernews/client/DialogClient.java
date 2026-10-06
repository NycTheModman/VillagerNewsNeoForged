package com.javafied.villagernews.client;

import com.javafied.villagernews.client.bedrock.BedrockRuntime;
import com.javafied.villagernews.client.bedrock.BedrockSounds;
import com.javafied.villagernews.client.guide.ClientSettings;
import com.javafied.villagernews.dialog.DialogLibrary;
import com.javafied.villagernews.dialog.DialogPayloads;

import com.javafied.villagernews.platform.ClientTickEvents;
import com.javafied.villagernews.platform.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Plays the lines the server's dialog engine starts: the voice (following the
 * speaker), the line's lip-sync animation on the speaker's model, and its
 * subtitles - shown in the action bar when Minecraft's own "Show Subtitles"
 * option is on (the add-on's equivalent setting is off by default too).
 */
public final class DialogClient {
	private static final double SUBTITLE_RANGE = 16;

	private record Subtitle(Entity speaker, long showAt, String key) {
	}

	private record Voice(SoundInstance sound, long until) {
	}

	private static final Map<Integer, Voice> voices = new HashMap<>();
	private static final List<Subtitle> subtitles = new ArrayList<>();
	private static long ticks;

	private DialogClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(DialogPayloads.Line.TYPE, (payload, context) -> speak(context.client(), payload));
		ClientPlayNetworking.registerGlobalReceiver(DialogPayloads.Stop.TYPE, (payload, context) -> hush(context.client(), payload.entityId()));
		ClientTickEvents.END_CLIENT_TICK.register(DialogClient::tick);
	}

	private static void speak(Minecraft client, DialogPayloads.Line line) {
		if (client.level == null) {
			return;
		}
		Entity speaker = client.level.getEntity(line.entityId());
		if (speaker == null) {
			return;
		}
		hush(client, line.entityId());
		voices.put(line.entityId(), new Voice(BedrockSounds.playFrom(speaker, line.sound()), ticks + line.durationTicks() + 20));
		BedrockRuntime.playAnimation(speaker, line.animation());
		for (DialogLibrary.Subtitle subtitle : line.subtitles()) {
			subtitles.add(new Subtitle(speaker, ticks + Math.round(subtitle.time() * 20), subtitle.key()));
		}
	}

	private static void hush(Minecraft client, int entityId) {
		Voice voice = voices.remove(entityId);
		if (voice != null) {
			client.getSoundManager().stop(voice.sound());
		}
		subtitles.removeIf(subtitle -> subtitle.speaker().getId() == entityId);
	}

	private static void tick(Minecraft client) {
		ticks++;
		voices.values().removeIf(voice -> ticks > voice.until());
		for (Iterator<Subtitle> it = subtitles.iterator(); it.hasNext(); ) {
			Subtitle subtitle = it.next();
			if (ticks < subtitle.showAt()) {
				continue;
			}
			it.remove();
			if (client.player != null && (ClientSettings.subtitles() || client.options.showSubtitles().get()) && subtitle.speaker().isAlive()
					&& subtitle.speaker().distanceTo(client.player) <= SUBTITLE_RANGE) {
				client.gui.setOverlayMessage(Component.translatable(subtitle.key()), false);
			}
		}
	}
}
