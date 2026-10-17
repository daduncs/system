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

import dlindustries.uk.daduncs.event.events.HudListener;
import dlindustries.uk.daduncs.gui.Frame;
import dlindustries.uk.daduncs.gui.HudEditor;
import dlindustries.uk.daduncs.gui.Theme;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.setting.mode;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.render.Render2D;
import dlindustries.uk.daduncs.utils.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public final class Watermark extends Module implements HudListener {

	private static final CharSequence LABEL = EncryptedString.of("SYSTEM");

	public enum Style {
		V1, V2
	}

	private final mode<Style> style = new mode<>(EncryptedString.of("Style"), Style.V2, Style.class);
	private final number size = new number(EncryptedString.of("Size"), 50, 200, 100, 5);
	private final number glow = new number(EncryptedString.of("Glow"), 0, 10, 10, 1);
	private final number speed = new number(EncryptedString.of("Speed"), 1, 20, 20, 1);
	private final bool fading = new bool(EncryptedString.of("Fade"), false);
	private final number fadeSize = new number(EncryptedString.of("Fade Size"), 4, 40, 18, 1).setVisibility(fading::getValue);
	private final bool fadeGradient = new bool(EncryptedString.of("Fade Gradient"), true).setVisibility(fading::getValue);
	private float drift;

	public Watermark() {
		super(EncryptedString.of("Watermark"),
				EncryptedString.of("Shows the client name"),
				-1,
				Category.RENDER);
		addSettings(style, size, glow, speed, fading, fadeSize, fadeGradient);
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
		super.onDisable();
	}

	@Override
	public void onRenderHud(HudEvent event) {
		DrawContext context = event.context;
		int x = HudEditor.x(this, 8);
		int y = HudEditor.y(this, 8);

		HudEditor.push(context, size.getValueFloat() / 100f, x, y);
		drift = (drift + Frame.delta() * (float) speed.getValue() * 0.02f) % 1f;

		if (style.isMode(Style.V2)) {
			renderPill(context, x, y);
			HudEditor.pop(context);
			return;
		}

		int width = TextRenderer.boldWidth(LABEL);
		int tall = TextRenderer.boldHeight();
		HudEditor.bounds(this, x, y + 7, width, tall);

		int cursor = x;
		for (int i = 0; i < LABEL.length(); i++) {
			String letter = String.valueOf(LABEL.charAt(i));
			float wave = ((float) i / LABEL.length() + drift) % 1f;
			float lit = wave < 0.5f ? wave * 2f : (1f - wave) * 2f;
			int colour = Theme.lerp(Theme.accent, Theme.flowEnd(), lit);

			float spread = glow.getValueFloat();
			if (spread > 0) TextRenderer.drawBoldGlow(letter, context, cursor, y + 15, Theme.fade(colour, 0.35f + 0.45f * lit), spread * 4f);

			TextRenderer.drawBold(letter, context, cursor, y + 15, colour);
			cursor += TextRenderer.boldWidth(letter);
		}

		HudEditor.pop(context);
	}

	private void renderPill(DrawContext context, int x, int y) {
		String fps = mc.getCurrentFps() + " fps";

		int name = TextRenderer.menuWidth(LABEL);
		int width = 8 + name + 6 + 1 + 6 + TextRenderer.smallWidth(fps) + 8;
		int tall = 20;

		HudEditor.bounds(this, x, y, width, tall);
		if (fading.getValue())
			Render2D.drawFade(context, x, y, width, tall, tall / 2f, fadeSize.getValueFloat(), fadeGradient.getValue(), 1f);

		if (ClickGUI.hudGlass.getValue()) {
			Render2D.drawBubble(context, x, y, width, tall, tall / 2f, Theme.SHEEN, 7f, 0.35f, 0x66101010, 1f);
		} else {
			Render2D.drawGlassRoundRect(context, x, y, width, tall, tall / 2f, Theme.SHEEN, Theme.BLUR, Theme.surface, 1f);
		}

		int cursor = x + 8;
		for (int i = 0; i < LABEL.length(); i++) {
			String letter = String.valueOf(LABEL.charAt(i));
			float wave = ((float) i / LABEL.length() + drift) % 1f;
			int colour = Theme.lerp(Theme.accent, Theme.flowEnd(), wave < 0.5f ? wave * 2f : (1f - wave) * 2f);

			TextRenderer.drawMenu(letter, context, cursor, y + tall / 2 - 2, colour);
			cursor += TextRenderer.menuWidth(letter);
		}

		Render2D.drawRoundRect(context, x + 8 + name + 6, y + 6, 1, 8, 0.5f, Theme.fade(Theme.divider, 0.8f));
		TextRenderer.drawSmall(fps, context, x + 8 + name + 6 + 1 + 6, y + tall / 2 - 1, Theme.muted);
	}
}
