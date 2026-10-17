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
import dlindustries.uk.daduncs.gui.Anim;
import dlindustries.uk.daduncs.gui.HudEditor;
import dlindustries.uk.daduncs.gui.Theme;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.render.Render2D;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EffectsHud extends Module implements HudListener {

	private static final int WIDTH = 136;
	private static final int ROW = 17;

	private static final Identifier POT = Identifier.of("system", "textures/gui/pot.png");

	private final bool outline = new bool(EncryptedString.of("Outline"), false);
	private final number size = new number(EncryptedString.of("Size"), 50, 200, 100, 5);
	private final bool fading = new bool(EncryptedString.of("Fade"), false);
	private final number fadeSize = new number(EncryptedString.of("Fade Size"), 4, 40, 18, 1).setVisibility(fading::getValue);
	private final bool fadeGradient = new bool(EncryptedString.of("Fade Gradient"), true).setVisibility(fading::getValue);

	private final Anim show = new Anim(0f, 7f);
	private final Map<RegistryEntry<StatusEffect>, Anim> rows = new HashMap<>();
	private final Map<RegistryEntry<StatusEffect>, StatusEffectInstance> last = new HashMap<>();
	private final bool hideAmbient = new bool(EncryptedString.of("Hide Beacon"), true);

	public EffectsHud() {
		super(EncryptedString.of("Effects Hud"),
				EncryptedString.of("Shows what potions you have"),
				-1,
				Category.RENDER);
		addSettings(hideAmbient, outline, size, fading, fadeSize, fadeGradient);
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
		rows.clear();
		last.clear();
		super.onDisable();
	}

	@Override
	public void onRenderHud(HudEvent event) {
		if (mc.player == null)
			return;

		sync();

		DrawContext context = event.context;
		float delta = mc.getRenderTickCounter().getDynamicDeltaTicks() / 20f;

		show.to(rows.isEmpty() && !HudEditor.editing() ? 0f : 1f);
		show.update(delta);

		if (show.get() <= 0.06f)
			return;

		List<RegistryEntry<StatusEffect>> order = new ArrayList<>(rows.keySet());
		order.sort(Comparator.comparingInt((RegistryEntry<StatusEffect> type) -> last.get(type).getDuration()).reversed());

		float fade = show.get();
		float stack = 0;
		int width = Math.max(WIDTH, 9 + TextRenderer.smallBoldWidth(EncryptedString.of("Effects")) + 12 + 13 + 9);

		for (RegistryEntry<StatusEffect> type : order) {
			Anim grow = rows.get(type);
			grow.update(delta);
			stack += ROW * grow.get();

			String name = type.value().getName().getString().toLowerCase();
			width = Math.max(width, 25 + TextRenderer.smallWidth(name) + 10 + TextRenderer.smallWidth(format(last.get(type).getDuration())) + 9);
		}

		int x = HudEditor.x(this, 8);
		int y = HudEditor.y(this, 8);
		int height = Math.round((25 + stack) * (0.9f + 0.1f * show.overshoot()));

		HudEditor.push(context, size.getValueFloat() / 100f, x, y);
		HudEditor.bounds(this, x, y, width, height);

		if (fading.getValue())
			Render2D.drawFade(context, x, y, width, height, 12, fadeSize.getValueFloat(), fadeGradient.getValue(), fade);

		if (ClickGUI.hudGlass.getValue())
			Render2D.drawBubble(context, x, y, width, height, 12, Theme.SHEEN, 7f, 0.35f, 0x66101010, fade);
		else Render2D.drawGlassRoundRect(context, x, y, width, height, 12, Theme.SHEEN, Theme.BLUR, Theme.surface, fade);

		if (outline.getValue())
			Render2D.drawRoundOutline(context, x, y, width, height, 12, 1, Theme.fade(Theme.accent, fade));

		TextRenderer.drawSmallBold(EncryptedString.of("Effects"), context, x + 9, y + 11, Theme.fade(Theme.text, fade));
		Render2D.drawRoundImage(context, x + width - 22, y + 5, 13, 13, 0f, POT, Theme.fade(Theme.accent, fade));
		Render2D.drawRoundRect(context, x + 9, y + 20, width - 18, 1, 0.5f, Theme.fade(Theme.divider, fade));

		context.enableScissor(x, y + 21, x + width, y + height);

		float row = y + 23;
		for (RegistryEntry<StatusEffect> type : order) {
			float open = rows.get(type).get();
			float alpha = fade * open;
			int top = Math.round(row);
			String name = type.value().getName().getString().toLowerCase();
			String time = format(last.get(type).getDuration());

			context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, InGameHud.getEffectTexture(type), x + 9, top + 3, 13, 13, Theme.fade(0xFFFFFFFF, alpha));
			TextRenderer.drawSmall(name, context, x + 25, top + 8, Theme.fade(Theme.text, alpha));
			TextRenderer.drawSmall(time, context, x + width - 9 - TextRenderer.smallWidth(time), top + 8, Theme.fade(Theme.accent, alpha));

			row += ROW * open;
		}

		context.disableScissor();
		HudEditor.pop(context);
	}

	private void sync() {
		List<RegistryEntry<StatusEffect>> active = new ArrayList<>();

		for (StatusEffectInstance effect : mc.player.getStatusEffects()) {
			if (hideAmbient.getValue() && effect.isAmbient())
				continue;

			active.add(effect.getEffectType());
			last.put(effect.getEffectType(), effect);
			rows.computeIfAbsent(effect.getEffectType(), type -> new Anim(0f, 11f)).to(1f);
		}

		rows.forEach((type, grow) -> {
			if (!active.contains(type))
				grow.to(0f);
		});

		rows.entrySet().removeIf(entry -> entry.getValue().target() == 0f && entry.getValue().get() <= 0.01f);
		last.keySet().retainAll(rows.keySet());
	}

	private static String format(int ticks) {
		int seconds = ticks / 20;
		return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
	}
}
