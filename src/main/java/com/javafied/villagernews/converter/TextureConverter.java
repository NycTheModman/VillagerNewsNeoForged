package com.javafied.villagernews.converter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Copies Bedrock {@code textures/**} through as plain RGBA {@code .png}
 * files (the only form vanilla Java resource loading reliably accepts),
 * decoding {@code .tga} and re-encoding every {@code .png} too - Bedrock/
 * Blockbench sometimes exports palette-indexed PNGs, which Minecraft's own
 * texture loader silently fails to load (surfaces as "Missing textures").
 */
public final class TextureConverter {
	/** Suffix of the dye-mask texture {@link #opaqueDyeMask} writes next to a sheep-material texture. */
	public static final String DYE_MASK_SUFFIX = "_dye";

	private TextureConverter() {
	}

	public static int convert(Path resourcePack, Path outputAssetsDir) throws IOException {
		Path texturesDir = resourcePack.resolve("textures");
		if (!Files.isDirectory(texturesDir)) {
			return 0;
		}

		Path outDir = outputAssetsDir.resolve("textures");
		int count = 0;

		try (var stream = Files.walk(texturesDir)) {
			for (Path file : stream.filter(Files::isRegularFile).filter(TextureConverter::isImage).toList()) {
				String name = file.getFileName().toString();
				String pngName = name.substring(0, name.lastIndexOf('.')) + ".png";
				Path target = outDir.resolve(texturesDir.relativize(file.getParent())).resolve(pngName);
				writePng(read(file), target);
				count++;
			}
		}
		return count;
	}

	/**
	 * Resolves a Bedrock texture reference, which never carries an extension
	 * (e.g. "textures/oreville/vn/ean"), to the actual {@code .png}/{@code .tga}.
	 */
	public static Path find(Path resourcePack, String extensionlessPath) {
		for (String ext : new String[] {".png", ".tga"}) {
			Path candidate = resourcePack.resolve(extensionlessPath + ext);
			if (Files.exists(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	public static BufferedImage read(Path file) throws IOException {
		if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".tga")) {
			return TgaImage.read(file);
		}
		return toArgb(ImageIO.read(file.toFile()));
	}

	public static void writePng(BufferedImage image, Path target) throws IOException {
		Files.createDirectories(target.getParent());
		ImageIO.write(image, "png", target.toFile());
	}

	private static boolean isImage(Path file) {
		String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
		return lower.endsWith(".png") || lower.endsWith(".tga");
	}

	private static BufferedImage toArgb(BufferedImage source) {
		if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
			return source;
		}
		BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		// Preserve straight RGBA: compositing quantizes RGB in low-alpha dye-mask pixels.
		converted.setRGB(0, 0, source.getWidth(), source.getHeight(),
				source.getRGB(0, 0, source.getWidth(), source.getHeight(), null, 0, source.getWidth()), 0, source.getWidth());
		return converted;
	}

	/**
	 * Bedrock's {@code sheep} material reads a texture's alpha as a dye mask
	 * (wool 255, skin and hooves a faint 3) rather than as opacity. Java draws
	 * alpha as opacity, so the skin would all but vanish: make every pixel
	 * that isn't fully transparent opaque - and keep the mask as a texture of
	 * its own ({@code <name>_dye.png}), which the mod draws over the model
	 * tinted with the sheep's colour.
	 */
	public static void opaqueDyeMask(Path png) throws IOException {
		if (!Files.exists(png)) {
			return;
		}
		BufferedImage image = read(png);
		// The mask itself, as its own texture: the pixels the dye colours (the wool), everything else clear.
		BufferedImage dye = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int argb = image.getRGB(x, y);
				dye.setRGB(x, y, (argb >>> 24) >= 128 ? argb | 0xFF000000 : 0);
			}
		}
		String name = png.getFileName().toString();
		writePng(dye, png.resolveSibling(name.substring(0, name.length() - ".png".length()) + DYE_MASK_SUFFIX + ".png"));
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int argb = image.getRGB(x, y);
				if ((argb >>> 24) != 0) {
					image.setRGB(x, y, argb | 0xFF000000);
				}
			}
		}
		writePng(image, png);
	}
}
