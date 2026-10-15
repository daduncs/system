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

import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.colour;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.render.SkyPainter;
import dlindustries.uk.daduncs.utils.EncryptedString;
import org.joml.Vector4f;

public final class CustomSky extends Module {

	public CustomSky() {
		super(EncryptedString.of("Custom Sky"),
				EncryptedString.of("Swaps the vanilla sky for one of our own"),
				-1,
				Category.RENDER);
		addSettings(skyColor, color, speed, clouds, stars, waves, fog, fogColor, fogStart, fogEnd);
	}

	private final colour skyColor = new colour(EncryptedString.of("Sky Color"), 0xFF9B6DFF);
	private final number color = new number(EncryptedString.of("Color"), 0, 100, 55, 1);
	private final number speed = new number(EncryptedString.of("Speed"), 0, 5, 3.4, 0.1);
	private final number clouds = new number(EncryptedString.of("Clouds"), 0, 100, 100, 1);
	private final number stars = new number(EncryptedString.of("Stars"), 0, 100, 100, 1);
	private final number waves = new number(EncryptedString.of("Waves"), 0, 100, 100, 1);
	private final bool fog = new bool(EncryptedString.of("Fog"), false);
	private final colour fogColor = new colour(EncryptedString.of("Fog Color"), 0xFF9B6DFF).setVisibility(fog::getValue);
	private final number fogStart = new number(EncryptedString.of("Fog Start"), 0, 200, 17, 1);
	private final number fogEnd = new number(EncryptedString.of("Fog End"), 4, 512, 162, 2);

	public void paint() {
		if (mc.world == null) return;

		float clock = (java.lang.System.currentTimeMillis() % 600000L) / 1000f;
		float day = (mc.world.getTimeOfDay() % 24000L) / 24000f;

		SkyPainter.render(skyColor.getValue(), clock * speed.getValueFloat(), day,
				color.getValueFloat() / 100f, night(day),
				waves.getValueFloat() / 100f, stars.getValueFloat() / 100f, clouds.getValueFloat() / 100f);
	}

	public boolean fogging() {
		return fog.getValue();
	}

	public float fogStart() {
		return fogStart.getValueFloat();
	}

	public float fogEnd() {
		return Math.max(fogStart.getValueFloat() + 1f, fogEnd.getValueFloat());
	}

	public Vector4f fogColour(float alpha) {
		int accent = fogColor.getValue();

		return new Vector4f(((accent >> 16) & 0xFF) / 255f,
				((accent >> 8) & 0xFF) / 255f,
				(accent & 0xFF) / 255f,
				alpha);
	}


	private static float night(float day) {
		float sun = (float) Math.cos((day - 0.25f) * Math.PI * 2.0);

		return Math.max(0f, Math.min(1f, 0.5f - sun * 0.75f));
	}
}
