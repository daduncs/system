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

public final class Frame {

	private static long stamp = java.lang.System.nanoTime();
	private static float delta;

	public static void tick() {
		long now = java.lang.System.nanoTime();
		delta = Math.min(0.1f, (now - stamp) / 1_000_000_000f);
		stamp = now;

		dlindustries.uk.daduncs.module.modules.client.ClickGUI.syncTheme();
		Theme.ease(delta, dlindustries.uk.daduncs.module.modules.client.ClickGUI.gradientSpeed.getValueFloat());
	}

	public static float delta() {
		return delta;
	}

	private Frame() {
	}
}
