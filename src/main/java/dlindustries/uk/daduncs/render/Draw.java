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

package dlindustries.uk.daduncs.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import dlindustries.uk.daduncs.render.renderers.pipeline.PipelineRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;

public final class Draw {

	private static SimpleFramebuffer scene;
	private static SimpleFramebuffer behind;
	private static boolean copied;
	private static boolean asked;

	private static boolean warmed;

	public static void warmup(DrawContext context) {
		if (warmed)
			return;

		warmed = true;

		dlindustries.uk.daduncs.render.renderers.pipeline.PipelineInitializer.init();
		dlindustries.uk.daduncs.font.Fonts.preload();

		MinecraftClient client = MinecraftClient.getInstance();
		var main = client.getFramebuffer();

		if (main.getColorAttachmentView() != null && scene == null)
			scene = new SimpleFramebuffer("system_scene", main.textureWidth, main.textureHeight, false);

		float[] radii = {1f, 1f, 1f, 1f};

		rect(context, 0f, 0f, 1f, 1f, radii, 0);
		border(context, 0f, 0f, 1f, 1f, radii, 1f, 0);
	}

	public static void rect(DrawContext context, float x, float y, float w, float h, float[] radii, int color) {
		submit(context, PipelineRegistry.RECTANGLE_PIPELINE, TextureSetup.empty(), x, y, w, h, even(radii), color, 0f);
	}

	public static void gradient(DrawContext context, float x, float y, float w, float h, float[] radii, int left, int right) {
		submit(context, PipelineRegistry.RECTANGLE_PIPELINE, TextureSetup.empty(), x, y, w, h, even(radii), left, left, right, right, 0f);
	}

	public static void picker(DrawContext context, float x, float y, float w, float h, float round, float hue, int mode) {
		submit(context, PipelineRegistry.PICKER_PIPELINE, TextureSetup.empty(), x, y, w, h,
				new float[]{round, mode, 0f, 0f}, 0xFFFFFFFF, hue * 100f);
	}

	public static void logo(DrawContext context, float x, float y, float w, float h, Identifier texture, int color) {
		GpuTextureView view = MinecraftClient.getInstance().getTextureManager().getTexture(texture).getGlTextureView();
		TextureSetup setup = TextureSetup.of(view, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
		float time = (java.lang.System.currentTimeMillis() % 600000L) / 1000f;

		submit(context, PipelineRegistry.LOGO_PIPELINE, setup, x, y, w, h, new float[]{time, 0f, 0f, 0f}, color, 0f);
	}

	public static void border(DrawContext context, float x, float y, float w, float h, float[] radii, float thickness, int color) {
		submit(context, PipelineRegistry.BORDER_PIPELINE, TextureSetup.empty(), x, y, w, h, even(radii), color, thickness);
	}

	public static void image(DrawContext context, float x, float y, float w, float h, float[] radii, Identifier texture, int color) {
		GpuTextureView view = MinecraftClient.getInstance().getTextureManager().getTexture(texture).getGlTextureView();
		TextureSetup setup = TextureSetup.of(view, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));

		submit(context, PipelineRegistry.ROUND_IMAGE_PIPELINE, setup, x, y, w, h, even(radii), color, blocky());
	}

	private static float[] even(float[] radii) {
		if (radii[0] == radii[1] && radii[0] == radii[2] && radii[0] == radii[3])
			return new float[]{radii[0], radii[0], -1f, -1f};

		return radii;
	}

	private static float blocky() {
		return dlindustries.uk.daduncs.module.modules.client.ClickGUI.modern.getValue() ? 0f : 12f;
	}

	public static void head(DrawContext context, float x, float y, float w, float h,
							float u, float v, float uw, float vh, float round, Identifier texture, int color) {
		GpuTextureView view = MinecraftClient.getInstance().getTextureManager().getTexture(texture).getGlTextureView();
		TextureSetup setup = TextureSetup.of(view, RenderSystem.getSamplerCache().get(FilterMode.NEAREST));

		submit(context, PipelineRegistry.HEAD_PIPELINE, setup, x, y, w, h, new float[]{u, v, uw, vh}, color, round);
	}

