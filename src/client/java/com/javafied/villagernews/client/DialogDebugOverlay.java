package com.javafied.villagernews.client;

import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.dialog.DialogPayloads;

import com.javafied.villagernews.platform.ClientPlayNetworking;
import com.javafied.villagernews.platform.HudElementRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import java.util.List;

/** Draws the server's villager debug lines ({@code /villagernews debug}) in the top-left corner. */
public final class DialogDebugOverlay {
	private static List<String> lines = List.of();

	private DialogDebugOverlay() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(DialogPayloads.Debug.TYPE, (payload, context) -> lines = List.copyOf(payload.lines()));
		HudElementRegistry.addLast(VillagerNewsJavafied.id("dialog_debug"), (graphics, delta) -> {
			if (lines.isEmpty()) {
				return;
			}
			Font font = Minecraft.getInstance().font;
			int width = 0;
			for (String line : lines) {
				width = Math.max(width, font.width(line));
			}
			int x = 4;
			int y = 4;
			graphics.fill(x - 2, y - 2, x + width + 2, y + lines.size() * (font.lineHeight + 1) + 1, 0x90000000);
			for (int i = 0; i < lines.size(); i++) {
				graphics.drawString(font, lines.get(i), x, y + i * (font.lineHeight + 1), i == 0 ? 0xFFFFE070 : 0xFFE0E0E0, false);
			}
		});
	}
}
