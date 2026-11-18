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

package dlindustries.uk.daduncs.utils;

import org.lwjgl.glfw.GLFW;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static dlindustries.uk.daduncs.System.mc;


public final class MouseSimulation {
	private static final ScheduledExecutorService clickScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "system-mouse-sim");
		t.setDaemon(true);
		return t;
	});

	public static void mousePress(int keyCode) {
		if (mc != null && !mc.isOnThread()) {
			mc.execute(() -> mousePress(keyCode));
			return;
		}
		dlindustries.uk.daduncs.managers.Recorder tm = dlindustries.uk.daduncs.managers.Recorder.getInstance();
		if (tm != null && mc != null && mc.getWindow() != null) {
			double x = mc.mouse != null ? mc.mouse.getX() : 0;
			double y = mc.mouse != null ? mc.mouse.getY() : 0;
			tm.recordClick(keyCode, GLFW.GLFW_PRESS, 0, x, y, true);
		}
	}

	public static void mouseRelease(int keyCode) {
		if (mc != null && !mc.isOnThread()) {
			mc.execute(() -> mouseRelease(keyCode));
			return;
		}
		dlindustries.uk.daduncs.managers.Recorder tm = dlindustries.uk.daduncs.managers.Recorder.getInstance();
		if (tm != null && mc != null && mc.getWindow() != null) {
			double x = mc.mouse != null ? mc.mouse.getX() : 0;
			double y = mc.mouse != null ? mc.mouse.getY() : 0;
			tm.recordClick(keyCode, GLFW.GLFW_RELEASE, 0, x, y, true);
		}
	}

	public static void mouseClick(int keyCode, int millis) {
		mousePress(keyCode);
		clickScheduler.schedule(() -> {
			if (mc != null) {
				mc.execute(() -> mouseRelease(keyCode));
			}
		}, Math.max(0, millis), TimeUnit.MILLISECONDS);
	}

	public static void mouseClick(int keyCode) {
		mouseClick(keyCode, 35);
	}
}
