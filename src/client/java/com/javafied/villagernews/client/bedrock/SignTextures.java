package com.javafied.villagernews.client.bedrock;

import com.javafied.villagernews.VillagerNewsJavafied;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The sign a villager holds up is drawn with Bedrock's own sign textures
 * ("textures/entity/sign_spruce", ...), which the add-on borrows from the
 * game rather than ships. Java has the same wood art in another layout
 * ({@code textures/block/<wood>_sign.png}, 32x32), so on first use each one
 * is rearranged into Bedrock's 64x32 sign layout.
 */
final class SignTextures {
	private static final String PREFIX = "textures/generated/sign/";
	private static final Map<String, String> WOOD_BY_BEDROCK_PATH = Map.ofEntries(Map.entry("textures/entity/sign", "oak"),
			Map.entry("textures/entity/sign_spruce", "spruce"), Map.entry("textures/entity/sign_birch", "birch"),
			Map.entry("textures/entity/sign_jungle", "jungle"), Map.entry("textures/entity/sign_acacia", "acacia"),
			Map.entry("textures/entity/sign_darkoak", "dark_oak"), Map.entry("textures/entity/mangrove_sign", "mangrove"),
			Map.entry("textures/entity/cherry_sign", "cherry"), Map.entry("textures/entity/pale_oak_sign", "pale_oak"),
			Map.entry("textures/entity/bamboo_sign", "bamboo"), Map.entry("textures/entity/sign_crimson", "crimson"),
			Map.entry("textures/entity/sign_warped", "warped"));
	/**
	 * The board's faces, as {Java x, Java y, Bedrock x, Bedrock y, width,
	 * height} in pixels: Java's from its sign block model, Bedrock's from the
	 * add-on's sign geometry.
	 */
	private static final int[][] FACES = {
			{0, 0, 2, 0, 24, 2}, // up
			{0, 28, 26, 0, 24, 2}, // down
			{0, 16, 2, 2, 24, 12}, // north
			{0, 2, 28, 2, 24, 12}, // south
			{24, 2, 0, 2, 2, 12}, // east
			{24, 16, 26, 2, 2, 12}}; // west

	private static final Set<ResourceLocation> loaded = new HashSet<>();

	private SignTextures() {
	}

	/** Null if this isn't one of the Bedrock sign textures. */
	static ResourceLocation idFor(String bedrockPath) {
		String wood = WOOD_BY_BEDROCK_PATH.get(bedrockPath.toLowerCase(Locale.ROOT));
		return wood == null ? null : VillagerNewsJavafied.id(PREFIX + wood + ".png");
	}

	static boolean isGenerated(ResourceLocation id) {
		return id.getNamespace().equals(VillagerNewsJavafied.MOD_ID) && id.getPath().startsWith(PREFIX);
	}

	/** Builds the texture the first time it's drawn. */
	static void ensureLoaded(ResourceLocation id) {
		if (!isGenerated(id) || !loaded.add(id)) {
			return;
		}
		String wood = id.getPath().substring(PREFIX.length(), id.getPath().length() - ".png".length());
		Minecraft minecraft = Minecraft.getInstance();
		try (InputStream in=minecraft.getResourceManager().open(ResourceLocation.withDefaultNamespace("textures/entity/signs/"+(wood.equals("pale_oak")?"oak":wood)+".png"))) {
            NativeImage image=NativeImage.read(in);
            minecraft.getTextureManager().register(id,new DynamicTexture(image));
		} catch (IOException | RuntimeException e) {
			VillagerNewsJavafied.LOGGER.warn("Couldn't make the {} sign texture", wood, e);
		}
	}
}
