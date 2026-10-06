package com.javafied.villagernews.client.guide;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Map;

/**
 * Text written with Bedrock's {@code §} codes, as a component. Most codes mean
 * the same on Java, but Bedrock also has "material" colours - and uses
 * {@code §m} and {@code §n} for two of them, where Java would strike through
 * or underline.
 */
public final class BedrockText {
	/** Bedrock's extra colours: minecoin gold, quartz, iron, netherite, redstone, copper, gold, emerald, diamond, lapis, amethyst, resin. */
	private static final Map<Character, Integer> MATERIAL_COLORS = Map.ofEntries(Map.entry('g', 0xDDD605),
			Map.entry('h', 0xE3D4D1), Map.entry('i', 0xCECACA), Map.entry('j', 0x443A3B), Map.entry('m', 0x971607),
			Map.entry('n', 0xB4684D), Map.entry('p', 0xDEB12D), Map.entry('q', 0x47A036), Map.entry('s', 0x2CBAA8),
			Map.entry('t', 0x21497B), Map.entry('u', 0x9A5CC6), Map.entry('v', 0xEB7114));

	private BedrockText() {
	}

	public static Component of(String text) {
		MutableComponent out = Component.empty();
		Style style = Style.EMPTY;
		StringBuilder run = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c != '§' || i + 1 >= text.length()) {
				run.append(c);
				continue;
			}
			if (!run.isEmpty()) {
				out.append(Component.literal(run.toString()).withStyle(style));
				run.setLength(0);
			}
			style = apply(style, Character.toLowerCase(text.charAt(++i)));
		}
		if (!run.isEmpty()) {
			out.append(Component.literal(run.toString()).withStyle(style));
		}
		return out;
	}

	/** Colours reset the other formatting, as on both editions. */
	private static Style apply(Style style, char code) {
		Integer material = MATERIAL_COLORS.get(code);
		if (material != null) {
			return Style.EMPTY.withColor(material);
		}
		return switch (code) {
			case 'k' -> style.withObfuscated(true);
			case 'l' -> style.withBold(true);
			case 'o' -> style.withItalic(true);
			case 'r' -> Style.EMPTY;
			default -> {
				ChatFormatting formatting = ChatFormatting.getByCode(code);
				yield formatting != null && "0123456789abcdef".indexOf(code) >= 0 ? Style.EMPTY.withColor(formatting) : style;
			}
		};
	}
}
