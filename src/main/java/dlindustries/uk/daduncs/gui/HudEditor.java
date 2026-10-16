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

import dlindustries.uk.daduncs.System;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.render.Render2D;
import dlindustries.uk.daduncs.utils.Sounds;
import dlindustries.uk.daduncs.utils.TextRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HudEditor {

	private static final Identifier SHRINK = Identifier.of("system", "textures/gui/hud.png");

	private static final int WIDTH = 168;
	private static final int ROW = 20;

	private static final List<Module> registered = new ArrayList<>();
	private static final Map<Module, int[]> spots = new HashMap<>();
	private static final Map<Module, int[]> boxes = new HashMap<>();
	private static final Map<Module, Anim> knobs = new HashMap<>();

	private static final Anim panel = new Anim(0f, 11f, 28f);

	private static Module dragging;
	private static int grabX;
	private static int grabY;

	private static float scale = 1f;

	private static boolean open;
	private static int panelX;
	private static int panelY;
	private static int lastX;

	public static int mouseX() {
		return lastX;
	}

	public static boolean editing() {
		return MinecraftClient.getInstance().currentScreen instanceof ChatScreen;
	}

	public static int x(Module module, int fallback) {
		return spots.computeIfAbsent(module, m -> new int[]{fallback, fallback})[0];
	}

	public static int y(Module module, int fallback) {
		return spots.computeIfAbsent(module, m -> new int[]{fallback, fallback})[1];
	}

	public static boolean placed(Module module) {
		return spots.containsKey(module);
	}

	public static void place(Module module, int x, int y) {
		spots.put(module, new int[]{x, y});
	}

	public static void bounds(Module module, int x, int y, int width, int height) {
		boxes.put(module, new int[]{x, y, Math.round(width * scale), Math.round(height * scale)});
	}

	public static void push(DrawContext context, float size, int x, int y) {
		scale = size;

		Matrix3x2fStack matrices = context.getMatrices();

		matrices.pushMatrix();
		matrices.translate(x, y);
		matrices.scale(size, size);
		matrices.translate(-x, -y);
	}

	public static void pop(DrawContext context) {
		context.getMatrices().popMatrix();
		scale = 1f;
	}

	public static void register(Module module) {
		if (!registered.contains(module))
			registered.add(module);
	}

	private static List<Module> huds() {
		return registered;
	}

	private static Module at(int mouseX, int mouseY) {
		for (Module module : huds()) {
			int[] box = boxes.get(module);
			if (box == null || !module.isEnabled())
				continue;

			if (mouseX >= box[0] && mouseX <= box[0] + box[2] && mouseY >= box[1] && mouseY <= box[1] + box[3])
				return module;
		}

		return null;
	}

	public static void render(DrawContext context, int mouseX, int mouseY, float delta) {
		lastX = mouseX;

		if (!editing()) {
			open = false;
			dragging = null;
			panel.snap(0f);
			return;
		}

		if (dragging != null) {
			if (!held()) {
				if (System.INSTANCE.getConfigManager() != null) {
					System.INSTANCE.getConfigManager().save();
				}
				System.INSTANCE.getProfileManager().saveProfile();
				dragging = null;
			} else {
				int[] spot = spots.get(dragging);
				spot[0] = mouseX - grabX;
				spot[1] = mouseY - grabY;
			}
		}

		boxes.keySet().removeIf(module -> !module.isEnabled());
		if (dragging != null && !dragging.isEnabled())
			dragging = null;

		Module hovered = dragging != null ? dragging : at(mouseX, mouseY);

		if (hovered != null) {
			int[] box = boxes.get(hovered);
			int label = TextRenderer.smallWidth(hovered.getName()) + 5;

			Render2D.drawRoundOutline(context, box[0] - 3, box[1] - 3, box[2] + 6, box[3] + 6, 2, 1, Theme.accent);
			Render2D.drawRoundRect(context, box[0] - 3, box[1] - 15, label, 13, new float[]{2, 0, 2, 2}, Theme.accent);
			TextRenderer.drawSmall(hovered.getName(), context, box[0] - 1, box[1] - 8, 0xFF141414);
		}

		panel.to(open ? 1f : 0f);
		panel.update(delta);

		if (panel.get() > 0.01f)
			renderPanel(context, mouseX, mouseY, delta);
	}

	private static void renderPanel(DrawContext context, int mouseX, int mouseY, float delta) {
		List<Module> huds = huds();

		float shown = panel.overshoot();
		int full = 27 + huds.size() * ROW;
		int width = Math.round(WIDTH * shown);
		int height = Math.round(full * shown);

		Render2D.drawRoundRect(context, panelX, panelY, width, height, 10, 0xF01C1C1C);

		if (panel.get() < 0.55f)
			return;

		float fade = Math.min(1f, (panel.get() - 0.55f) / 0.45f);

		TextRenderer.drawSmallBold("HUD modules", context, panelX + 11, panelY + 13, Theme.fade(Theme.accent, fade));
		Render2D.drawRoundImage(context, panelX + WIDTH - 25, panelY + 6, 14, 14, 0f, SHRINK, Theme.fade(Theme.accent, fade));
		Render2D.drawRoundRect(context, panelX + 11, panelY + 22, WIDTH - 22, 1, 0.5f, Theme.fade(Theme.divider, fade));

		int row = panelY + 25;
		for (Module module : huds) {
			Anim knob = knobs.computeIfAbsent(module, m -> new Anim(m.isEnabled() ? 1f : 0f, 9f));
			knob.to(module.isEnabled() ? 1f : 0f);
			knob.update(delta);

			Render2D.drawRoundRect(context, panelX + 11, row + 4, 11, 11, 5.5f, Theme.fade(Theme.off, fade));

			float dot = 8f * knob.overshoot();
			if (dot > 0.2f)
				Render2D.drawRoundRect(context, panelX + 16.5f - dot / 2f, row + 9.5f - dot / 2f, dot, dot, dot / 2f, Theme.fade(Theme.accent, fade));

			TextRenderer.drawSmall(module.getName(), context, panelX + 28, row + 9, Theme.fade(Theme.text, fade));

			row += ROW;
		}
	}

	public static boolean click(int mouseX, int mouseY, int button) {
		if (!editing())
			return false;

		if (open && panel.get() > 0.5f) {
			List<Module> huds = huds();
			int row = panelY + 25;

			for (Module module : huds) {
				if (mouseX >= panelX && mouseX <= panelX + WIDTH && mouseY >= row && mouseY <= row + ROW) {
					module.toggle();
					Sounds.play(Sounds.TOGGLE);
					return true;
				}

				row += ROW;
			}
		}

		if (button == 1) {
			open = !open;
			panelX = mouseX;
			panelY = mouseY;
			Sounds.play(Sounds.COLLAPSE);
			return true;
		}

		if (open) {
			open = false;
			Sounds.play(Sounds.COLLAPSE);
			return true;
		}

		if (button == 0) {
			Module target = at(mouseX, mouseY);

			if (target != null) {
				if (target.claim(mouseX, mouseY))
					return true;

				int[] spot = spots.get(target);
				int[] box = boxes.get(target);
				dragging = target;
				grabX = mouseX - (spot != null ? spot[0] : box[0]);
				grabY = mouseY - (spot != null ? spot[1] : box[1]);
				Sounds.play(Sounds.CLICK);
				return true;
			}
		}

		return false;
	}

	public static boolean escape() {
		if (!open)
			return false;

		open = false;
		Sounds.play(Sounds.COLLAPSE);
		return true;
	}

	public static boolean held() {
		long window = MinecraftClient.getInstance().getWindow().getHandle();
		return org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
	}

	private HudEditor() {
	}
}
