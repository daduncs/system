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

package dlindustries.uk.daduncs.module.setting;

import dlindustries.uk.daduncs.gui.Theme;

public final class colour extends Setting<colour> {

	private float hue;
	private float saturation;
	private float brightness;
	private final int originalValue;

	public colour(CharSequence name, int value) {
		super(name);
		this.originalValue = value;
		setValue(value);
	}

	public int getOriginalValue() {
		return originalValue;
	}

	public float getHue() {
		return hue;
	}

	public float getSaturation() {
		return saturation;
	}

	public float getBrightness() {
		return brightness;
	}

	public void setHue(float value) {
		hue = Math.max(0f, Math.min(0.9999f, value));
	}

	public void setSaturation(float value) {
		saturation = clamp(value);
	}

	public void setBrightness(float value) {
		brightness = clamp(value);
	}

	public int getValue() {
		return Theme.synced(getPicked());
	}

	public int getPicked() {
		return pack(hue, saturation, brightness);
	}

	public void setValue(int value) {
		int red = (value >> 16) & 0xFF;
		int green = (value >> 8) & 0xFF;
		int blue = value & 0xFF;

		int top = Math.max(red, Math.max(green, blue));
		int low = Math.min(red, Math.min(green, blue));
		int span = top - low;

		brightness = top / 255f;
		saturation = top == 0 ? 0f : span / (float) top;

		if (span == 0) {
			hue = 0f;
			return;
		}

		float shade;

		if (top == red) shade = (green - blue) / (float) span;
		else if (top == green) shade = 2f + (blue - red) / (float) span;
		else shade = 4f + (red - green) / (float) span;

		hue = wrap(shade / 6f);
	}

	public static int pack(float hue, float saturation, float brightness) {
		float slice = wrap(hue) * 6f;
		int wedge = (int) slice;
		float step = slice - wedge;

		float top = clamp(brightness);
		float low = top * (1f - clamp(saturation));
		float fall = top * (1f - clamp(saturation) * step);
		float climb = top * (1f - clamp(saturation) * (1f - step));

		float red;
		float green;
		float blue;

		switch (wedge % 6) {
			case 0 -> { red = top; green = climb; blue = low; }
			case 1 -> { red = fall; green = top; blue = low; }
			case 2 -> { red = low; green = top; blue = climb; }
			case 3 -> { red = low; green = fall; blue = top; }
			case 4 -> { red = climb; green = low; blue = top; }
			default -> { red = top; green = low; blue = fall; }
		}

		return 0xFF000000 | (Math.round(red * 255f) << 16) | (Math.round(green * 255f) << 8) | Math.round(blue * 255f);
	}

	private static float wrap(float value) {
		float turn = value % 1f;

		return turn < 0f ? turn + 1f : turn;
	}

	private static float clamp(float value) {
		return Math.max(0f, Math.min(1f, value));
	}
}
