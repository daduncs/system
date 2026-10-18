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

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import static dlindustries.uk.daduncs.System.mc;

public final class ProjectionUtils {
	private ProjectionUtils() {
	}

	@Nullable
	public static ProjectedPoint project(Vec3d worldPos) {
		if (mc.world == null || mc.gameRenderer == null) {
			return null;
		}

		Camera camera = mc.gameRenderer.getCamera();
		if (camera == null) {
			return null;
		}

		int width = mc.getWindow().getScaledWidth();
		int height = mc.getWindow().getScaledHeight();
		if (width <= 0 || height <= 0) {
			return null;
		}

		Vec3d cameraPos = camera.getCameraPos();
		Vector4f clip = new Vector4f(
				(float) (worldPos.x - cameraPos.x),
				(float) (worldPos.y - cameraPos.y),
				(float) (worldPos.z - cameraPos.z),
				1.0F
		);

		Matrix4f view = new Matrix4f()
				.rotateX((float) Math.toRadians(camera.getPitch()))
				.rotateY((float) Math.toRadians(camera.getYaw() + 180.0F));
		Matrix4f projection = new Matrix4f().setPerspective(
				(float) Math.toRadians(mc.options.getFov().getValue()),
				(float) width / (float) height,
				0.05F,
				mc.options.getViewDistance().getValue() * 16.0F
		);

		view.transform(clip);
		projection.transform(clip);

		if (clip.w <= 0.0F) {
			return null;
		}

		float ndcX = clip.x / clip.w;
		float ndcY = clip.y / clip.w;
		float ndcZ = clip.z / clip.w;
		if (ndcZ < -1.0F || ndcZ > 1.0F) {
			return null;
		}

		float screenX = (ndcX * 0.5F + 0.5F) * width;
		float screenY = (1.0F - (ndcY * 0.5F + 0.5F)) * height;
		return new ProjectedPoint(screenX, screenY, ndcZ);
	}

	public static float shrink(Vec3d worldPos) {
		double distance = mc.gameRenderer.getCamera().getCameraPos().distanceTo(worldPos);

		return (float) Math.max(0.15, Math.min(1.0, 6.0 / distance));
	}

	public record ProjectedPoint(float x, float y, float depth) {
	}
}
