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

import dlindustries.uk.daduncs.gui.Theme;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.colour;
import dlindustries.uk.daduncs.module.setting.mode;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.render.esp.OutlineRenderer;
import dlindustries.uk.daduncs.utils.EncryptedString;

public final class HandShaders extends Module {

	public enum Fill {
		SOLID, FROSTED, FLUID
	}

	private final bool custom = new bool(EncryptedString.of("Custom Color"), false);
	private final colour color = new colour(EncryptedString.of("Color"), 0xFF9B6DFF).setVisibility(custom::getValue);
	private final bool gradient = new bool(EncryptedString.of("Gradient"), true).setVisibility(() -> !custom.getValue());
	private final mode<Fill> fillMode = new mode<>(EncryptedString.of("Fill Mode"), Fill.SOLID, Fill.class);
	private final number fill = new number(EncryptedString.of("Fill"), 0, 1, 0.35, 0.05);
	private final number width = new number(EncryptedString.of("Width"), 1, 6, 3, 0.5);
	private final number glow = new number(EncryptedString.of("Glow"), 0, 24, 24, 1);

	private final OutlineRenderer renderer = new OutlineRenderer();

	public HandShaders() {
		super(EncryptedString.of("HandShaders"),
				EncryptedString.of("pretty self explanatory"),
				-1,
				Category.RENDER);
		addSettings(custom, color, gradient, fillMode, fill, width, glow);
	}

	public void composite() {
		renderer.beginFrame();
		renderer.addDepth(custom.getValue() ? color.getValue() : Theme.accent);
		renderer.composite(width.getValueFloat(), glow.getValueFloat(), fill.getValueFloat(), fillMode.getModeIndex(),
				gradient.getValue() && !custom.getValue(), Theme.accent, Theme.flowEnd());
	}
}
