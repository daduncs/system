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

package dlindustries.uk.daduncs.utils.rotation;

public final class SilentRot {
	private static volatile boolean active;
	private static volatile float y;
	private static volatile float p;
	private static volatile Object holder;
	private static volatile boolean moving;
	private static volatile float realY;

	private SilentRot() {
	}

	/** Client-thread writers keep the direction until their action releases it. */
	public static boolean isAvailableTo(Object requester) {
		return !active || holder == requester;
	}

	public static void set(float yaw, float pitch, Object who) {
		if (!isAvailableTo(who) || !Float.isFinite(yaw) || !Float.isFinite(pitch)) return;
		y = yaw;
		p = Math.max(-90.0f, Math.min(90.0f, pitch));
		active = true;
		holder = who;
	}

	public static void beginMove(float realYaw) {
		realY = realYaw;
		moving = true;
	}

	public static void endMove() {
		moving = false;
	}

	public static boolean moving() {
		return moving;
	}

	public static float real() {
		return realY;
	}

	public static void stop(Object who) {
		if (holder == who) {
			active = false;
			holder = null;
		}
	}

	public static void stop() {
		active = false;
		holder = null;
		moving = false;
	}

	public static boolean on() {
		return active;
	}

	public static float yaw() {
		return y;
	}

	public static float pitch() {
		return p;
	}
}
