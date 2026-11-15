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

import dlindustries.uk.daduncs.utils.rotation.Rotation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.*;

import static dlindustries.uk.daduncs.System.mc;

public final class RotationUtils {

	public static Vec3d getEyesPos(PlayerEntity player) {
		return player.getCameraPosVec(RenderUtils.tickProgress());
	}

	public static BlockPos getCameraBlockPos() {
		return mc.gameRenderer.getCamera().getBlockPos();
	}

	public static BlockPos getEyesBlockPos() {
		return new BlockPos((int) getEyesPos(mc.player).x, (int) getEyesPos(mc.player).y, (int) getEyesPos(mc.player).z);
	}

	public static Vec3d getPlayerLookVec(float yaw, float pitch) {
		float f = pitch * 0.017453292F;
		float g = -yaw * 0.017453292F;

		float h = MathHelper.cos(g);
		float i = MathHelper.sin(g);
		float j = MathHelper.cos(f);
		float k = MathHelper.sin(f);

		return new Vec3d((i * j), (-k), (h * j));
	}

	public static Vec3d getPlayerLookVec(PlayerEntity player) {
		return getPlayerLookVec(player.getYaw(), player.getPitch());
	}

	public static Rotation getDiff(Rotation rotation1, Rotation rotation2) {
		double yaw = Math.abs(MathHelper.wrapDegrees((float) (rotation1.yaw() - rotation2.yaw())));
		double pitch = Math.abs(rotation1.pitch() - rotation2.pitch());

		return new Rotation(yaw, pitch);
	}

	public static Rotation getSmoothRotation(Rotation from, Rotation to, double speed) {
		return new Rotation(
				MathHelper.lerpAngleDegrees((float) speed, (float) from.yaw(), (float) to.yaw()),
				MathHelper.lerpAngleDegrees((float) speed, (float) from.pitch(), (float) to.pitch())
		);
	}

	public static double getTotalDiff(Rotation rotation1, Rotation rotation2) {
		Rotation diff = getDiff(rotation1, rotation2);

		return diff.yaw() + diff.pitch();
	}

	public static Vec3d getClientLookVec() {
		return getPlayerLookVec(mc.player);
	}

	public static Rotation getDirection(Entity entity, Vec3d vec) {
		return getDirection(entity.getEyePos(), vec);
	}

