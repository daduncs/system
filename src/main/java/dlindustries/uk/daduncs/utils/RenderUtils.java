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

package dlindustries.uk.daduncs.utils;
import dlindustries.uk.daduncs.mixin.RenderLayerAccessor;
import dlindustries.uk.daduncs.render.renderers.pipeline.PipelineInitializer;
import dlindustries.uk.daduncs.render.renderers.pipeline.PipelineRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.function.Consumer;

import static dlindustries.uk.daduncs.System.mc;

public final class RenderUtils {
	public static boolean rendering3D = true;
	private static RenderLayer lines;
	private static int projectionDepth;

	public static Vec3d getCameraPos() {
		return mc.gameRenderer.getCamera().getCameraPos();
	}

	public static float tickProgress() {
		return mc.getRenderTickCounter().getTickProgress(true);
	}

	public static float frameDelta() {
		return mc.getRenderTickCounter().getDynamicDeltaTicks();
	}

	public static double deltaTime() {
		return mc.getCurrentFps() > 0 ? (1.0000 / mc.getCurrentFps()) : 1;
	}

	public static float fast(float end, float start, float multiple) {
		return (1 - MathHelper.clamp((float) (deltaTime() * multiple), 0, 1)) * end + MathHelper.clamp((float) (deltaTime() * multiple), 0, 1) * start;
	}

	public static Vec3d getPlayerLookVec(PlayerEntity player) {
		float f = 0.017453292F;
		float pi = 3.1415927F;
		float f1 = MathHelper.cos(-player.getYaw() * f - pi);
		float f2 = MathHelper.sin(-player.getYaw() * f - pi);
		float f3 = -MathHelper.cos(-player.getPitch() * f);
		float f4 = MathHelper.sin(-player.getPitch() * f);
		return (new Vec3d((f2 * f3), f4, (f1 * f3))).normalize();
	}

	public static void resetProjection() {
		projectionDepth = 0;
	}