	public static void captureScene() {
		MinecraftClient client = MinecraftClient.getInstance();
		var main = client.getFramebuffer();

		if (main.getColorAttachmentView() == null)
			return;

		if (scene == null)
			scene = new SimpleFramebuffer("system_scene", main.textureWidth, main.textureHeight, false);

		if (scene.textureWidth != main.textureWidth || scene.textureHeight != main.textureHeight)
			scene.resize(main.textureWidth, main.textureHeight);

		if (scene.getColorAttachmentView() == null)
			return;

		main.drawBlit(scene.getColorAttachmentView());
		copied = true;
	}

	public static void askBehind() {
		var main = MinecraftClient.getInstance().getFramebuffer();

		if (main.getColorAttachmentView() == null)
			return;

		if (behind == null)
			behind = new SimpleFramebuffer("system_behind", main.textureWidth, main.textureHeight, false);

		if (behind.textureWidth != main.textureWidth || behind.textureHeight != main.textureHeight)
			behind.resize(main.textureWidth, main.textureHeight);

		asked = behind.getColorAttachmentView() != null;
	}

	public static boolean takeBehind() {
		if (!asked)
			return false;

		asked = false;
		MinecraftClient.getInstance().getFramebuffer().drawBlit(behind.getColorAttachmentView());
		return true;
	}

