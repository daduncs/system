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

import dlindustries.uk.daduncs.System;
import dlindustries.uk.daduncs.module.modules.client.Friends;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Objects;
import java.util.stream.Stream;

import static dlindustries.uk.daduncs.System.mc;

public final class WorldUtils {
	public static boolean isDeadBodyNearby() {
		return mc.world.getPlayers().parallelStream()
				.filter(e -> e != mc.player)
				.filter(e -> e.squaredDistanceTo(mc.player) <= 36)
				.anyMatch(LivingEntity::isDead);
	}

	public static Entity findNearestEntity(PlayerEntity toPlayer, float radius, boolean seeOnly) {
		float mr = Float.MAX_VALUE;
		Entity entity = null;

		assert mc.world != null;
		for (Entity e : mc.world.getEntities()) {
			float d = e.distanceTo(toPlayer);

			if (e != toPlayer && d <= radius && mc.player.canSee(e) == seeOnly) {
				if (d < mr) {
					mr = d;
					entity = e;
				}
			}
		}
		return entity;
	}

	public static double distance(Vec3d fromVec, Vec3d toVec) {
		return Math.sqrt(Math.pow(toVec.x - fromVec.x, 2) + Math.pow(toVec.y - fromVec.y, 2) + Math.pow(toVec.z - fromVec.z, 2));
	}

	public static PlayerEntity findNearestPlayer(PlayerEntity toPlayer, float range, boolean seeOnly, boolean excludeFriends) {
		float minRange = Float.MAX_VALUE;
		PlayerEntity minPlayer = null;

		for (PlayerEntity player : mc.world.getPlayers()) {
			float distance = (float) distance(toPlayer.getEntityPos(), player.getEntityPos());

			if(excludeFriends && System.INSTANCE.getModuleManager().getModule(Friends.class).disableAimAssist.getValue() && System.INSTANCE.getFriendManager().isFriend(player))
				continue;

			if (player != toPlayer && distance <= range && player.canSee(toPlayer) == seeOnly) {
				if (distance < minRange) {
					minRange = distance;
					minPlayer = player;
				}
			}
		}

		return minPlayer;
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

	public static HitResult getHitResult(double radius) {
		return getHitResult(mc.player, false, mc.player.getYaw(), mc.player.getPitch(), radius);
	}

	public static HitResult getHitResult(PlayerEntity entity, boolean ignoreInvisibles, float yaw, float pitch, double distance) {
		if (entity == null || mc.world == null) return null;

		double d = distance;
		Vec3d cameraPosVec = entity.getCameraPosVec(RenderUtils.tickProgress());
		Vec3d rotationVec = getPlayerLookVec(yaw, pitch);
		Vec3d range = cameraPosVec.add(rotationVec.x * d, rotationVec.y * d, rotationVec.z * d);

		HitResult result = mc.world.raycast(new RaycastContext(cameraPosVec, range, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, entity));

		double e = d * d;
		d = distance;

		if (result != null) {
			e = result.getPos().squaredDistanceTo(cameraPosVec);
		}

		Vec3d vec3d3 = cameraPosVec.add(rotationVec.x * d, rotationVec.y * d, rotationVec.z * d);
		Box box = entity.getBoundingBox().stretch(rotationVec.multiply(d)).expand(1.0, 1.0, 1.0);

		EntityHitResult entityHitResult = ProjectileUtil.raycast(entity, cameraPosVec, vec3d3, box, (entityx) ->
				!entityx.isSpectator() && entityx.canHit() && (!entityx.isInvisible() || !ignoreInvisibles), e);

		if (entityHitResult != null) {
			Vec3d vec3d4 = entityHitResult.getPos();
			double g = cameraPosVec.squaredDistanceTo(vec3d4);

			if ((distance > distance && g > Math.pow(distance, 2)) || (g < e || result == null)) {
				result = g > Math.pow(distance, 2)
						? BlockHitResult.createMissed(vec3d4, Direction.getFacing(rotationVec.x, rotationVec.y, rotationVec.z), BlockPos.ofFloored(vec3d4))
						: entityHitResult;
			}
		}

		return result;
	}


	public static boolean placeBlock(BlockHitResult blockHit, boolean swingHand) {
		if (mc.player == null || mc.interactionManager == null || blockHit == null) return false;
		if (!dlindustries.uk.daduncs.managers.AntibanbyDaduncs.canBlockActionNow()) return false;
		ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHit);
		if (result.isAccepted() && swingHand) mc.player.swingHand(Hand.MAIN_HAND);
		return result.isAccepted();
	}