	public static void unscaledProjection(DrawContext context) {
		if (projectionDepth++ == 0) {
			float scaleFactor = (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
			context.getMatrices().pushMatrix();
			context.getMatrices().scale(1.0F / scaleFactor, 1.0F / scaleFactor);
		}
		rendering3D = false;
	}

	public static void scaledProjection(DrawContext context) {
		if (projectionDepth == 0) {
			rendering3D = true;
			return;
		}

		if (--projectionDepth == 0) {
			context.getMatrices().popMatrix();
		}
		rendering3D = true;
	}

	public static void drawLayer(RenderLayer layer, Consumer<VertexConsumer> runner) {
		int bufferSize = Math.max(256, Math.min(layer.getExpectedBufferSize(), 8192));
		try (BufferAllocator allocator = new BufferAllocator(bufferSize)) {
			VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
			runner.accept(immediate.getBuffer(layer));
			immediate.draw(layer);
		}
	}

	public static void emitLine(VertexConsumer buffer, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float red, float green, float blue, float alpha, float lineWidth) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float dz = z2 - z1;
		float length = Math.max(0.0001F, MathHelper.sqrt(dx * dx + dy * dy + dz * dz));
		float nx = dx / length;
		float ny = dy / length;
		float nz = dz / length;

		buffer.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha).normal(nx, ny, nz).lineWidth(lineWidth);
		buffer.vertex(matrix, x2, y2, z2).color(red, green, blue, alpha).normal(nx, ny, nz).lineWidth(lineWidth);
	}

	private static void addQuad(VertexConsumer buffer, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float red, float green, float blue, float alpha) {
		buffer.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha);
		buffer.vertex(matrix, x2, y2, z2).color(red, green, blue, alpha);
		buffer.vertex(matrix, x3, y3, z3).color(red, green, blue, alpha);
		buffer.vertex(matrix, x4, y4, z4).color(red, green, blue, alpha);
	}

	private static void addQuad(VertexConsumer buffer, Matrix3x2fc matrix, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, float red, float green, float blue, float alpha) {
		buffer.vertex(matrix, x1, y1).color(red, green, blue, alpha);
		buffer.vertex(matrix, x2, y2).color(red, green, blue, alpha);
		buffer.vertex(matrix, x3, y3).color(red, green, blue, alpha);
		buffer.vertex(matrix, x4, y4).color(red, green, blue, alpha);
	}

	public static void renderRoundedQuad(DrawContext context, Color color, double x, double y, double x2, double y2, double corner1, double corner2, double corner3, double corner4, double samples) {
		int left = (int) Math.floor(Math.min(x, x2));
		int top = (int) Math.floor(Math.min(y, y2));
		int right = (int) Math.ceil(Math.max(x, x2));
		int bottom = (int) Math.ceil(Math.max(y, y2));

		int width = right - left;
		int height = bottom - top;
		if (width <= 0 || height <= 0) {
			return;
		}

		int radiusTopLeft = clampRadius(corner1, width, height);
		int radiusTopRight = clampRadius(corner2, width, height);
		int radiusBottomLeft = clampRadius(corner3, width, height);
		int radiusBottomRight = clampRadius(corner4, width, height);
		int packedColor = color.getRGB();

		for (int row = 0; row < height; row++) {
			int leftInset = getLeftInsetForRow(row, height, radiusTopLeft, radiusBottomLeft);
			int rightInset = getRightInsetForRow(row, height, radiusTopRight, radiusBottomRight);
			fillRow(context, left + leftInset, right - rightInset, top + row, packedColor);
		}
	}

	public static void renderRoundedQuad(DrawContext context, Color color, double x, double y, double x1, double y1, double rad, double samples) {
		renderRoundedQuad(context, color, x, y, x1, y1, rad, rad, rad, rad, samples);
	}

	public static void renderRoundedOutlineInternal(Matrix3x2fc matrix, float cr, float cg, float cb, float ca, double fromX, double fromY, double toX, double toY, double radC1, double radC2, double radC3, double radC4, double width, double samples) {
		drawLayer(RenderLayers.debugQuads(), buffer -> {
			double[][] map = new double[][]{
					new double[]{toX - radC4, toY - radC4, radC4},
					new double[]{toX - radC2, fromY + radC2, radC2},
					new double[]{fromX + radC1, fromY + radC1, radC1},
					new double[]{fromX + radC3, toY - radC3, radC3}
			};

			for (int i = 0; i < 4; i++) {
				double[] current = map[i];
				double radius = current[2];
				double start = i * 90D;
				double end = start + 90D;
				double step = 90D / samples;

				for (double angle = start; angle < end; angle += step) {
					double nextAngle = Math.min(angle + step, end);

					float rad1 = (float) Math.toRadians(angle);
					float rad2 = (float) Math.toRadians(nextAngle);

					float sin1 = (float) Math.sin(rad1);
					float cos1 = (float) Math.cos(rad1);
					float sin2 = (float) Math.sin(rad2);
					float cos2 = (float) Math.cos(rad2);

					float innerX1 = (float) current[0] + sin1 * (float) radius;
					float innerY1 = (float) current[1] + cos1 * (float) radius;
					float innerX2 = (float) current[0] + sin2 * (float) radius;
					float innerY2 = (float) current[1] + cos2 * (float) radius;

					float outerRadius = (float) (radius + width);
					float outerX1 = (float) current[0] + sin1 * outerRadius;
					float outerY1 = (float) current[1] + cos1 * outerRadius;
					float outerX2 = (float) current[0] + sin2 * outerRadius;
					float outerY2 = (float) current[1] + cos2 * outerRadius;

					addQuad(buffer, matrix, innerX1, innerY1, outerX1, outerY1, outerX2, outerY2, innerX2, innerY2, cr, cg, cb, ca);
				}
			}
		});
	}

	public static void setScissorRegion(int x, int y, int width, int height) {
		Screen currentScreen = MinecraftClient.getInstance().currentScreen;
		int screenHeight;
		if (currentScreen == null)
			screenHeight = 0;
		else
			screenHeight = currentScreen.height - height;

		double scaleFactor = MinecraftClient.getInstance().getWindow().getScaleFactor();
		GL11.glScissor((int) (x * scaleFactor), (int) (screenHeight * scaleFactor), (int) ((width - x) * scaleFactor), (int) ((height - y) * scaleFactor));
		GL11.glEnable(GL11.GL_SCISSOR_TEST);
	}

	public static void renderCircle(DrawContext context, Color color, double originX, double originY, double radius, int segments) {
		int top = (int) Math.floor(originY - radius);
		int bottom = (int) Math.ceil(originY + radius);
		int packedColor = color.getRGB();

		for (int y = top; y < bottom; y++) {
			double distanceY = (y + 0.5D) - originY;
			double radiusSquared = radius * radius;
			double inner = radiusSquared - (distanceY * distanceY);
			if (inner <= 0.0D) {
				continue;
			}

			double distanceX = Math.sqrt(inner);
			int left = (int) Math.floor(originX - distanceX);
			int right = (int) Math.ceil(originX + distanceX);
			fillRow(context, left, right, y, packedColor);
		}
	}

	public static void renderShaderRect(Matrix3x2fStack matrixStack, Color color, Color color2, Color color3, Color color4, float f, float f2, float f3, float f4, float f5, float f6) {
		float alpha = color.getAlpha() / 255F;
		float red = color.getRed() / 255F;
		float green = color.getGreen() / 255F;
		float blue = color.getBlue() / 255F;

		drawLayer(RenderLayers.debugQuads(), buffer ->
				addQuad(buffer, matrixStack, f - 10, f2 - 10, f - 10, f2 + f4 + 20, f + f3 + 20, f2 + f4 + 20, f + f3 + 20, f2 - 10, red, green, blue, alpha));
	}

	public static void renderRoundedOutline(DrawContext poses, Color c, double fromX, double fromY, double toX, double toY, double rad1, double rad2, double rad3, double rad4, double width, double samples) {
		int left = (int) Math.floor(Math.min(fromX, toX));
		int top = (int) Math.floor(Math.min(fromY, toY));
		int right = (int) Math.ceil(Math.max(fromX, toX));
		int bottom = (int) Math.ceil(Math.max(fromY, toY));

		int outerWidth = right - left;
		int outerHeight = bottom - top;
		if (outerWidth <= 0 || outerHeight <= 0) {
			return;
		}

		int thickness = Math.max(1, (int) Math.round(width));
		if (thickness * 2 >= outerWidth || thickness * 2 >= outerHeight) {
			renderRoundedQuad(poses, c, fromX, fromY, toX, toY, rad1, rad2, rad3, rad4, samples);
			return;
		}

		int radiusTopLeft = clampRadius(rad1, outerWidth, outerHeight);
		int radiusTopRight = clampRadius(rad2, outerWidth, outerHeight);
		int radiusBottomLeft = clampRadius(rad3, outerWidth, outerHeight);
		int radiusBottomRight = clampRadius(rad4, outerWidth, outerHeight);

		int innerLeft = left + thickness;
		int innerTop = top + thickness;
		int innerRight = right - thickness;
		int innerBottom = bottom - thickness;
		int innerWidth = innerRight - innerLeft;
		int innerHeight = innerBottom - innerTop;

		int innerRadiusTopLeft = Math.max(0, radiusTopLeft - thickness);
		int innerRadiusTopRight = Math.max(0, radiusTopRight - thickness);
		int innerRadiusBottomLeft = Math.max(0, radiusBottomLeft - thickness);
		int innerRadiusBottomRight = Math.max(0, radiusBottomRight - thickness);
		int packedColor = c.getRGB();

		for (int row = 0; row < outerHeight; row++) {
			int outerLeft = left + getLeftInsetForRow(row, outerHeight, radiusTopLeft, radiusBottomLeft);
			int outerRight = right - getRightInsetForRow(row, outerHeight, radiusTopRight, radiusBottomRight);
			int y = top + row;

			if (y < innerTop || y >= innerBottom) {
				fillRow(poses, outerLeft, outerRight, y, packedColor);
				continue;
			}

			int innerRow = y - innerTop;
			int innerRowLeft = innerLeft + getLeftInsetForRow(innerRow, innerHeight, innerRadiusTopLeft, innerRadiusBottomLeft);
			int innerRowRight = innerRight - getRightInsetForRow(innerRow, innerHeight, innerRadiusTopRight, innerRadiusBottomRight);

			fillRow(poses, outerLeft, innerRowLeft, y, packedColor);
			fillRow(poses, innerRowRight, outerRight, y, packedColor);
		}
	}

	public static MatrixStack matrixFrom(double x, double y, double z) {
		MatrixStack matrices = new MatrixStack();

		Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));

		Vec3d cameraPos = camera.getCameraPos();
		matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);

		return matrices;
	}

	public static void renderQuad(Matrix3x2fStack matrices, float x, float y, float width, float height, int color) {
		float alpha = ((color >> 24) & 0xFF) / 255f;
		float red = ((color >> 16) & 0xFF) / 255f;
		float green = ((color >> 8) & 0xFF) / 255f;
		float blue = (color & 0xFF) / 255f;

		matrices.pushMatrix();
		matrices.scale(0.5f, 0.5f);
		matrices.translate(x, y);

		drawLayer(RenderLayers.debugQuads(), buffer ->
				addQuad(buffer, matrices, 0.0F, 0.0F, 0.0F, height, width, height, width, 0.0F, red, green, blue, alpha));

		matrices.popMatrix();
	}

	public static void renderRoundedQuadInternal(Matrix3x2fc matrix, float cr, float cg, float cb, float ca, double fromX, double fromY, double toX, double toY, double corner1, double corner2, double corner3, double corner4, double samples) {
		drawLayer(RenderLayers.debugTriangleFan(), buffer -> {
			buffer.vertex(matrix, (float) ((fromX + toX) / 2.0), (float) ((fromY + toY) / 2.0)).color(cr, cg, cb, ca);

			double[][] map = new double[][]{
					new double[]{toX - corner4, toY - corner4, corner4},
					new double[]{toX - corner2, fromY + corner2, corner2},
					new double[]{fromX + corner1, fromY + corner1, corner1},
					new double[]{fromX + corner3, toY - corner3, corner3}
			};

			for (int i = 0; i < 4; i++) {
				double[] current = map[i];
				double radius = current[2];
				double start = i * 90D;
				double end = start + 90D;
				double step = 90D / samples;

				for (double angle = start; angle <= end; angle += step) {
					float radians = (float) Math.toRadians(Math.min(angle, end));
					float sin = (float) (Math.sin(radians) * radius);
					float cos = (float) (Math.cos(radians) * radius);
					buffer.vertex(matrix, (float) current[0] + sin, (float) current[1] + cos).color(cr, cg, cb, ca);
				}
			}

			double[] current = map[0];
			float sin = 0.0F;
			float cos = (float) current[2];
			buffer.vertex(matrix, (float) current[0] + sin, (float) current[1] + cos).color(cr, cg, cb, ca);
		});
	}

	private static int clampRadius(double radius, int width, int height) {
		return Math.max(0, Math.min((int) Math.round(radius), Math.min(width, height) / 2));
	}

	private static int getLeftInsetForRow(int row, int height, int radiusTop, int radiusBottom) {
		int inset = 0;
		if (radiusTop > 0 && row < radiusTop) {
			inset = Math.max(inset, getCornerInset(radiusTop, row));
		}
		if (radiusBottom > 0 && row >= height - radiusBottom) {
			inset = Math.max(inset, getCornerInset(radiusBottom, height - 1 - row));
		}
		return inset;
	}

	private static int getRightInsetForRow(int row, int height, int radiusTop, int radiusBottom) {
		int inset = 0;
		if (radiusTop > 0 && row < radiusTop) {
			inset = Math.max(inset, getCornerInset(radiusTop, row));
		}
		if (radiusBottom > 0 && row >= height - radiusBottom) {
			inset = Math.max(inset, getCornerInset(radiusBottom, height - 1 - row));
		}
		return inset;
	}

	private static int getCornerInset(int radius, int offsetFromEdge) {
		double distance = radius - (offsetFromEdge + 0.5D);
		double inside = (radius * radius) - (distance * distance);
		if (inside <= 0.0D) {
			return radius;
		}

		return Math.max(0, (int) Math.ceil(radius - Math.sqrt(inside)));
	}

	private static void fillRow(DrawContext context, int left, int right, int y, int color) {
		if (right <= left) {
			return;
		}

		context.fill(left, y, right, y + 1, color);
	}

	public static void renderFilledBox(MatrixStack matrices, float f, float f2, float f3, float f4, float f5, float f6, Color color) {
		float red = color.getRed() / 255F;
		float green = color.getGreen() / 255F;
		float blue = color.getBlue() / 255F;
		float alpha = color.getAlpha() / 255F;
		Matrix4f matrix = matrices.peek().getPositionMatrix();

		GL11.glDepthFunc(GL11.GL_ALWAYS);
		drawLayer(RenderLayers.debugFilledBox(), buffer -> {
			addQuad(buffer, matrix, f, f2, f3, f4, f2, f3, f4, f2, f6, f, f2, f6, red, green, blue, alpha);
			addQuad(buffer, matrix, f, f5, f3, f, f5, f6, f4, f5, f6, f4, f5, f3, red, green, blue, alpha);
			addQuad(buffer, matrix, f, f2, f3, f, f5, f3, f4, f5, f3, f4, f2, f3, red, green, blue, alpha);
			addQuad(buffer, matrix, f, f2, f6, f4, f2, f6, f4, f5, f6, f, f5, f6, red, green, blue, alpha);
			addQuad(buffer, matrix, f, f2, f3, f, f2, f6, f, f5, f6, f, f5, f3, red, green, blue, alpha);
			addQuad(buffer, matrix, f4, f2, f3, f4, f5, f3, f4, f5, f6, f4, f2, f6, red, green, blue, alpha);
		});
		GL11.glDepthFunc(GL11.GL_LEQUAL);
	}

	public static void renderBoxOutline(MatrixStack matrices, Box box, Color color, float lineWidth) {
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		float red = color.getRed() / 255F;
		float green = color.getGreen() / 255F;
		float blue = color.getBlue() / 255F;
		float alpha = color.getAlpha() / 255F;

		drawLayer(lines(), buffer -> {
			float minX = (float) box.minX;
			float minY = (float) box.minY;
			float minZ = (float) box.minZ;
			float maxX = (float) box.maxX;
			float maxY = (float) box.maxY;
			float maxZ = (float) box.maxZ;

			emitLine(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, red, green, blue, alpha, lineWidth);
			emitLine(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, red, green, blue, alpha, lineWidth);
			emitLine(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, red, green, blue, alpha, lineWidth);
			emitLine(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, red, green, blue, alpha, lineWidth);

			emitLine(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, red, green, blue, alpha, lineWidth);
			emitLine(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, red, green, blue, alpha, lineWidth);
			emitLine(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, alpha, lineWidth);
			emitLine(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, red, green, blue, alpha, lineWidth);

			emitLine(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, red, green, blue, alpha, lineWidth);
			emitLine(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, red, green, blue, alpha, lineWidth);
			emitLine(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, red, green, blue, alpha, lineWidth);
			emitLine(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, red, green, blue, alpha, lineWidth);
		});
	}

	public static void renderLine(MatrixStack matrices, Color color, Vec3d start, Vec3d end) {
		renderLine(matrices, color, start, end, 1.0F);
	}

	public static void renderLine(MatrixStack matrices, Color color, Vec3d start, Vec3d end, float lineWidth) {
		matrices.push();
		Matrix4f matrix = matrices.peek().getPositionMatrix();

		float red = color.getRed() / 255f;
		float green = color.getGreen() / 255f;
		float blue = color.getBlue() / 255f;
		float alpha = color.getAlpha() / 255f;

		drawLayer(lines(), buffer -> emitLine(
				buffer,
				matrix,
				(float) start.x,
				(float) start.y,
				(float) start.z,
				(float) end.x,
				(float) end.y,
				(float) end.z,
				red,
				green,
				blue,
				alpha,
				lineWidth
		));

		matrices.pop();
	}

	private static RenderLayer lines() {
		if (lines == null) {
			PipelineInitializer.init();
			lines = RenderLayerAccessor.invokeOf("system_lines", RenderSetup.builder(PipelineRegistry.LINES_PIPELINE).build());
		}

		return lines;
	}

}
