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
import dlindustries.uk.daduncs.gui.HudEditor;
import dlindustries.uk.daduncs.gui.Theme;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.render.Render2D;
import dlindustries.uk.daduncs.utils.EncryptedString;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

public final class InventoryHud extends Module implements HudListener {

	public InventoryHud() {
		super(EncryptedString.of("Inventory Hud"),
				EncryptedString.of("Shows what you have in your inventory"),
				-1,
				Category.RENDER);
		addSettings(outline, counts, size, fading, fadeSize, fadeGradient);
		HudEditor.register(this);
	}

	private final bool outline = new bool(EncryptedString.of("Outline"), false);
	private final number size = new number(EncryptedString.of("Size"), 50, 200, 100, 5);
	private final bool counts = new bool(EncryptedString.of("Counts"), true);
	private final bool fading = new bool(EncryptedString.of("Fade"), false);
	private final number fadeSize = new number(EncryptedString.of("Fade Size"), 4, 40, 18, 1).setVisibility(fading::getValue);
	private final bool fadeGradient = new bool(EncryptedString.of("Fade Gradient"), true).setVisibility(fading::getValue);

	private static final Identifier EMPTY = Identifier.of("system", "textures/gui/x.png");
	private static final int BUBBLE = 20;
	private static final int GAP = 2;
	private static final float ROUND = 6f;

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
		if (mc.player == null) return;

		DrawContext context = event.context;
		int x = HudEditor.x(this, 8);
		int y = HudEditor.y(this, 200);
		int step = BUBBLE + GAP;

		HudEditor.push(context, size.getValueFloat() / 100f, x, y);

		HudEditor.bounds(this, x, y, step * 9 - GAP, step * 3 - GAP);

		for (int slot = 9; slot < 36; slot++) {
			ItemStack stack = mc.player.getInventory().getStack(slot);

			int bubbleX = x + (slot - 9) % 9 * step;
			int bubbleY = y + (slot - 9) / 9 * step;

			bubble(context, bubbleX, bubbleY);

			if (stack.isEmpty()) {
				Render2D.drawRoundImage(context, bubbleX + 6, bubbleY + 6, 8, 8, 0f, EMPTY, Theme.fade(Theme.text, 0.22f));
				continue;
			}

			context.drawItem(stack, bubbleX + 2, bubbleY + 2);

			if (counts.getValue()) {
				context.drawStackOverlay(mc.textRenderer, stack, bubbleX + 2, bubbleY + 2);
			}
		}

		HudEditor.pop(context);
	}

	private void bubble(DrawContext context, int x, int y) {
		if (fading.getValue())
			Render2D.drawFade(context, x, y, BUBBLE, BUBBLE, ROUND, fadeSize.getValueFloat(), fadeGradient.getValue(), 1f);

		if (ClickGUI.hudGlass.getValue()) {
			Render2D.drawBubble(context, x, y, BUBBLE, BUBBLE, ROUND, Theme.SHEEN, 7f, 0.35f, 0x66101010, 1f);
		} else {
			Render2D.drawGlassRoundRect(context, x, y, BUBBLE, BUBBLE, ROUND, Theme.SHEEN, Theme.BLUR, Theme.surface, 1f);
		}

		if (outline.getValue()) {
			Render2D.drawRoundOutline(context, x, y, BUBBLE, BUBBLE, ROUND, 1, Theme.accent);
		}
	}
}