	public static Stream<WorldChunk> getLoadedChunks() {
		int radius = Math.max(2, mc.options.getClampedViewDistance()) + 3;
		int diameter = radius * 2 + 1;

		ChunkPos center = mc.player.getChunkPos();
		ChunkPos min = new ChunkPos(center.x - radius, center.z - radius);
		ChunkPos max = new ChunkPos(center.x + radius, center.z + radius);

		return Stream.iterate(min, pos -> {
					int x = pos.x;
					int z = pos.z;
					x++;
					if (x > max.x) {
						x = min.x;
						z++;
					}
					if (z > max.z)
						throw new IllegalStateException("Stream limit didn't work.");

					return new ChunkPos(x, z);

				}).limit((long) diameter * diameter)
				.filter(c -> mc.world.isChunkLoaded(c.x, c.z))
				.map(c -> mc.world.getChunk(c.x, c.z)).filter(Objects::nonNull);
	}


	public static boolean isShieldFacingAway(PlayerEntity player) {
		if (mc.player == null || player == null) return false;

		Vec3d facing = player.getRotationVector(0f, player.getHeadYaw());

		Vec3d toAttacker = mc.player.getEntityPos().subtract(player.getEntityPos());
		toAttacker = new Vec3d(toAttacker.x, 0, toAttacker.z);

		if (toAttacker.lengthSquared() < 1.0E-6) return false;

		return toAttacker.normalize().dotProduct(facing) < 0;
	}

	public static boolean isSword(ItemStack itemStack) {
		return itemStack.isIn(ItemTags.SWORDS);
	}

	public static boolean isAxe(ItemStack itemStack) {
		return itemStack.isIn(ItemTags.AXES);
	}

	public static boolean isWeapon(ItemStack itemStack) {
		return isSword(itemStack) || isAxe(itemStack) || itemStack.isOf(Items.MACE);
	}

	public static double getEyeToBoxDistance(PlayerEntity player, Entity target) {
		if (player == null || target == null) return Double.MAX_VALUE;
		Vec3d eyePos = player.getEyePos();
		Box box = target.getBoundingBox();
		double clampedX = MathHelper.clamp(eyePos.x, box.minX, box.maxX);
		double clampedY = MathHelper.clamp(eyePos.y, box.minY, box.maxY);
		double clampedZ = MathHelper.clamp(eyePos.z, box.minZ, box.maxZ);
		return eyePos.distanceTo(new Vec3d(clampedX, clampedY, clampedZ));
	}

	public static boolean doesRaycastHitEntity(PlayerEntity player, Entity target, double maxReach) {
		if (player == null || target == null || mc.world == null) return false;
		Vec3d eyePos = player.getEyePos();
		Vec3d lookVec = getPlayerLookVec(player);
		Vec3d reachVec = eyePos.add(lookVec.multiply(maxReach));
		var entityHit = target.getBoundingBox().raycast(eyePos, reachVec);
		if (entityHit.isEmpty()) return false;
		HitResult blockHit = mc.world.raycast(new RaycastContext(eyePos, entityHit.get(), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
		if (blockHit.getType() == HitResult.Type.BLOCK && blockHit.getPos().squaredDistanceTo(eyePos) < entityHit.get().squaredDistanceTo(eyePos)) {
			return false;
		}
		return true;
	}

	public static boolean isTool(ItemStack itemStack) {
		return isWeapon(itemStack)
				|| itemStack.isIn(ItemTags.PICKAXES)
				|| itemStack.isIn(ItemTags.SHOVELS)
				|| itemStack.isIn(ItemTags.HOES);
	}

	public static boolean isCrit(PlayerEntity player, Entity target) {
		return player.getAttackCooldownProgress(0.5F) > 0.9F && player.fallDistance > 0.0F && !player.isOnGround() && !player.isClimbing() && !player.isSubmergedInWater() && !player.hasStatusEffect(StatusEffects.BLINDNESS) && target instanceof LivingEntity;
	}

	public static void hitEntity(Entity entity, boolean swingHand) {
		mc.interactionManager.attackEntity(mc.player, entity);

		if (swingHand)
			mc.player.swingHand(Hand.MAIN_HAND);
	}
}
