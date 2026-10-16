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

package dlindustries.uk.daduncs.gui;

import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.setting.Setting;
import dlindustries.uk.daduncs.module.setting.bind;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.colour;
import dlindustries.uk.daduncs.module.setting.mode;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.module.setting.range;
import dlindustries.uk.daduncs.module.setting.string;
import dlindustries.uk.daduncs.render.Render2D;
import dlindustries.uk.daduncs.utils.Sounds;
import dlindustries.uk.daduncs.utils.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

public final class Row {

	public static final int HEIGHT = 26;

	private static final int TRACK = 96;
	private static final int KNOB = 6;
	private static final int MIN_TRACK = 44;

	private final Setting<?> setting;
	private final Anim knob = new Anim(0f, 7f);
	private final Anim knobMax = new Anim(0f, 7f);
	private final Anim grab = new Anim(0f, 5f);
	private final Anim grabMax = new Anim(0f, 5f);
	private final Anim flip = new Anim(0f, 2.8f, 16f);
	private final Anim dot = new Anim(0f, 3.5f);

	private boolean dragging;
	private boolean draggingMax;
	private boolean focused;
	private boolean listening;
	private boolean hovered;
	private float alpha = 1f;

	public Row(Setting<?> setting) {
		this.setting = setting;

		if (setting instanceof bool value)
			flip.snap(value.getValue() ? 1f : 0f);

		if (setting instanceof mode<?>)
			dot.snap(1f);
	}

	public Setting<?> getSetting() {
		return setting;
	}

	public boolean isHovered() {
		return hovered;
	}

	public int height() {
		return HEIGHT;
	}

	public boolean focused() {
		return focused;
	}

	public boolean listening() {
		return listening;
	}

	private int track = TRACK;
	private int trackLeft;
	private int room;

	public void render(DrawContext context, int x, int y, int right, int mouseX, int mouseY, float delta, float alpha) {
		this.alpha = alpha;
		this.room = x + TextRenderer.lightWidth(setting.getName()) + 8;
		this.hovered = mouseX >= x && mouseX <= right && mouseY >= y && mouseY <= y + HEIGHT;
		knob.update(delta);
		knobMax.update(delta);
		grab.to(dragging && !draggingMax ? 1f : 0f);
		grabMax.to(dragging && draggingMax ? 1f : 0f);
		grab.update(delta);
		grabMax.update(delta);
		flip.update(delta);
		dot.update(delta);

		TextRenderer.drawLight(setting.getName(), context, x, y + 9, Theme.fade(Theme.text, alpha));

		if (setting.getTag() != null) {
			int badge = TextRenderer.tinyWidth(setting.getTag()) + 10;
			int badgeX = x + TextRenderer.lightWidth(setting.getName()) + 6;
			boolean tagHovered = mouseX >= badgeX && mouseX <= badgeX + badge && mouseY >= y + HEIGHT / 2f - 6.5f && mouseY <= y + HEIGHT / 2f + 6.5f;

			float bgAlpha = tagHovered ? 0.38f : 0.22f;
			Render2D.drawRoundRect(context, badgeX, y + HEIGHT / 2f - 6.5f, badge, 13, 6.5f, Theme.fade(Theme.accent, alpha * bgAlpha));
			TextRenderer.drawTiny(setting.getTag(), context, badgeX + 5, y + HEIGHT / 2 - 1, Theme.fade(Theme.accent, alpha));
		}

		if (setting instanceof number value)
			renderNumber(context, value, right, y);
		else if (setting instanceof bool value)
			renderBool(context, value, right, y);
		else if (setting instanceof string value)
			renderString(context, value, right, y);
		else if (setting instanceof mode<?> value)
			renderMode(context, value, right, y);
		else if (setting instanceof range value)
			renderRange(context, value, right, y);
		else if (setting instanceof bind value)
			renderBind(context, value, right, y);
		else if (setting instanceof colour value)
			renderColour(context, value, right, y);
	}

	private void renderColour(DrawContext context, colour value, int right, int y) {
		Render2D.drawRoundRect(context, right - 31, y + 5, 16, 16, 8, Theme.fade(value.getValue(), alpha));
		Render2D.drawRoundOutline(context, right - 31, y + 5, 16, 16, 8, 1f, Theme.fade(Theme.off, alpha));
	}

	private void drawFill(DrawContext context, float x, float y, float width, float from, float to) {
		if (!ClickGUI.sliderGradient.getValue()) {
			Render2D.drawRoundRect(context, x, y, width, 6, 3, Theme.fade(Theme.accent, alpha));
			return;
		}

		Render2D.drawGradientRect(context, x, y, width, 6, 3,
				Theme.fade(Theme.lerp(Theme.accent, Theme.secondary, from), alpha),
				Theme.fade(Theme.lerp(Theme.accent, Theme.secondary, to), alpha));
	}

