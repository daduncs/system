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

import dlindustries.uk.daduncs.gui.Theme;
import dlindustries.uk.daduncs.mixin.RenderLayerAccessor;
import dlindustries.uk.daduncs.render.renderers.pipeline.PipelineInitializer;
import dlindustries.uk.daduncs.render.renderers.pipeline.PipelineRegistry;
import dlindustries.uk.daduncs.utils.RenderUtils;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

public final class WorldFont {

	private static final float PAD = 8f;
	private static final Map<Identifier, RenderLayer> layers = new HashMap<>();
	private static final Map<Identifier, RenderLayer> comics = new HashMap<>();

	public static float width(GlyphPageFontRenderer font, CharSequence text) {
		GlyphPage page = font.page();
		float wide = 0f;

		for (int i = 0; i < text.length(); i++) {
			GlyphPage.Glyph glyph = page.glyph(text.charAt(i));

			if (glyph != null)
				wide += glyph.getWidth() - PAD;
		}

		return wide;
	}

	public static void vanilla(MatrixStack matrices, CharSequence text, int color) {
		try (BufferAllocator allocator = new BufferAllocator(2048)) {
			VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);

			dlindustries.uk.daduncs.System.mc.textRenderer.draw(text.toString(), 0f, 0f, color, false,
					matrices.peek().getPositionMatrix(), immediate,
					net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);

			immediate.draw();
		}
	}

	public static float vanillaWidth(CharSequence text) {
		return dlindustries.uk.daduncs.System.mc.textRenderer.getWidth(text.toString());
	}

	public static float height(GlyphPageFontRenderer font) {
		return font.page().getMaxFontHeight();
	}

	public static void draw(GlyphPageFontRenderer font, MatrixStack matrices, CharSequence text,
							int hot, int cold, float phase, float gone, float born, float spread) {
		GlyphPage page = font.page();
		Identifier texture = page.texture();

		if (texture == null || text.isEmpty())
			return;

		Matrix4f matrix = new Matrix4f(matrices.peek().getPositionMatrix());
		float atlas = page.atlas();
		float span = Math.max(1f, width(font, text));
		int count = text.length();

		RenderUtils.drawLayer(layer(texture), buffer -> {
			float cursor = 0f;

			for (int i = 0; i < count; i++) {
				GlyphPage.Glyph glyph = page.glyph(text.charAt(i));

				if (glyph == null)
					continue;

				float slice = cursor / span;
				float stop = (cursor + glyph.getWidth()) / span;

				quad(buffer, matrix, cursor, glyph, atlas,
						shade(hot, cold, slice, phase), shade(hot, cold, stop, phase),
						ramp(gone, slice, 0.35f), ramp(born, slice, 0.55f), spread);

				cursor += glyph.getWidth() - PAD;
			}
		});
	}

	public static void outlined(GlyphPageFontRenderer font, MatrixStack matrices, CharSequence text, int color, float thickness) {
		GlyphPage page = font.page();
		Identifier texture = page.texture();

		if (texture == null || text.isEmpty())
			return;

		Matrix4f matrix = new Matrix4f(matrices.peek().getPositionMatrix());
		RenderLayer layer = comic(texture);

		RenderUtils.drawLayer(layer, buffer -> letters(buffer, matrix, page, text, color & 0xFF000000, thickness));
		RenderUtils.drawLayer(layer, buffer -> letters(buffer, matrix, page, text, color, 0f));
	}

	private static void letters(VertexConsumer buffer, Matrix4f matrix, GlyphPage page, CharSequence text, int color, float grow) {
		float atlas = page.atlas();
		float cursor = 0f;

		for (int i = 0; i < text.length(); i++) {
			GlyphPage.Glyph glyph = page.glyph(text.charAt(i));

			if (glyph == null)
				continue;

			float w = glyph.getWidth();
			float h = glyph.getHeight();

			float u0 = (glyph.getX() - grow) / atlas;
			float v0 = (glyph.getY() - grow) / atlas;
			float u1 = (glyph.getX() + w + grow) / atlas;
			float v1 = (glyph.getY() + h + grow) / atlas;

			edge(buffer, matrix, cursor - grow, h + grow, u0, v0, color, glyph, grow);
			edge(buffer, matrix, cursor - grow, -grow, u0, v1, color, glyph, grow);
			edge(buffer, matrix, cursor + w + grow, -grow, u1, v1, color, glyph, grow);
			edge(buffer, matrix, cursor + w + grow, h + grow, u1, v0, color, glyph, grow);

			cursor += w - PAD;
		}
	}

	private static void edge(VertexConsumer buffer, Matrix4f matrix, float px, float py, float u, float v,
							 int color, GlyphPage.Glyph glyph, float grow) {
		buffer.vertex(matrix, px, py, 0f)
				.color(color)
				.texture(u, v)
				.overlay(glyph.getX(), glyph.getY())
				.light(0, 0)
				.normal(glyph.getWidth() / 127f, glyph.getHeight() / 127f, grow / 127f);
	}

	private static int shade(int hot, int cold, float slice, float phase) {
		return Theme.lerp(hot, cold, (float) (0.5 + 0.5 * Math.sin((slice * 0.8f + phase) * Math.PI * 2.0)));
	}

	private static float ramp(float progress, float slice, float lag) {
		return Math.max(0f, Math.min(1f, (progress - slice * lag) / (1f - lag)));
	}

	private static void quad(VertexConsumer buffer, Matrix4f matrix, float x, GlyphPage.Glyph glyph,
							 float atlas, int left, int right, float gone, float born, float spread) {
		float w = glyph.getWidth();
		float h = glyph.getHeight();

		float u0 = glyph.getX() / atlas;
		float v0 = glyph.getY() / atlas;
		float u1 = (glyph.getX() + w) / atlas;
		float v1 = (glyph.getY() + h) / atlas;

		corner(buffer, matrix, x, h, u0, v0, left, glyph, gone, born, spread);
		corner(buffer, matrix, x, 0f, u0, v1, left, glyph, gone, born, spread);
		corner(buffer, matrix, x + w, 0f, u1, v1, right, glyph, gone, born, spread);
		corner(buffer, matrix, x + w, h, u1, v0, right, glyph, gone, born, spread);
	}

	private static void corner(VertexConsumer buffer, Matrix4f matrix, float px, float py, float u, float v,
							   int color, GlyphPage.Glyph glyph, float gone, float born, float spread) {
		buffer.vertex(matrix, px, py, 0f)
				.color(color)
				.texture(u, v)
				.overlay(glyph.getX(), glyph.getY())
				.light(Math.round(gone * 100f), Math.round(born * 100f))
				.normal(glyph.getWidth() / 127f, glyph.getHeight() / 127f, spread / 127f);
	}

	private static RenderLayer layer(Identifier texture) {
		PipelineInitializer.init();
		return layers.computeIfAbsent(texture, id -> RenderLayerAccessor.invokeOf("system_world_text",
				RenderSetup.builder(PipelineRegistry.LYRICS_PIPELINE)
						.texture("Sampler0", id)
						.translucent()
						.expectedBufferSize(4096)
						.build()));
	}

	private static RenderLayer comic(Identifier texture) {
		PipelineInitializer.init();
		return comics.computeIfAbsent(texture, id -> RenderLayerAccessor.invokeOf("system_world_comic",
				RenderSetup.builder(PipelineRegistry.COMIC_PIPELINE)
						.texture("Sampler0", id)
						.translucent()
						.expectedBufferSize(4096)
						.build()));
	}

	private WorldFont() {
	}
}
