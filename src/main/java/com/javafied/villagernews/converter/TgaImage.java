package com.javafied.villagernews.converter;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minimal TGA decoder covering what Blockbench/GIMP actually export for these
 * add-on textures: 24/32-bit true-color, either uncompressed (type 2) or
 * run-length encoded (type 10), top-left or bottom-left origin. Java has no
 * built-in TGA reader, and vanilla Minecraft only accepts PNG textures.
 */
public final class TgaImage {
	private TgaImage() {
	}

	public static BufferedImage read(Path file) throws IOException {
		byte[] data = Files.readAllBytes(file);

		int idLength = data[0] & 0xFF;
		int colorMapType = data[1] & 0xFF;
		int imageType = data[2] & 0xFF;
		int colorMapLength = u16(data, 5);
		int colorMapEntrySize = data[7] & 0xFF;
		int width = u16(data, 12);
		int height = u16(data, 14);
		int bpp = data[16] & 0xFF;
		int descriptor = data[17] & 0xFF;
		boolean topLeftOrigin = (descriptor & 0x20) != 0;

		int bytesPerPixel = bpp / 8;
		if (bytesPerPixel != 3 && bytesPerPixel != 4) {
			throw new IOException("Unsupported TGA bit depth " + bpp + " in " + file);
		}

		int offset = 18 + idLength;
		if (colorMapType != 0) {
			offset += colorMapLength * (colorMapEntrySize / 8);
		}

		int pixelCount = width * height;
		int[] pixels = new int[pixelCount];
		int written = 0;

		if (imageType == 2) {
			for (int i = 0; i < pixelCount; i++) {
				pixels[written++] = readPixel(data, offset, bytesPerPixel);
				offset += bytesPerPixel;
			}
		} else if (imageType == 10) {
			while (written < pixelCount) {
				int packet = data[offset++] & 0xFF;
				int count = (packet & 0x7F) + 1;
				if ((packet & 0x80) != 0) {
					int pixel = readPixel(data, offset, bytesPerPixel);
					offset += bytesPerPixel;
					for (int j = 0; j < count && written < pixelCount; j++) {
						pixels[written++] = pixel;
					}
				} else {
					for (int j = 0; j < count && written < pixelCount; j++) {
						pixels[written++] = readPixel(data, offset, bytesPerPixel);
						offset += bytesPerPixel;
					}
				}
			}
		} else {
			throw new IOException("Unsupported TGA image type " + imageType + " in " + file);
		}

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++) {
			int srcRow = topLeftOrigin ? y : (height - 1 - y);
			image.setRGB(0, y, width, 1, pixels, srcRow * width, width);
		}
		return image;
	}

	private static int readPixel(byte[] data, int offset, int bytesPerPixel) {
		int b = data[offset] & 0xFF;
		int g = data[offset + 1] & 0xFF;
		int r = data[offset + 2] & 0xFF;
		int a = bytesPerPixel == 4 ? (data[offset + 3] & 0xFF) : 255;
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static int u16(byte[] data, int offset) {
		return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
	}
}
