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

package dlindustries.uk.daduncs.module.modules.client;

import dlindustries.uk.daduncs.System;
import dlindustries.uk.daduncs.gui.ClickGui;
import dlindustries.uk.daduncs.gui.muon.MuonGui;
import dlindustries.uk.daduncs.gui.Theme;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.colour;
import dlindustries.uk.daduncs.module.setting.mode;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.Sounds;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.sound.SoundEvent;
import org.lwjgl.glfw.GLFW;

public final class ClickGUI extends Module {
	public static final mode<Palette> theme = new mode<>(EncryptedString.of("Theme"), Palette.Discord, Palette.class);

	public static final bool modern = new bool(EncryptedString.of("Modern"), true)
			.setDescription(EncryptedString.of("Off falls back to vanilla fonts, square corners and item icons"));

	public static final bool hudGlass = new bool(EncryptedString.of("HudGlass"), false);

	public static final bool sidebarGlass = new bool(EncryptedString.of("SidebarGlass"), false);

	public static final bool animatedIcons = new bool(EncryptedString.of("Animated Icons"), false);

	public static final bool randomisedTiming = new bool(EncryptedString.of("Randomised Timing"), true)
			.setDescription(EncryptedString.of("prevents 1.21.2+ TICK_END exploit"));

	public static final int VARIANCE = 10;

	public static final mode<Ui> ui = new mode<>(EncryptedString.of("Ui"), Ui.System, Ui.class);

	public static final number guiSize = new number(EncryptedString.of("Gui Size"), 50, 150, 100, 5);

	public static final bool openCloseSounds = new bool(EncryptedString.of("Open Close Sounds"), true);

	public static final bool uiSounds = new bool(EncryptedString.of("Ui Sounds"), true);

	public static final colour yours = new colour(EncryptedString.of("Your Color"), 0xFF9B6DFF);

	public enum Ui {
		System, SystemDuo, Argon, Muon
	}

	public enum Palette {
			Discord, System, Goon, Blood,
		Signal_Blue, Royal_Iris, Lime, Dragonfruit, Mint, Ultra_Violet, Your_Color
	}

	public static void syncTheme() {
		switch (theme.getMode()) {
			case System -> {
				Theme.accent = 0xFF9B6DFF;
				Theme.secondary = 0xFFFFFFFF;
			}
			case Goon -> {
				Theme.accent = 0xFFEFEFEF;
				Theme.secondary = 0xFF8A8A8E;
			}
			case Blood -> {
				Theme.accent = 0xFFCE3540;
				Theme.secondary = 0xFF1A1A1A;
			}
			case Signal_Blue -> {
				Theme.accent = 0xFF0057FF;
				Theme.secondary = 0xFFF8F7F4;
			}
			case Royal_Iris -> {
				Theme.accent = 0xFFFFF275;
				Theme.secondary = 0xFF3A0CA3;
			}
			case Lime -> {
				Theme.accent = 0xFFB6FF2E;
				Theme.secondary = 0xFF23262F;
			}
			case Dragonfruit -> {
				Theme.accent = 0xFFFF4696;
				Theme.secondary = 0xFF1E1033;
			}
			case Mint -> {
				Theme.accent = 0xFF064E3B;
				Theme.secondary = 0xFFF8E7C9;
			}
			case Your_Color -> {
				Theme.accent = yours.getValue();
				Theme.secondary = 0xFFFFFFFF;
			}
			case Ultra_Violet -> {
				Theme.accent = 0xFF6A00F4;
				Theme.secondary = 0xFFFFD6A5;
			}
			default -> {
				Theme.accent = dlindustries.uk.daduncs.System.INSTANCE.getAccentManager().getAccent();
				Theme.secondary = 0xFFFFFFFF;
			}
		}
	}

	public ClickGUI() {
		super(EncryptedString.of("System"),
				EncryptedString.of("Settings for the client"),
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				Category.CLIENT);

		addSettings(ui, guiSize, openCloseSounds, uiSounds, theme, yours, modern, hudGlass, sidebarGlass, animatedIcons, randomisedTiming);
	}

	@Override
	public void onEnable() {
		if (mc == null || mc.world == null || mc.player == null || mc.currentScreen instanceof TitleScreen) {
			setEnabledStatus(false);
			return;
		}

		if (!ours(mc.currentScreen))
			System.INSTANCE.previousScreen = mc.currentScreen;

		Screen screen = screen();
		if (screen != null) {
			if (!ours(mc.currentScreen))
				uiSound(Sounds.OPEN);
			mc.setScreen(screen);
		} else if (mc.currentScreen instanceof InventoryScreen) {
			System.INSTANCE.guiInitialized = true;
		}

		super.onEnable();
	}

	@Override
	public void onDisable() {
		if (mc.currentScreen instanceof ClickGui) {
			System.INSTANCE.clickGui.onGuiClose();
		} else if (mc.currentScreen instanceof dlindustries.uk.daduncs.argonsui.ClickGui) {
			System.INSTANCE.argonGui.onGuiClose();
		} else if (mc.currentScreen instanceof MuonGui muon) {
			muon.close();
		} else if (mc.currentScreen instanceof InventoryScreen) {
			System.INSTANCE.guiInitialized = false;
		}

		super.onDisable();
	}

	public static Screen screen() {
		return switch (ui.getMode()) {
			case Argon -> System.INSTANCE.argonGui;
			case Muon -> System.INSTANCE.muonGui();
			default -> System.INSTANCE.clickGui;
		};
	}

	public static boolean ours(Screen screen) {
		return screen instanceof ClickGui || screen instanceof dlindustries.uk.daduncs.argonsui.ClickGui || screen instanceof MuonGui;
	}

	public static void uiSound(SoundEvent sound) {
		if (openCloseSounds.getValue())
			Sounds.play(sound);
	}
}
