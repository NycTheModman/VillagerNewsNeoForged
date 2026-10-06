package com.javafied.villagernews.client.guide;

import com.javafied.villagernews.content.AttachableItem;
import com.javafied.villagernews.guide.GuidePayloads;

import com.javafied.villagernews.platform.ClientPlayConnectionEvents;
import com.javafied.villagernews.platform.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Opens the handbook when it's used, and keeps the world's settings the server sends for its settings page. */
public final class GuideClient {
	private GuideClient() {
	}

	public static void init() {
		AttachableItem.guideOpener = GuideClient::open;
		ClientPlayNetworking.registerGlobalReceiver(GuidePayloads.Settings.TYPE, (payload, context) -> GuideScreen.worldSettings = payload);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> GuideScreen.worldSettings = null);
	}

	private static void open() {
		Minecraft minecraft = Minecraft.getInstance();
		GuideBook.load().ifPresentOrElse(book -> minecraft.setScreen(new GuideScreen(book)), () -> {
			if (minecraft.player != null) {
				minecraft.player.displayClientMessage(Component.translatable("guide.villagernewsjavafied.missing"),true);
			}
		});
	}
}