	public static Rotation getDirection(Vec3d from, Vec3d to) {
		double dx = to.x - from.x,
				dy = to.y - from.y,
				dz = to.z - from.z,
				dist = MathHelper.sqrt((float) (dx * dx + dz * dz));

		double yaw = (dx == 0.0 && dz == 0.0) ? 0.0 : MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		double pitch = (dist == 0.0 && dy == 0.0) ? 0.0 : MathHelper.clamp(-MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dy, dist))), -90.0, 90.0);
		return new Rotation(yaw, pitch);
	}

	public static double getAngleToRotation(Rotation rotation) {
		double currentYaw = MathHelper.wrapDegrees(mc.player.getYaw());
		double currentPitch = MathHelper.wrapDegrees(mc.player.getPitch());

		double diffYaw = MathHelper.wrapDegrees(currentYaw - rotation.yaw());
		double diffPitch = MathHelper.wrapDegrees(currentPitch - rotation.pitch());

		return Math.sqrt(diffYaw * diffYaw + diffPitch * diffPitch);
	}

	public static double getSensitivity() {
		if (mc.options != null && mc.options.getMouseSensitivity() != null) {
			return mc.options.getMouseSensitivity().getValue();
		}
		return 0.5;
	}

	public static double getGCD() {
		double sens = getSensitivity();
		double f = sens * 0.6 + 0.2;
		return f * f * f * 1.2;
	}

	public static float quantizeAngle(double delta, double gcd) {
		if (gcd <= 0.00001) return (float) delta;
		long steps = Math.round(delta / gcd);
		return (float) (steps * gcd);
	}

	public static Rotation applyGCD(float fromYaw, float fromPitch, float targetYaw, float targetPitch) {
		return applyGCD(fromYaw, fromPitch, targetYaw, targetPitch, null, true);
	}

	public static Rotation applyGCD(float fromYaw, float fromPitch, float targetYaw, float targetPitch, double[] remainder) {
		return applyGCD(fromYaw, fromPitch, targetYaw, targetPitch, remainder, true);
	}

	public static Rotation applyGCD(float fromYaw, float fromPitch, float targetYaw, float targetPitch, double[] remainder, boolean couplePitchTremor) {
		double gcd = getGCD();
		if (gcd <= 0.00001) return new Rotation(targetYaw, MathHelper.clamp(targetPitch, -90.0F, 90.0F));

		double diffYaw = MathHelper.wrapDegrees(targetYaw - fromYaw) + (remainder != null ? remainder[0] : 0);
		double diffPitch = (targetPitch - fromPitch) + (remainder != null ? remainder[1] : 0);

		long stepsYaw = Math.round(diffYaw / gcd);
		long stepsPitch = Math.round(diffPitch / gcd);

		if (couplePitchTremor) {
			long timeMs = System.currentTimeMillis();
			if (stepsYaw != 0 && stepsPitch == 0 && Math.abs(fromPitch) < 88.0f) {
				double tremorWave = Math.sin(timeMs * 0.024);
				if (Math.abs(tremorWave) > 0.45) {
					stepsPitch = (long) Math.signum(tremorWave);
				}
			} else if (stepsPitch != 0 && stepsYaw == 0) {
				double tremorWave = Math.cos(timeMs * 0.024);
				if (Math.abs(tremorWave) > 0.45) {
					stepsYaw = (long) Math.signum(tremorWave);
				}
			}
		}

		double deltaYaw = (double) stepsYaw * gcd;
		double deltaPitch = (double) stepsPitch * gcd;

		if (remainder != null) {
			remainder[0] = diffYaw - deltaYaw;
			remainder[1] = diffPitch - deltaPitch;
		}

		float quantizedYaw = (float) ((double) fromYaw + deltaYaw);
		float quantizedPitch = MathHelper.clamp((float) ((double) fromPitch + deltaPitch), -90.0F, 90.0F);

		return new Rotation(quantizedYaw, quantizedPitch);
	}

	public static Rotation stepBallisticRotation(float currentYaw, float currentPitch, float targetYaw, float targetPitch, float maxSpeed, double[] remainder) {
		float diffY = MathHelper.wrapDegrees(targetYaw - currentYaw);
		float diffP = targetPitch - currentPitch;
		float dist = (float) Math.hypot(diffY, diffP);
		if (dist < 0.001f) {
			return applyGCD(currentYaw, currentPitch, targetYaw, targetPitch, remainder, true);
		}

		float cappedMax = Math.min(20.0f, maxSpeed);
		float speed = Math.max(1.8f, Math.min(cappedMax, dist * 0.45f));
		speed *= (float) (0.94 + Math.random() * 0.12);

		float stepDist = Math.min(dist, speed);
		float stepY = (diffY / dist) * stepDist;
		float stepP = (diffP / dist) * stepDist;

		float nextY = currentYaw + stepY;
		float nextP = MathHelper.clamp(currentPitch + stepP, -90.0f, 90.0f);

		return applyGCD(currentYaw, currentPitch, nextY, nextP, remainder, true);
	}

	public static Vec3d getWanderingTargetPoint(Entity entity, Vec3d basePoint) {
		if (entity == null || basePoint == null) return basePoint;
		long time = System.currentTimeMillis();
		double t = (time % 1000000L) * 0.001;
		double swayX = Math.sin(t * 1.8) * 0.09 * (entity.getWidth() / 0.6);
		double swayY = Math.cos(t * 1.3) * 0.07 * (entity.getHeight() / 1.8);
		double swayZ = Math.cos(t * 2.1) * 0.09 * (entity.getWidth() / 0.6);
		return basePoint.add(swayX, swayY, swayZ);
	}

	public static double minimumJerk(double t) {
		t = Math.max(0.0, Math.min(1.0, t));
		return t * t * t * (10.0 - 15.0 * t + 6.0 * t * t);
	}
}