	private void renderNumber(DrawContext context, number value, int right, int y) {
		fit(right - 34 - 10);
		int line = y + 11;

		knob.to(span(value.getValue(), value.getMin(), value.getMax()));

		Render2D.drawRoundRect(context, trackLeft, line, track, 4, 2, Theme.fade(Theme.off, alpha));
		drawFill(context, trackLeft, line - 1, track * knob.get() + KNOB / 2f, 0f, knob.get());

		float size = KNOB + grab.get() * 5f;
		Render2D.drawRoundRect(context, trackLeft + track * knob.get() - size / 2f, line + 2f - size / 2f, size, size, size / 2f, Theme.fade(Theme.knob(), alpha));

		String shown = format(value.getValue(), value.getIncrement());

		if (TextRenderer.lightWidth(shown) > 30)
			TextRenderer.drawSmall(shown, context, right - 34, y + 10, Theme.fade(Theme.text, alpha));
		else TextRenderer.drawLight(shown, context, right - 34, y + 9, Theme.fade(Theme.text, alpha));
	}

	private void fit(int end) {
		track = Math.max(MIN_TRACK, Math.min(TRACK, end - room));
		trackLeft = end - track;
	}

	private void renderBool(DrawContext context, bool value, int right, int y) {
		flip.to(value.getValue() ? 1f : 0f);

		Render2D.drawRoundRect(context, right - 31, y + 5, 16, 16, 8, Theme.fade(Theme.off, alpha));

		float dot = 12f * flip.overshoot();
		if (dot > 0.2f)
			Render2D.drawRoundRect(context, right - 23 - dot / 2f, y + 13 - dot / 2f, dot, dot, dot / 2f, Theme.fade(Theme.accent, alpha));
	}

	private void renderString(DrawContext context, string value, int right, int y) {
		String content = value.getValue() == null ? "" : value.getValue();
		int left = Math.max(right - 182, room);
		int wide = right - 6 - left;

		Render2D.drawRoundRect(context, left, y + 4, wide, 19, 6, Theme.fade(Theme.field, alpha));

		String shown = content;
		while (TextRenderer.lightWidth(shown) > wide - 16 && !shown.isEmpty())
			shown = shown.substring(1);

		TextRenderer.drawLight(shown, context, left + 6, y + 10, Theme.fade(Theme.accent, alpha));

		if (focused && blink())
			Render2D.drawRoundRect(context, left + 8 + TextRenderer.lightWidth(shown), y + 7, 1, 13, 0.5f, Theme.fade(Theme.accent, alpha));
	}

	private void renderMode(DrawContext context, mode<?> value, int right, int y) {
		String label = value.getMode().name().replace("_", " ");
		int count = value.getMode().getDeclaringClass().getEnumConstants().length;
		int pill = TextRenderer.smallBoldWidth(label) + count * 9 + 20;
		int left = right - pill;

		Render2D.drawRoundRect(context, left, y + 4, pill, 19, 9.5f, Theme.fade(Theme.field, alpha));

		dot.to(1f);

		for (int i = 0; i < count; i++)
			Render2D.drawRoundRect(context, left + 7 + i * 9, y + 10.5f, 6, 6, 3, Theme.fade(Theme.off, alpha));

		float size = 8f * dot.overshoot();

		Render2D.drawRoundRect(context, left + 10 + value.getModeIndex() * 9f - size / 2f, y + 13.5f - size / 2f,
				size, size, size / 2f, Theme.fade(Theme.accent, alpha));

		TextRenderer.drawSmallBold(label, context, left + 13 + count * 9, y + 11, Theme.fade(Theme.text, alpha));
	}

	private void renderRange(DrawContext context, range value, int right, int y) {
		fit(right - 78 - 6);
		int line = y + 11;

		knob.to(span(value.getMinValue(), value.getMin(), value.getMax()));
		knobMax.to(span(value.getMaxValue(), value.getMin(), value.getMax()));

		float low = track * knob.get();
		float high = track * knobMax.get();
		float lowSize = KNOB + grab.get() * 5f;
		float highSize = KNOB + grabMax.get() * 5f;

		Render2D.drawRoundRect(context, trackLeft, line, track, 4, 2, Theme.fade(Theme.off, alpha));
		drawFill(context, trackLeft + low - KNOB / 2f, line - 1, Math.max(0f, high - low) + KNOB, knob.get(), knobMax.get());
		Render2D.drawRoundRect(context, trackLeft + low - lowSize / 2f, line + 2f - lowSize / 2f, lowSize, lowSize, lowSize / 2f, Theme.fade(Theme.knob(), alpha));
		Render2D.drawRoundRect(context, trackLeft + high - highSize / 2f, line + 2f - highSize / 2f, highSize, highSize, highSize / 2f, Theme.fade(Theme.knob(), alpha));

		TextRenderer.drawLight(format(value.getMinValue(), value.getIncrement()) + " - " + format(value.getMaxValue(), value.getIncrement()),
				context, right - 78, y + 9, Theme.fade(Theme.text, alpha));
	}

