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
import dlindustries.uk.daduncs.gui.Anim;
import dlindustries.uk.daduncs.gui.HudEditor;
import dlindustries.uk.daduncs.gui.Theme;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.setting.Setting;
import dlindustries.uk.daduncs.module.setting.bind;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.render.Render2D;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class HotkeysHud extends Module implements HudListener {

	private static final int WIDTH = 136;
	private static final int ROW = 17;

	private static final Identifier KEYBOARD = Identifier.of("system", "textures/gui/hotkeyshud.png");

	private final bool outline = new bool(EncryptedString.of("Outline"), false);
	private final number size = new number(EncryptedString.of("Size"), 50, 200, 100, 5);
	private final bool fading = new bool(EncryptedString.of("Fade"), false);
	private final number fadeSize = new number(EncryptedString.of("Fade Size"), 4, 40, 18, 1).setVisibility(fading::getValue);
	private final bool fadeGradient = new bool(EncryptedString.of("Fade Gradient"), true).setVisibility(fading::getValue);

	private final Anim show = new Anim(0f, 7f);

	public HotkeysHud() {
		super(EncryptedString.of("Hotkeys Hud"),
				EncryptedString.of("Shows your binds"),
				-1,
				Category.RENDER);
		addSettings(outline, size, fading, fadeSize, fadeGradient);
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
		show.snap(0f);
		super.onDisable();
	}

	private static Identifier icon(Category category) {
		return Identifier.of("system", "textures/gui/" + switch (category) {
			case CLIENT -> "client";
			case CRYSTAL -> "crystal";
			case MACE -> "mace";
			case MISC -> "optimizer";
			case CART -> "cart";
			case RENDER -> "render";
			case SPEAR -> "spear";
			case SWORD -> "sword";
		} + ".png");
	}

	private static bind keyOf(Module module) {
		for (Setting<?> setting : module.getSettings())
			if (setting instanceof bind value && value.isModuleKey())
				return value;

		return null;
	}

	@Override
	public void onRenderHud(HudEvent event) {
		List<Module> bound = new ArrayList<>();
		for (Module module : System.INSTANCE.getModuleManager().getModules())
			if (module.getCategory() != Category.CLIENT && module.getKey() != GLFW.GLFW_KEY_UNKNOWN && module.getKey() != -1)
				bound.add(module);

		DrawContext context = event.context;
		float delta = mc.getRenderTickCounter().getDynamicDeltaTicks() / 20f;

		show.to(bound.isEmpty() && !HudEditor.editing() ? 0f : 1f);
		show.update(delta);

		if (show.get() <= 0.06f)
			return;

		float fade = show.get();
		int x = HudEditor.x(this, 8);
		int y = HudEditor.y(this, 120);

		HudEditor.push(context, size.getValueFloat() / 100f, x, y);

		int height = Math.round((25 + bound.size() * ROW) * (0.9f + 0.1f * show.overshoot()));
		int width = Math.max(WIDTH, 9 + TextRenderer.smallBoldWidth(EncryptedString.of("Hotkeys")) + 12 + 13 + 9);

		for (Module module : bound) {
			bind key = keyOf(module);
			String label = key == null ? "" : key.getKeyName();
			int needed = 25 + TextRenderer.smallWidth(module.getName()) + 10 + TextRenderer.smallWidth(label) + 9 + 9;

			width = Math.max(width, needed);
		}

		if (fading.getValue())
			Render2D.drawFade(context, x, y, width, height, 12, fadeSize.getValueFloat(), fadeGradient.getValue(), fade);

		if (ClickGUI.hudGlass.getValue())
			Render2D.drawBubble(context, x, y, width, height, 12, Theme.SHEEN, 7f, 0.35f, 0x66101010, fade);
		else Render2D.drawGlassRoundRect(context, x, y, width, height, 12, Theme.SHEEN, Theme.BLUR, Theme.surface, fade);

		if (outline.getValue())
			Render2D.drawRoundOutline(context, x, y, width, height, 12, 1, Theme.fade(Theme.accent, fade));
		HudEditor.bounds(this, x, y, width, height);

		TextRenderer.drawSmallBold(EncryptedString.of("Hotkeys"), context, x + 9, y + 10, Theme.fade(Theme.text, fade));
		Render2D.drawRoundImage(context, x + width - 22, y + 6, 13, 13, 0f, KEYBOARD, Theme.fade(Theme.accent, fade));
		Render2D.drawRoundRect(context, x + 9, y + 20, width - 18, 1, 0.5f, Theme.fade(Theme.divider, fade));

		int row = y + 23;
		for (Module module : bound) {
			bind key = keyOf(module);
			String label = key == null ? "" : key.getKeyName();

			int colour = Theme.fade(module.isEnabled() ? Theme.accent : Theme.text, fade);

			Render2D.drawRoundImage(context, x + 9, row + 3, 12, 12, 0f, icon(module.getCategory()), colour);
			TextRenderer.drawSmall(module.getName(), context, x + 25, row + 8, colour);

			int badge = TextRenderer.smallWidth(label) + 9;
			Render2D.drawRoundRect(context, x + width - 9 - badge, row + 3, badge, 13, 4, Theme.fade(colour, 0.16f * fade));
			TextRenderer.drawSmall(label, context, x + width - 5 - badge, row + 9, colour);

			row += ROW;
		}

		HudEditor.pop(context);
	}
}
