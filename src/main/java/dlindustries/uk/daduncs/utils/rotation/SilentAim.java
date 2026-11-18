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

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Optional;

public final class SilentAim {

	private static final MinecraftClient mc = MinecraftClient.getInstance();

	private static float[] lastRotations = {0f, 0f};
	private static float[] lastSentRotations = {0f, 0f};
	private static float gcdYawRemainder;
	private static float gcdPitchRemainder;
	private static long lastNanoTime;

	private static boolean tracking;
	private static LivingEntity currentTarget;
	private static Object owner;

	private static LivingEntity stickyTarget;
	private static Vec3d stickyOffset;

	private SilentAim() {
	}

	public static boolean rotateTo(Object requester, LivingEntity target, float yawSpeed, float pitchSpeed, float multipoint) {
		if (target == null || mc.player == null || mc.world == null) return false;
		if ((owner != null && owner != requester) || !SilentRot.isAvailableTo(requester)) return false;

		float partial = mc.getRenderTickCounter().getTickProgress(false);
		Optional<Vec3d> point = possiblePoint(target, multipoint * 0.01f, partial);
		if (point.isEmpty()) return false;
		owner = requester;

		if (currentTarget != target || !tracking) {
			currentTarget = target;
			if (!tracking) initFromPlayer();
		}
		tracking = true;

		float[] wanted = continuity(lastRotations, calculate(mc.player.getCameraPosVec(partial), point.get()));
		lastRotations = fixed(lastRotations, step(lastRotations, wanted, yawSpeed, pitchSpeed));
		apply(lastRotations);
		return true;
	}

	public static boolean rotateBack(Object requester, float yawSpeed, float pitchSpeed) {
		if ((owner != null && owner != requester) || !SilentRot.isAvailableTo(requester)) return false;

		if (mc.player == null || !tracking) {
			stop(requester);
			return false;
		}

		float delta = deltaSeconds();
		float[] client = {mc.player.getYaw(), mc.player.getPitch()};

		float targetYaw = lerpAngle(lastRotations[0], client[0], delta, yawSpeed * 2.5f);
		float targetPitch = lerpAngle(lastRotations[1], client[1], delta, pitchSpeed * 2.5f);
		lastRotations = fixed(lastRotations, new float[]{targetYaw, targetPitch});

		float yawDiff = Math.abs(MathHelper.wrapDegrees(lastRotations[0] - client[0]));
		float pitchDiff = Math.abs(lastRotations[1] - client[1]);

		if (yawDiff < 1f && pitchDiff < 1f) {
			stop(requester);
			return false;
		}

		apply(lastRotations);
		return true;
	}

	public static void stop(Object requester) {
		if (owner != null && owner != requester) return;

		SilentRot.stop(requester);
		if (mc.player != null) {
			lastSentRotations[0] = mc.player.getYaw();
			lastSentRotations[1] = mc.player.getPitch();
		}

		lastNanoTime = 0;
		tracking = false;
		currentTarget = null;
		owner = null;
		stickyTarget = null;
		stickyOffset = null;
	}

	public static boolean isTracking() {
		return tracking;
	}

	public static void onPlayerChanged() {
		stop(owner);
	}

	public static float yaw() {
		return lastSentRotations[0];
	}

	public static float pitch() {
		return lastSentRotations[1];
	}

	private static void initFromPlayer() {
		float startYaw = dlindustries.uk.daduncs.managers.AntibanbyDaduncs.hasSentRotation()
				? dlindustries.uk.daduncs.managers.AntibanbyDaduncs.getLastSentYaw()
				: mc.player.getYaw();
		float startPitch = dlindustries.uk.daduncs.managers.AntibanbyDaduncs.hasSentRotation()
				? dlindustries.uk.daduncs.managers.AntibanbyDaduncs.getLastSentPitch()
				: mc.player.getPitch();
		startPitch = MathHelper.clamp(startPitch, -90f, 90f);
		lastRotations[0] = startYaw;
		lastRotations[1] = startPitch;
		lastSentRotations[0] = startYaw;
		lastSentRotations[1] = startPitch;
		gcdYawRemainder = 0f;
		gcdPitchRemainder = 0f;
	}

	private static void apply(float[] rotations) {
		float yawDiff = Math.abs(MathHelper.wrapDegrees(rotations[0] - lastSentRotations[0]));
		float pitch = MathHelper.clamp(rotations[1], -90f, 90f);
		float pitchDiff = Math.abs(pitch - lastSentRotations[1]);

		if (yawDiff <= 0.01f && pitchDiff <= 0.01f) return;

		float yaw = lastSentRotations[0] + MathHelper.wrapDegrees(rotations[0] - lastSentRotations[0]);

		SilentRot.set(yaw, pitch, owner);
		lastSentRotations[0] = yaw;
		lastSentRotations[1] = pitch;
	}

	private static float[] step(float[] last, float[] wanted, float yawSpeed, float pitchSpeed) {
		float delta = deltaSeconds();
		return new float[]{
				lerpAngle(last[0], wanted[0], delta, yawSpeed * 2.5f),
				lerpAngle(last[1], wanted[1], delta, pitchSpeed * 2.5f)
		};
	}

