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

import dlindustries.uk.daduncs.gui.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public final class Render2D {

	private static float bend(float radius) {
		return dlindustries.uk.daduncs.module.modules.client.ClickGUI.modern.getValue() ? radius : 0f;
	}

	private static float[] all(float radius) {
		float r = bend(radius);
		return new float[]{r, r, r, r};
	}

	private static float[] some(float[] radii) {
		return new float[]{bend(radii[0]), bend(radii[1]), bend(radii[2]), bend(radii[3])};
	}

	public static void drawPicker(DrawContext context, float x, float y, float width, float height, float round, float hue, int mode) {
		Draw.picker(context, x, y, width, height, round, hue, mode);
	}

	public static void drawLogo(DrawContext context, float x, float y, float width, float height, Identifier texture, int color) {
		Draw.logo(context, x, y, width, height, texture, color);
	}

	public static void drawGradientRect(DrawContext context, float x, float y, float width, float height, float round, int left, int right) {
		Draw.gradient(context, x, y, width, height, all(round), left, right);
	}

	public static void drawRoundRect(DrawContext context, float x, float y, float width, float height, float round, int color) {
		Draw.rect(context, x, y, width, height, all(round), color);
	}

	public static void drawRoundRect(DrawContext context, float x, float y, float width, float height, float[] radii, int color) {
		Draw.rect(context, x, y, width, height, some(radii), color);
	}

	public static void drawRoundRectOut(DrawContext context, float x, float y, float width, float height, float round, int color, int outline) {
		Draw.rect(context, x - 1, y - 1, width + 2, height + 2, all(round + 1), outline);
		Draw.rect(context, x, y, width, height, all(round), color);
	}

	public static void drawRoundOutline(DrawContext context, float x, float y, float width, float height, float round, float thickness, int color) {
		Draw.border(context, x, y, width, height, all(round), thickness, color);
	}

	public static void drawBlurRoundRect(DrawContext context, float x, float y, float width, float height, float round, float smooth, float blur, int color) {
		Draw.blur(context, x, y, width, height, bend(round), blur, color, 1f);
	}

	public static void drawBlurRoundRect(DrawContext context, float x, float y, float width, float height, float round, float smooth, float blur, int color, float opacity) {
		Draw.blur(context, x, y, width, height, bend(round), blur, color, opacity);
	}

	public static void drawGlassRoundRect(DrawContext context, float x, float y, float width, float height, float round, float sheen, float blur, int color, float opacity) {
		Draw.glass(context, x, y, width, height, bend(round), blur, sheen, color, opacity);
	}

	public static void drawBubble(DrawContext context, float x, float y, float width, float height, float round, float sheen, float blur, float lens, int color, float opacity) {
		Draw.bubble(context, x, y, width, height, bend(round), blur, sheen, lens, color, opacity, false);
	}

	public static void drawGuiBubble(DrawContext context, float x, float y, float width, float height, float round, float sheen, float blur, float lens, int color, float opacity) {
		Draw.bubble(context, x, y, width, height, bend(round), blur, sheen, lens, color, opacity, true);
	}

	public static void drawStairGlass(DrawContext context, float x, float y, float width, float height,
									  float above, float below, float round, float blur, int color, float opacity) {
		Draw.stairGlass(context, x, y, width + round, height, above, below, bend(round), blur, color, opacity);
	}

	public static void drawStair(DrawContext context, float x, float y, float width, float height,
								 float above, float below, float round, float blur, int color, float opacity) {
		Draw.stairFill(context, x, y, width + round, height, above, below, bend(round), blur, color, opacity);
	}

	public static void drawFade(DrawContext context, float x, float y, float width, float height, float round, float spread, int left, int right) {
		Draw.fade(context, x, y, width, height, bend(round), spread, left, left, right, right, 60f);
	}

	public static void drawFade(DrawContext context, float x, float y, float width, float height, float round, float spread,
								int topLeft, int bottomLeft, int bottomRight, int topRight, float strength) {
		Draw.fade(context, x, y, width, height, bend(round), spread, topLeft, bottomLeft, bottomRight, topRight, strength);
	}

	public static void drawFade(DrawContext context, float x, float y, float width, float height, float round, float spread, boolean gradient, float alpha) {
		int left = Theme.fade(gradient ? Theme.flow(0f) : Theme.accent, alpha);
		int right = Theme.fade(gradient ? Theme.flow(1f) : Theme.accent, alpha);

		Draw.fade(context, x, y, width, height, bend(round), spread, left, left, right, right, 60f);
	}

	public static void drawStairOutline(DrawContext context, float x, float y, float width, float height,
										float above, float below, float round, float thickness, int color) {
		drawStairOutline(context, x, y, width, height, above, below, round, thickness, color, color);
	}

	public static void drawStairOutline(DrawContext context, float x, float y, float width, float height,
										float above, float below, float round, float thickness, int top, int bottom) {
		Draw.stairBorder(context, x, y, width + round, height, above, below, bend(round), thickness, top, bottom);
	}

	public static void drawHead(DrawContext context, float x, float y, float size, float round, Identifier skin, int color) {
		Draw.head(context, x, y, size, size, 8f, 8f, 8f, 8f, bend(round), skin, color);
		Draw.head(context, x, y, size, size, 40f, 8f, 8f, 8f, bend(round), skin, color);
	}

	public static void drawLine(DrawContext context, float[] points, float[] widths, int color) {
		Draw.line(context, points, widths, color);
	}

	public static void drawCircle(DrawContext context, float x, float y, float radius, int color) {
		Draw.rect(context, x - radius, y - radius, radius * 2f, radius * 2f, all(radius), color);
	}

	public static void drawRoundImage(DrawContext context, float x, float y, float width, float height, float round, Identifier texture) {
		Draw.image(context, x, y, width, height, all(round), texture, 0xFFFFFFFF);
	}

	public static void drawRoundImage(DrawContext context, float x, float y, float width, float height, float round, Identifier texture, int color) {
		Draw.image(context, x, y, width, height, all(round), texture, color);
	}

	private Render2D() {
	}
}
