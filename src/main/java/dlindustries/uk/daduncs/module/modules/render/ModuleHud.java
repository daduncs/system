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

package dlindustries.uk.daduncs.module.modules.render;

import dlindustries.uk.daduncs.System;
import dlindustries.uk.daduncs.event.events.HudListener;
import dlindustries.uk.daduncs.font.Fonts;
import dlindustries.uk.daduncs.font.GlyphPageFontRenderer;
import dlindustries.uk.daduncs.gui.Anim;
import dlindustries.uk.daduncs.gui.Frame;
import dlindustries.uk.daduncs.gui.HudEditor;
import dlindustries.uk.daduncs.gui.Theme;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.mode;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.render.Render2D;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModuleHud extends Module implements HudListener {

	public enum Face {
		Default, Chakra, Rajdhani, Titillium, Michroma, Poppins
	}

	private static final float ROUND = 3f;
	private static final int SNAP = 3;

	private final mode<Face> font = new mode<>(EncryptedString.of("Font"), Face.Default, Face.class);
	private final bool gradient = new bool(EncryptedString.of("Gradient"), true);
	private final bool outline = new bool(EncryptedString.of("Outline"), false);
	private final number thickness = new number(EncryptedString.of("Outline Thickness"), 1, 5, 1, 1).setVisibility(outline::getValue);
	private final bool outlineGradient = new bool(EncryptedString.of("Outline Gradient"), true).setVisibility(outline::getValue);
	private final bool glow = new bool(EncryptedString.of("Glow"), false);
	private final number glowSize = new number(EncryptedString.of("Glow Size"), 4, 60, 30, 1).setVisibility(glow::getValue);
	private final bool glowGradient = new bool(EncryptedString.of("Glow Gradient"), true).setVisibility(glow::getValue);
	private final number padding = new number(EncryptedString.of("Padding Y"), 0, 10, 4, 1);
	private final number paddingX = new number(EncryptedString.of("Padding X"), 0, 10, 4, 1);
	private final number speed = new number(EncryptedString.of("Speed"), 1, 20, 9, 1);
	private final number size = new number(EncryptedString.of("Size"), 50, 200, 100, 5);

	private final Map<Module, Anim> slide = new HashMap<>();
	private final List<Module> showing = new ArrayList<>();

	private float drift;

	public ModuleHud() {
		super(EncryptedString.of("Module Hud"),
				EncryptedString.of("Lists what you have on"),
				-1,
				Category.RENDER);
		addSettings(font, gradient, outline, thickness, outlineGradient, glow, glowSize, glowGradient, padding, paddingX, speed, size);
		HudEditor.register(this);
	}

	@Override
	public void onEnable() {
		eventManager.add(HudListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(HudListener.class, this);
		slide.clear();
		showing.clear();
		super.onDisable();
	}

	@Override
	public void onRenderHud(HudEvent event) {
		DrawContext context = event.context;
		float delta = mc.getRenderTickCounter().getDynamicDeltaTicks() / 20f;

		sync();

		int row = 10 + padding.getValueInt();

		drift = (drift + Frame.delta() * (float) speed.getValue() * 0.02f) % 1f;

		int base = HudEditor.x(this, 2);
		int top = HudEditor.y(this, 2);

		HudEditor.push(context, size.getValueFloat() / 100f, base, top);

		List<Module> visible = new ArrayList<>();
		List<float[]> shape = new ArrayList<>();

		float offset = top;
		int previous = 0;
		for (Module module : showing) {
			Anim anim = slide.get(module);
			anim.update(delta);

			float shown = anim.get();
			if (shown <= 0.01f)
				continue;

			int width = nameWidth(module) + paddingX.getValueInt() * 2;
			if (previous > 0 && previous - width < SNAP)
				width = previous;
			previous = width;

			float tall = row * shown;

			visible.add(module);
			shape.add(new float[]{base - (width + 3) * (1f - shown), offset, width, shown, tall});

			offset += tall;
		}

		if (visible.isEmpty()) {
			HudEditor.bounds(this, base, top, 40, row);
			HudEditor.pop(context);
			return;
		}

		int widest = 0;
		for (float[] slot : shape)
			widest = Math.max(widest, (int) slot[2]);

		boolean mirror = base + widest / 2 > mc.getWindow().getScaledWidth() / 2;
		float axis = base + widest / 2f;

		List<float[]> runs = new ArrayList<>();

		int start = 0;
		while (start < visible.size()) {
			int end = start;
			while (end + 1 < visible.size() && allign(shape.get(start), shape.get(end + 1)))
				end++;

			float[] run = shape.get(start);
			runs.add(new float[]{run[0], run[1], run[2], shape.get(end)[1] + shape.get(end)[4] - run[1], run[3]});

			start = end + 1;
		}

		Matrix3x2fStack matrices = context.getMatrices();

		if (mirror) {
			matrices.pushMatrix();
			matrices.translate(axis, 0f);
			matrices.scale(-1f, 1f);
			matrices.translate(-axis, 0f);
		}

		if (glow.getValue()) {
			float margin = glowSize.getValueFloat() * 1.5f;
			float left = base - margin;
			float wide = widest + margin * 2f;
			float high = offset - top + margin * 2f;

			for (float[] slot : shape) {
				float x0 = (slot[0] - margin - left) / wide;
				float x1 = (slot[0] + slot[2] + margin - left) / wide;
				float y0 = (slot[1] - top) / high;
				float y1 = (slot[1] + slot[4] - top + margin * 2f) / high;

				Render2D.drawFade(context, slot[0], slot[1], slot[2], slot[4], 0f, glowSize.getValueFloat(),
						Theme.fade(tint(x0, y0), slot[3]), Theme.fade(tint(x0, y1), slot[3]),
						Theme.fade(tint(x1, y1), slot[3]), Theme.fade(tint(x1, y0), slot[3]), 120f);
			}
		}

		for (int i = 0; i < runs.size(); i++) {
			float[] run = runs.get(i);
			float above = i > 0 ? runs.get(i - 1)[2] : 0f;
			float below = i < runs.size() - 1 ? runs.get(i + 1)[2] : 0f;

			if (ClickGUI.hudGlass.getValue())
				Render2D.drawStairGlass(context, run[0], run[1], run[2], run[3], above, below, ROUND, 26f, 0x59101010, run[4]);
			else Render2D.drawStair(context, run[0], run[1], run[2], run[3], above, below, ROUND,
					Theme.BLUR, Theme.surface, run[4]);
		}

		if (outline.getValue())
			for (int i = 0; i < runs.size(); i++) {
				float[] run = runs.get(i);
				float above = i > 0 ? runs.get(i - 1)[2] : 0f;
				float below = i < runs.size() - 1 ? runs.get(i + 1)[2] : 0f;

				int upper = outlineGradient.getValue() ? shade((run[1] - top) / (offset - top)) : Theme.accent;
				int lower = outlineGradient.getValue() ? shade((run[1] + run[3] - top) / (offset - top)) : Theme.accent;

				Render2D.drawStairOutline(context, run[0], run[1], run[2], run[3], above, below, ROUND,
						thickness.getValueFloat(), Theme.fade(upper, run[4]), Theme.fade(lower, run[4]));
			}

		if (mirror)
			matrices.popMatrix();

		matrices.pushMatrix();
		matrices.translate(0f, -1.5f);

		for (int i = 0; i < visible.size(); i++) {
			float[] slot = shape.get(i);

			if (slot[3] < 0.6f)
				continue;

			float span = visible.size() > 1 ? (float) i / (visible.size() - 1) : 0f;
			int colour = gradient.getValue() ? shade(span) : Theme.text;

			float left = mirror ? 2f * axis - slot[0] - slot[2] : slot[0];

			drawName(visible.get(i), context, (int) left + paddingX.getValueInt(), (int) (slot[1] + row) - 7,
					Theme.fade(colour, (slot[3] - 0.6f) / 0.4f));
		}

		matrices.popMatrix();

		HudEditor.bounds(this, base, top, widest, Math.max(row, (int) (offset - top)));
		HudEditor.pop(context);
	}

	private GlyphPageFontRenderer face() {
		return switch (font.getMode()) {
			case Default -> ClickGUI.modern.getValue() ? Fonts.SMALL : null;
			case Chakra -> Fonts.CHAKRA;
			case Rajdhani -> Fonts.RAJDHANI;
			case Titillium -> Fonts.TITILLIUM;
			case Michroma -> Fonts.MICHROMA;
			case Poppins -> Fonts.POPPINS;
		};
	}

	private int nameWidth(Module module) {
		GlyphPageFontRenderer face = face();

		return face == null ? TextRenderer.smallWidth(module.getName()) : Math.round(face.getInkWidth(module.getName()));
	}

	private void drawName(Module module, DrawContext context, int x, int y, int color) {
		GlyphPageFontRenderer face = face();

		if (face == null) {
			TextRenderer.drawSmall(module.getName(), context, x, y, color);
			return;
		}

		face.drawString(context, module.getName(), x - face.getInkLeft(module.getName()), y - 4 + (Fonts.SMALL.getFontHeight() - face.getFontHeight()) / 2f, color);
	}

	private int tint(float across, float down) {
		return glowGradient.getValue() ? shade(across * 0.6f + down * 0.4f) : Theme.accent;
	}

	private int shade(float span) {
		float wave = (span * 0.7f + drift) % 1f;

		return Theme.lerp(Theme.accent, Theme.flowEnd(), wave < 0.5f ? wave * 2f : (1f - wave) * 2f);
	}

	private static boolean allign(float[] run, float[] row) {
		return Math.abs(run[0] - row[0]) < 0.5f && Math.abs(run[2] - row[2]) < 0.5f;
	}

	private void sync() {
		List<Module> enabled = System.INSTANCE.getModuleManager().getEnabledModules();

		for (Module module : enabled)
			if (!slide.containsKey(module)) {
				slide.put(module, new Anim(0f, (float) speed.getValue()));
				showing.add(module);
			}

		for (Module module : showing)
			slide.get(module).to(enabled.contains(module) ? 1f : 0f);

		showing.removeIf(module -> {
			Anim anim = slide.get(module);
			boolean gone = !enabled.contains(module) && anim.get() <= 0.01f;

			if (gone)
				slide.remove(module);

			return gone;
		});

		showing.sort(Comparator.comparingInt(this::nameWidth).reversed());
	}
}