	private static float lerpAngle(float current, float target, float delta, float speed) {
		float diff = MathHelper.wrapDegrees(target - current);
		float step = speed * delta;
		if (Math.abs(diff) <= step) return current + diff;
		return current + Math.signum(diff) * step;
	}

	private static float[] fixed(float[] previous, float[] current) {
		float yawDiff = MathHelper.wrapDegrees(current[0] - previous[0]);
		float pitchDiff = MathHelper.wrapDegrees(current[1] - previous[1]);
		return gcd(new float[]{previous[0] + yawDiff, previous[1] + pitchDiff}, previous);
	}

	private static float[] gcd(float[] rotations, float[] last) {
		float f = (float) (mc.options.getMouseSensitivity().getValue() * 0.6f + 0.2f);
		float gcd = f * f * f * 1.2f;

		float yaw = gcdYawRemainder + MathHelper.wrapDegrees(rotations[0] - last[0]);
		float pitch = gcdPitchRemainder + (rotations[1] - last[1]);
		float snappedYaw = gcd <= 1e-6f ? yaw : Math.round(yaw / gcd) * gcd;
		float snappedPitch = gcd <= 1e-6f ? pitch : Math.round(pitch / gcd) * gcd;

		gcdYawRemainder = MathHelper.wrapDegrees(yaw - snappedYaw);
		gcdPitchRemainder = pitch - snappedPitch;

		return new float[]{last[0] + snappedYaw, MathHelper.clamp(last[1] + snappedPitch, -90f, 90f)};
	}

	private static float[] continuity(float[] last, float[] target) {
		float lastYaw = MathHelper.wrapDegrees(last[0]);
		float diff = MathHelper.wrapDegrees(target[0] - lastYaw);
		return new float[]{lastYaw + diff, MathHelper.clamp(target[1], -90f, 90f)};
	}

	private static float[] calculate(Vec3d from, Vec3d to) {
		Vec3d diff = to.subtract(from);
		double distance = Math.hypot(diff.x, diff.z);
		float yaw = MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90f);
		float pitch = MathHelper.wrapDegrees((float) -Math.toDegrees(Math.atan2(diff.y, distance)));
		return new float[]{yaw, pitch};
	}

	private static float deltaSeconds() {
		long now = java.lang.System.nanoTime();
		float delta = lastNanoTime == 0 ? 1f / 60f : (now - lastNanoTime) / 1_000_000_000f;
		lastNanoTime = now;
		return Math.min(delta, 0.1f);
	}

	private static Box box(Entity entity, float partial) {
		return entity.getDimensions(entity.getPose()).getBoxAt(entity.getLerpedPos(partial));
	}

	private static Optional<Vec3d> possiblePoint(LivingEntity entity, float shrink, float partial) {
		Box box = box(entity, partial);
		Vec3d center = box.getCenter();
		Vec3d candidate = center.lerp(new Vec3d(center.x, box.maxY - 0.1f, center.z), shrink);

		if (visible(candidate, partial)) {
			stickyTarget = null;
			stickyOffset = null;
			return Optional.of(candidate);
		}

		if (stickyTarget == entity && stickyOffset != null) {
			Vec3d sticky = new Vec3d(box.minX + stickyOffset.x, box.minY + stickyOffset.y, box.minZ + stickyOffset.z);
			if (inside(sticky, box) && visible(sticky, partial))
				return Optional.of(sticky);
		}

		Vec3d alt = visibleIn(box, candidate, partial);
		if (alt != null) {
			stickyTarget = entity;
			stickyOffset = new Vec3d(alt.x - box.minX, alt.y - box.minY, alt.z - box.minZ);
			return Optional.of(alt);
		}

		stickyTarget = null;
		stickyOffset = null;
		return Optional.empty();
	}

	private static boolean inside(Vec3d point, Box box) {
		return point.x >= box.minX && point.x <= box.maxX && point.y >= box.minY && point.y <= box.maxY && point.z >= box.minZ && point.z <= box.maxZ;
	}

	private static boolean visible(Vec3d point, float partial) {
		HitResult hit = mc.world.raycast(new RaycastContext(mc.player.getCameraPosVec(partial), point,
				RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
		return hit.getType() == HitResult.Type.MISS || hit.getPos().squaredDistanceTo(point) < 1.0e-6d;
	}

	private static Vec3d visibleIn(Box box, Vec3d preferred, float partial) {
		Vec3d best = null;
		double bestDistance = Double.MAX_VALUE;

		for (int xi = 0; xi < 3; xi++) {
			double x = box.minX + xi / 2.0 * (box.maxX - box.minX);
			for (int yi = 0; yi < 5; yi++) {
				double y = box.minY + yi / 4.0 * (box.maxY - box.minY);
				for (int zi = 0; zi < 3; zi++) {
					double z = box.minZ + zi / 2.0 * (box.maxZ - box.minZ);
					Vec3d sample = new Vec3d(x, y, z);

					if (!visible(sample, partial)) continue;

					double distance = sample.squaredDistanceTo(preferred);
					if (distance < bestDistance) {
						bestDistance = distance;
						best = sample;
					}
				}
			}
		}

		return best;
	}
}