	private void renderBind(DrawContext context, bind value, int right, int y) {
		String label = listening ? "..." : value.getKeyName();
		int badge = Math.max(34, TextRenderer.smallBoldWidth(label) + 16);
		int left = right - badge;

		Render2D.drawRoundRect(context, left, y + 4, badge, 19, 6, Theme.fade(Theme.field, alpha));
		TextRenderer.drawSmallBold(label, context, left + (badge - TextRenderer.smallBoldWidth(label)) / 2, y + 11,
				Theme.fade(listening ? Theme.accent : Theme.text, alpha));
	}

	public boolean click(int x, int y, int right, int mouseX, int mouseY, int button) {
		focused = false;
		listening = false;

		if (mouseX < x || mouseX > right || mouseY < y || mouseY > y + height())
			return false;

		if (button != 0)
			return false;

		if (setting instanceof colour value) {
			Picker.open(value, (x + right) / 2, y + HEIGHT / 2);
			Sounds.play(Sounds.CLICK);
			return true;
		}

		if (setting instanceof bool value) {
			value.toggle();
			Sounds.play(Sounds.TOGGLE);
			return true;
		}

		if (setting instanceof mode<?> value) {
			value.cycle();
			dot.snap(0f);

			Sounds.play(Sounds.CLICK);
			return true;
		}

		if (setting instanceof string) {
			focused = true;
			Sounds.play(Sounds.CLICK);
			return true;
		}

		if (setting instanceof bind) {
			listening = true;
			Sounds.play(Sounds.CLICK);
			return true;
		}

		if (setting instanceof number) {
			dragging = true;
			Sounds.play(Sounds.SCROLL, 0.5f);
			drag(right, mouseX, mouseY);
			return true;
		}

		if (setting instanceof range value) {
			float at = clamp((mouseX - trackLeft) / (float) track);

			float minSpan = span(value.getMinValue(), value.getMin(), value.getMax());
			float maxSpan = span(value.getMaxValue(), value.getMin(), value.getMax());

			if (minSpan >= maxSpan) {
				draggingMax = at >= maxSpan;
			} else {
				draggingMax = Math.abs(at - maxSpan) <= Math.abs(at - minSpan);
			}

			dragging = true;
			Sounds.play(Sounds.SCROLL, 0.5f);
			drag(right, mouseX, mouseY);
			return true;
		}

		return false;
	}

	public void drag(int right, int mouseX, int mouseY) {
		if (!dragging)
			return;

		double before = current();

		if (setting instanceof number value)
			value.setValue(value.getMin() + clamp((mouseX - trackLeft) / (float) track) * (value.getMax() - value.getMin()));

		if (setting instanceof range value) {
			double at = value.getMin() + clamp((mouseX - trackLeft) / (float) track) * (value.getMax() - value.getMin());

			if (draggingMax) {
				if (at < value.getMinValue()) {
					value.setMinValue(at);
				}
				value.setMaxValue(at);
			} else {
				if (at > value.getMaxValue()) {
					value.setMaxValue(at);
				}
				value.setMinValue(at);
			}
		}

		if (current() != before)
			Sounds.play(Sounds.SCROLL, 0.35f);
	}

	private double current() {
		if (setting instanceof number value)
			return value.getValue();

		if (setting instanceof range value)
			return draggingMax ? value.getMaxValue() : value.getMinValue();

		return 0;
	}

	public void release() {
		dragging = false;
	}

	public boolean type(String typed) {
		if (!focused || !(setting instanceof string value))
			return false;

		String content = value.getValue() == null ? "" : value.getValue();
		value.setValue(content + typed);
		Sounds.play(Sounds.KEYPRESS, 0.6f);
		return true;
	}

	public boolean backspace() {
		if (!focused || !(setting instanceof string value))
			return false;

		String content = value.getValue() == null ? "" : value.getValue();
		if (!content.isEmpty())
			value.setValue(content.substring(0, content.length() - 1));

		return true;
	}

	public boolean pressed(int keyCode) {
		if (!listening || !(setting instanceof bind value))
			return false;

		if (keyCode == GLFW.GLFW_KEY_ESCAPE)
			value.setKey(GLFW.GLFW_KEY_UNKNOWN);
		else if (!value.shouldIgnoreKey(keyCode))
			value.setKey(keyCode);

		listening = false;
		Sounds.play(Sounds.CLICK);
		return true;
	}

	public boolean mousePressed(int button) {
		if (!listening || !(setting instanceof bind value))
			return false;

		value.setKey(button);
		listening = false;
		Sounds.play(Sounds.CLICK);
		return true;
	}

	public static boolean blink() {
		return (java.lang.System.currentTimeMillis() / 500) % 2 == 0;
	}

	private static float span(double value, double min, double max) {
		return max - min == 0 ? 0f : clamp((float) ((value - min) / (max - min)));
	}

	private static float clamp(float value) {
		return Math.max(0f, Math.min(1f, value));
	}

	private static String format(double value, double increment) {
		if (increment >= 1 && value == Math.floor(value))
			return String.valueOf((int) value);

		return String.valueOf(Math.round(value * 100.0) / 100.0);
	}
}
