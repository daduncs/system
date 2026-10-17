/*
 * This file is part of System Client (https://github.com/daduncs/system)
 * Website: https://download.daduncs.cloud/
 *
 * Copyright (c) 2024 - 2026 DLindustries and project contributors.
 *
 * System Client is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * System Client is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with System Client. If not, see <https://www.gnu.org/licenses/>.
 */

package dlindustries.uk.daduncs.font;

import lombok.Getter;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class GlyphPage {

	private static final AtomicInteger TEXTURE_COUNTER = new AtomicInteger();

	private int imgSize;
	@Getter
	private int maxFontHeight = -1;
	private final Font font;
	private final boolean antiAliasing;
	private final boolean fractionalMetrics;
	private final HashMap<Character, Glyph> glyphCharacterMap = new HashMap<>();

	private BufferedImage bufferedImage;
	private AbstractTexture loadedTexture;
	private Identifier textureId;

	public GlyphPage(Font font, boolean antiAliasing, boolean fractionalMetrics) {
		this.font = font;
		this.antiAliasing = antiAliasing;
		this.fractionalMetrics = fractionalMetrics;
	}

	public void generateGlyphPage(char[] chars) {
		double maxWidth = -1;
		double maxHeight = -1;

		AffineTransform affineTransform = new AffineTransform();
		FontRenderContext fontRenderContext = new FontRenderContext(affineTransform, antiAliasing, fractionalMetrics);

		for (char ch : chars) {
			Rectangle2D bounds = font.getStringBounds(Character.toString(ch), fontRenderContext);

			if (maxWidth < bounds.getWidth())
				maxWidth = bounds.getWidth();
			if (maxHeight < bounds.getHeight())
				maxHeight = bounds.getHeight();
		}

		maxWidth += 2;
		maxHeight += 2;

		imgSize = (int) Math.ceil(Math.max(Math.ceil(Math.sqrt(maxWidth * maxWidth * chars.length) / maxWidth),
				Math.ceil(Math.sqrt(maxHeight * maxHeight * chars.length) / maxHeight)) * Math.max(maxWidth, maxHeight))
				+ 1;

		bufferedImage = new BufferedImage(imgSize, imgSize, BufferedImage.TYPE_INT_ARGB);

		Graphics2D g = bufferedImage.createGraphics();

		g.setFont(font);
		g.setColor(new Color(255, 255, 255, 0));
		g.fillRect(0, 0, imgSize, imgSize);

		g.setColor(Color.white);

		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fractionalMetrics ? RenderingHints.VALUE_FRACTIONALMETRICS_ON : RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antiAliasing ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, antiAliasing ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

		FontMetrics fontMetrics = g.getFontMetrics();

		int currentCharHeight = 0;
		int posX = 0;
		int posY = 1;

		for (char ch : chars) {
			Glyph glyph = new Glyph();

			Rectangle2D bounds = fontMetrics.getStringBounds(Character.toString(ch), g);

			glyph.width = bounds.getBounds().width + 8;
			glyph.height = bounds.getBounds().height;

			if (posX + glyph.width >= imgSize) {
				posX = 0;
				posY += currentCharHeight;
				currentCharHeight = 0;
			}

			glyph.x = posX;
			glyph.y = posY;

			if (glyph.height > maxFontHeight)
				maxFontHeight = glyph.height;

			if (glyph.height > currentCharHeight)
				currentCharHeight = glyph.height;

			g.drawString(Character.toString(ch), posX + 2, posY + fontMetrics.getAscent());

			posX += glyph.width;

			glyphCharacterMap.put(ch, glyph);
		}

		for (Glyph glyph : glyphCharacterMap.values())
			measure(glyph);
	}

	private void measure(Glyph glyph) {
		glyph.inkLeft = glyph.width;
		glyph.inkRight = 0;

		for (int px = 0; px < glyph.width && glyph.x + px < imgSize; px++) {
			for (int py = 0; py < glyph.height && glyph.y + py < imgSize; py++) {
				if ((bufferedImage.getRGB(glyph.x + px, glyph.y + py) >>> 24) > 32) {
					glyph.inkLeft = Math.min(glyph.inkLeft, px);
					glyph.inkRight = Math.max(glyph.inkRight, px + 1);
				}
			}
		}
	}

	public void setupTexture() {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(bufferedImage, "png", baos);
			byte[] bytes = baos.toByteArray();

			ByteBuffer data = BufferUtils.createByteBuffer(bytes.length).put(bytes);
			data.flip();

			int textureIndex = TEXTURE_COUNTER.incrementAndGet();
			NativeImageBackedTexture texture = new LinearFontTexture(
					() -> "system_font_" + textureIndex,
					NativeImage.read(data)
			);
			texture.upload();

			loadedTexture = texture;
			textureId = Identifier.of("system", "font/" + textureIndex);
			MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, loadedTexture);
		} catch (Exception ignored) {
		}
	}

	public float drawChar(DrawContext context, char ch, float x, float y, int color) {
		Glyph glyph = glyphCharacterMap.get(ch);

		if (glyph == null || textureId == null)
			return 0;

		Matrix3x2fStack matrices = context.getMatrices();
		matrices.pushMatrix();
		matrices.translate(x, y);

		context.drawTexture(
				RenderPipelines.GUI_TEXTURED,
				textureId,
				0,
				0,
				(float) glyph.x,
				(float) glyph.y,
				glyph.width,
				glyph.height,
				imgSize,
				imgSize,
				color
		);

		matrices.popMatrix();

		return glyph.width - 8;
	}

	public float drawCharGlow(DrawContext context, char ch, float x, float y, int color, float spread) {
		Glyph glyph = glyphCharacterMap.get(ch);

		if (glyph == null || textureId == null)
			return 0;

		dlindustries.uk.daduncs.render.Draw.glyphGlow(context, x - spread, y - spread,
				glyph.width + spread * 2f, glyph.height + spread * 2f,
				glyph.x, glyph.y, spread, textureId, color, 1f);

		return glyph.width - 8;
	}

	void destroy() {
		if (textureId != null)
			MinecraftClient.getInstance().getTextureManager().destroyTexture(textureId);
	}

	Identifier texture() {
		return textureId;
	}

	int atlas() {
		return imgSize;
	}

	Glyph glyph(char ch) {
		return glyphCharacterMap.get(ch);
	}

	public int inkLeft(char ch) {
		Glyph glyph = glyphCharacterMap.get(ch);
		return glyph == null || glyph.inkRight == 0 ? 0 : glyph.inkLeft;
	}

	public int inkRight(char ch) {
		Glyph glyph = glyphCharacterMap.get(ch);
		return glyph == null || glyph.inkRight == 0 ? 0 : glyph.inkRight;
	}

	public float getWidth(char ch) {
		Glyph glyph = glyphCharacterMap.get(ch);
		return glyph == null ? 0 : glyph.width;
	}

	public boolean isAntiAliasingEnabled() {
		return antiAliasing;
	}

	public boolean isFractionalMetricsEnabled() {
		return fractionalMetrics;
	}

	@Getter
	static class Glyph {
		private int x;
		private int y;
		private int width;
		private int height;
		private int inkLeft;
		private int inkRight;

		Glyph(int x, int y, int width, int height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}

		Glyph() {
		}
	}

	private static final class LinearFontTexture extends NativeImageBackedTexture {
		private LinearFontTexture(java.util.function.Supplier<String> label, NativeImage image) {
			super(label, image);
			this.sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
		}
	}
}