	public static void blur(DrawContext context, float x, float y, float w, float h, float round, float radius, int color, float opacity) {
		float shown = Math.max(0f, Math.min(1f, opacity));

		if (!copied || scene == null || scene.getColorAttachmentView() == null) {
			rect(context, x, y, w, h, new float[]{round, round, round, round}, dim(color, shown));
			return;
		}

		TextureSetup setup = TextureSetup.of(scene.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
		submit(context, PipelineRegistry.BLUR_PIPELINE, setup, x, y, w, h, new float[]{round, round, round, shown * 100f}, color, radius);
	}

	public static void glass(DrawContext context, float x, float y, float w, float h, float round, float radius, float sheen, int color, float opacity) {
		float shown = Math.max(0f, Math.min(1f, opacity));

		if (!copied || scene == null || scene.getColorAttachmentView() == null) {
			rect(context, x, y, w, h, new float[]{round, round, round, round}, dim(color, shown));
			return;
		}

		TextureSetup setup = TextureSetup.of(scene.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
		submit(context, PipelineRegistry.GLASS_PIPELINE, setup, x, y, w, h,
				new float[]{round, round, sheen * 100f, shown * 100f}, color, radius);
	}

	public static void glyphGlow(DrawContext context, float x, float y, float w, float h,
								 float glyphX, float glyphY, float spread, Identifier texture, int color, float opacity) {
		GpuTextureView view = MinecraftClient.getInstance().getTextureManager().getTexture(texture).getGlTextureView();
		TextureSetup setup = TextureSetup.of(view, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));

		submit(context, PipelineRegistry.TEXT_GLOW_PIPELINE, setup, x, y, w, h,
				new float[]{glyphX, glyphY, Math.max(0f, Math.min(1f, opacity)) * 100f, 0f}, color, spread);
	}

	public static void bubble(DrawContext context, float x, float y, float w, float h, float round, float radius, float sheen, float lens, int color, float opacity, boolean overGui) {
		float shown = Math.max(0f, Math.min(1f, opacity));
		boolean gui = overGui && asked;
		SimpleFramebuffer source = gui ? behind : scene;

		if (!gui && !copied || source == null || source.getColorAttachmentView() == null) {
			rect(context, x, y, w, h, new float[]{round, round, round, round}, dim(color, shown));
			return;
		}

		TextureSetup setup = TextureSetup.of(source.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
		submit(context, PipelineRegistry.BUBBLE_PIPELINE, setup, x, y, w, h,
				new float[]{round, Math.max(0f, lens) * 100f, sheen * 100f, shown * 100f}, color, radius);
	}

	public static void stairFill(DrawContext context, float x, float y, float w, float h,
								 float above, float below, float round, float radius, int color, float opacity) {
		float shown = Math.max(0f, Math.min(1f, opacity));
		float[] packed = {above, below, round, shown * 100f};

		if (!copied || scene == null || scene.getColorAttachmentView() == null) {
			submit(context, PipelineRegistry.STAIR_PIPELINE, TextureSetup.empty(), x, y, w, h, packed, dim(color, shown), 0f);
			return;
		}

		TextureSetup setup = TextureSetup.of(scene.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
		submit(context, PipelineRegistry.STAIR_BLUR_PIPELINE, setup, x, y, w, h, packed, color, radius);
	}

	public static void stairGlass(DrawContext context, float x, float y, float w, float h,
								  float above, float below, float round, float radius, int color, float opacity) {
		float shown = Math.max(0f, Math.min(1f, opacity));
		float[] packed = {above, below, round, shown * 100f};

		if (!copied || scene == null || scene.getColorAttachmentView() == null) {
			submit(context, PipelineRegistry.STAIR_PIPELINE, TextureSetup.empty(), x, y, w, h, packed, dim(color, shown), 0f);
			return;
		}

		TextureSetup setup = TextureSetup.of(scene.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
		submit(context, PipelineRegistry.STAIR_GLASS_PIPELINE, setup, x, y, w, h, packed, color, radius);
	}

	private static int dim(int color, float opacity) {
		int alpha = (int) (((color >>> 24) & 0xFF) * opacity);
		return (alpha << 24) | (color & 0xFFFFFF);
	}

	public static void stairBorder(DrawContext context, float x, float y, float w, float h,
								   float above, float below, float round, float thickness, int top, int bottom) {
		float grow = Math.max(0f, thickness - 1f);

		submit(context, PipelineRegistry.STAIR_BORDER_PIPELINE, TextureSetup.empty(), x - grow, y - grow, w + grow * 2f, h + grow * 2f,
				new float[]{above, below, round, grow}, top, bottom, bottom, top, thickness);
	}

	private static void submit(DrawContext context, RenderPipeline pipeline, TextureSetup setup,
							   float x, float y, float w, float h, float[] radii, int color, float extra) {
		submit(context, pipeline, setup, x, y, w, h, radii, color, color, color, color, extra);
	}

	private static void submit(DrawContext context, RenderPipeline pipeline, TextureSetup setup, float x, float y, float w, float h,
							   float[] radii, int topLeft, int bottomLeft, int bottomRight, int topRight, float extra) {
		if (pipeline == null || w <= 0f || h <= 0f)
			return;

		Matrix3x2f matrix = new Matrix3x2f(context.getMatrices());
		float margin = 1f / (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
		ScreenRect bounds = bounds(context, matrix, x - margin, y - margin, w + margin * 2, h + margin * 2);

		if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0)
			return;

		float u = margin / w;
		float v = margin / h;
		float x0 = x - margin, y0 = y - margin, x1 = x + w + margin, y1 = y + h + margin;

		context.state.addSimpleElement(new GuiState(pipeline, setup, context, bounds, buffer -> {
			corner(buffer, matrix, x0, y0, -u, -v, w, h, radii, topLeft, extra);
			corner(buffer, matrix, x0, y1, -u, 1f + v, w, h, radii, bottomLeft, extra);
			corner(buffer, matrix, x1, y1, 1f + u, 1f + v, w, h, radii, bottomRight, extra);
			corner(buffer, matrix, x1, y0, 1f + u, -v, w, h, radii, topRight, extra);
		}));
	}

	public static void shape(DrawContext context, float x, float y, float w, float h, float radius, float thickness, int color) {
		if (PipelineRegistry.SHAPE_PIPELINE == null || w <= 0f || h <= 0f || (color >>> 24) == 0)
			return;

		float margin = 1f / (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
		Matrix3x2f matrix = new Matrix3x2f(context.getMatrices());
		ScreenRect bounds = bounds(context, matrix, x - margin, y - margin, w + margin * 2, h + margin * 2);
		if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0)
			return;

		int width = fixed(w), height = fixed(h), round = fixed(radius), ring = fixed(thickness);
		float left = x - margin, top = y - margin, right = x + w + margin, bottom = y + h + margin;

		context.state.addSimpleElement(new GuiState(PipelineRegistry.SHAPE_PIPELINE, TextureSetup.empty(), context, bounds, buffer -> {
			shapeVertex(buffer, matrix, left, top, -margin, -margin, width, height, round, ring, color);
			shapeVertex(buffer, matrix, left, bottom, -margin, h + margin, width, height, round, ring, color);
			shapeVertex(buffer, matrix, right, bottom, w + margin, h + margin, width, height, round, ring, color);
			shapeVertex(buffer, matrix, right, top, w + margin, -margin, width, height, round, ring, color);
		}));
	}

	private static void shapeVertex(VertexConsumer buffer, Matrix3x2f matrix, float px, float py, float localX, float localY,
									int width, int height, int round, int ring, int color) {
		float tx = matrix.m00() * px + matrix.m10() * py + matrix.m20();
		float ty = matrix.m01() * px + matrix.m11() * py + matrix.m21();

		buffer.vertex(tx, ty, 0f)
				.color(color)
				.texture(localX, localY)
				.overlay(width, height)
				.light(round, ring)
				.normal(0f, 0f, 0f);
	}

	public static void frost(DrawContext context, float x, float y, float w, float h, float radius, float spread, int color, float opacity) {
		float shown = Math.max(0f, Math.min(1f, opacity));

		if (!copied || scene == null || scene.getColorAttachmentView() == null) {
			shape(context, x, y, w, h, radius, 0f, dim(color, shown));
			return;
		}

		if (PipelineRegistry.FROST_PIPELINE == null || w <= 0f || h <= 0f)
			return;

		float margin = 1f / (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
		Matrix3x2f matrix = new Matrix3x2f(context.getMatrices());
		ScreenRect bounds = bounds(context, matrix, x - margin, y - margin, w + margin * 2, h + margin * 2);
		if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0)
			return;

		int width = fixed(w), height = fixed(h), round = fixed(radius), fade = Math.round(shown * 1000f);
		float reach = Math.min(spread, 127f) / 127f;
		float left = x - margin, top = y - margin, right = x + w + margin, bottom = y + h + margin;
		TextureSetup setup = TextureSetup.of(scene.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));

		context.state.addSimpleElement(new GuiState(PipelineRegistry.FROST_PIPELINE, setup, context, bounds, buffer -> {
			frostVertex(buffer, matrix, left, top, -margin, -margin, width, height, round, fade, reach, color);
			frostVertex(buffer, matrix, left, bottom, -margin, h + margin, width, height, round, fade, reach, color);
			frostVertex(buffer, matrix, right, bottom, w + margin, h + margin, width, height, round, fade, reach, color);
			frostVertex(buffer, matrix, right, top, w + margin, -margin, width, height, round, fade, reach, color);
		}));
	}

	private static void frostVertex(VertexConsumer buffer, Matrix3x2f matrix, float px, float py, float localX, float localY,
									int width, int height, int round, int fade, float reach, int color) {
		float tx = matrix.m00() * px + matrix.m10() * py + matrix.m20();
		float ty = matrix.m01() * px + matrix.m11() * py + matrix.m21();

		buffer.vertex(tx, ty, 0f)
				.color(color)
				.texture(localX, localY)
				.overlay(width, height)
				.light(round, fade)
				.normal(0f, 0f, reach);
	}

	private static int fixed(float value) {
		return Math.max(0, Math.min(32000, Math.round(value * 16f)));
	}

	public static void line(DrawContext context, float[] points, float[] widths, int color) {
		int count = points.length / 2;
		if (PipelineRegistry.LINE_PIPELINE == null || count < 2)
			return;

		float margin = 1f / (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
		float[] normals = new float[points.length];
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, widest = 0;

		for (int i = 0; i < count; i++) {
			int prev = Math.max(i - 1, 0);
			int next = Math.min(i + 1, count - 1);
			float dx = points[next * 2] - points[prev * 2];
			float dy = points[next * 2 + 1] - points[prev * 2 + 1];
			float length = (float) Math.sqrt(dx * dx + dy * dy);
			if (length < 1.0E-4f) {
				dx = 1;
				dy = 0;
				length = 1;
			}
			normals[i * 2] = -dy / length;
			normals[i * 2 + 1] = dx / length;

			minX = Math.min(minX, points[i * 2]);
			minY = Math.min(minY, points[i * 2 + 1]);
			maxX = Math.max(maxX, points[i * 2]);
			maxY = Math.max(maxY, points[i * 2 + 1]);
			widest = Math.max(widest, widths[i]);
		}

		float pad = widest + margin;
		Matrix3x2f matrix = new Matrix3x2f(context.getMatrices());
		ScreenRect bounds = bounds(context, matrix, minX - pad, minY - pad, maxX - minX + pad * 2, maxY - minY + pad * 2);
		if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0)
			return;

		context.state.addSimpleElement(new GuiState(PipelineRegistry.LINE_PIPELINE, TextureSetup.empty(), context, bounds, buffer -> {
			for (int i = 0; i < count - 1; i++) {
				int j = i + 1;
				float reachI = widths[i] + margin;
				float reachJ = widths[j] + margin;
				float ax = points[i * 2], ay = points[i * 2 + 1];
				float bx = points[j * 2], by = points[j * 2 + 1];

				stroke(buffer, matrix, ax + normals[i * 2] * reachI, ay + normals[i * 2 + 1] * reachI, reachI, widths[i], color);
				stroke(buffer, matrix, ax - normals[i * 2] * reachI, ay - normals[i * 2 + 1] * reachI, -reachI, widths[i], color);
				stroke(buffer, matrix, bx - normals[j * 2] * reachJ, by - normals[j * 2 + 1] * reachJ, -reachJ, widths[j], color);
				stroke(buffer, matrix, bx + normals[j * 2] * reachJ, by + normals[j * 2 + 1] * reachJ, reachJ, widths[j], color);
			}
		}));
	}

	private static void stroke(VertexConsumer buffer, Matrix3x2f matrix, float px, float py, float across, float half, int color) {
		float tx = matrix.m00() * px + matrix.m10() * py + matrix.m20();
		float ty = matrix.m01() * px + matrix.m11() * py + matrix.m21();

		buffer.vertex(tx, ty, 0f)
				.color(color)
				.texture(across, half)
				.overlay(0, 0)
				.light(0, 0)
				.normal(0f, 0f, 0f);
	}

	private static void corner(VertexConsumer buffer, Matrix3x2f matrix, float px, float py, float u, float v,
							   float w, float h, float[] radii, int color, float extra) {
		float tx = matrix.m00() * px + matrix.m10() * py + matrix.m20();
		float ty = matrix.m01() * px + matrix.m11() * py + matrix.m21();

		buffer.vertex(tx, ty, 0f)
				.color(color)
				.texture(u, v)
				.overlay(fixed(w), fixed(h))
				.light(fixed(radii[0]), fixed(radii[1]))
				.normal(radii[2] / 127f, radii[3] / 127f, extra / 127f);
	}

	public static ScreenRect bounds(DrawContext context, Matrix3x2f matrix, float x, float y, float w, float h) {
		ScreenRect rect = new ScreenRect((int) Math.floor(x), (int) Math.floor(y),
				(int) Math.ceil(w) + 1, (int) Math.ceil(h) + 1).transformEachVertex(matrix);

		ScreenRect scissor = context.scissorStack.peekLast();
		if (scissor == null)
			return rect;

		ScreenRect clipped = scissor.intersection(rect);
		if (clipped == null || clipped.width() <= 0 || clipped.height() <= 0)
			return null;

		return clipped;
	}

	private Draw() {
	}
}
