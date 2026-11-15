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

package dlindustries.uk.daduncs.module.modules.sword;

import dlindustries.uk.daduncs.System;
import dlindustries.uk.daduncs.event.events.HudListener;
import dlindustries.uk.daduncs.event.events.MouseMoveListener;
import dlindustries.uk.daduncs.gui.Notice;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.crystal.HitManager;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.range;
import dlindustries.uk.daduncs.module.setting.mode;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.*;
import dlindustries.uk.daduncs.utils.rotation.Rotation;
import dlindustries.uk.daduncs.module.modules.client.Friends;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public final class AimAssist extends Module implements HudListener, MouseMoveListener {

	public enum WeaponMode {
		MACE_ONLY("Mace Only"),
		WEAPONS_ONLY("Weapons Only"),
		MACE_AND_WEAPONS("Mace and Weapons"),
		ONLY_AXE("Only Axe"),
		MACE_AND_AXE("Mace and Axe"),
		ALL("All Items");

		private final String name;

		WeaponMode(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	public enum PosMode {
		Normal, Lerped
	}

	public enum AimMode {
		Head, Chest, Legs
	}

	public enum LerpMode {
		Normal, Smoothstep, EaseOut
	}

	public enum EntityMode {
		PLAYERS_ONLY("Players Only"),
		ALL_ENTITIES("All Entities");

		private final String name;

		EntityMode(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	private final bool stickyAim = new bool(EncryptedString.of("Sticky Aim"), true)
			.setDescription(EncryptedString.of("Aims at the last attacked player"));

	private final mode<WeaponMode> weaponMode = new mode<>(
			EncryptedString.of("Filter"),
			WeaponMode.WEAPONS_ONLY,
			WeaponMode.class
	).setDescription(EncryptedString.of("Which items trigger aim assist"));

	private final bool onLeftClick = new bool(EncryptedString.of("On Left Click"), false)
			.setDescription(EncryptedString.of("Only gets triggered if holding down left click"));

	private final bool onRightClick = new bool(EncryptedString.of("On Right Click"), false)
			.setDescription(EncryptedString.of("Only gets triggered if holding down right click"));

	private final mode<EntityMode> entities = new mode<>(
			EncryptedString.of("Entities"),
			EntityMode.PLAYERS_ONLY,
			EntityMode.class
	).setDescription(EncryptedString.of("Which entities to target with aim assist"));

	private final mode<AimMode> aimAt = new mode<>(EncryptedString.of("Aim At"), AimMode.Chest, AimMode.class);

	private final bool stopAtTargetVertical = new bool(EncryptedString.of("Stop at Target Vert"), false)
			.setDescription(EncryptedString.of("Stops vertically assisting if already aiming at the entity, helps bypass anti-cheat"));

	private final bool stopAtTargetHorizontal = new bool(EncryptedString.of("Stop at Target Horiz"), false)
			.setDescription(EncryptedString.of("Stops horizontally assisting if already aiming at the entity, helps bypass anti-cheat"));

	private final number radius = new number(EncryptedString.of("Radius"), 0.1, 15, 4.0, 0.1);

	private final bool seeOnly = new bool(EncryptedString.of("Visible Only"), true);
	private final bool ignoreNoHP = new bool(EncryptedString.of("Ignore 0 HP"), true)
			.setDescription(EncryptedString.of("Stops assisting if target entity has 0 HP (is dead)"));
	private final bool maxReachMode = new bool(EncryptedString.of("Max Reach Mode"), true)
			.setDescription(EncryptedString.of("Aims at the nearest point of the hitbox to maximize reach"));
	private final bool lockMode = new bool(EncryptedString.of("Lock Mode"), false)
			.setDescription(EncryptedString.of("Full targets the player you attack manually (doesnt work with triggerbot)"));

	private final number fov = new number(EncryptedString.of("FOV"), 5, 360, 90, 1);

	private final range pitchSpeed = new range(EncryptedString.of("Vertical Speed"), 0, 10, 0.1, 6.0, 10.0);
	private final range yawSpeed = new range(EncryptedString.of("Horizontal Speed"), 0, 10, 0.1, 7.5, 10.0);

	private final bool aimResistance = new bool(EncryptedString.of("Aim Resistance"), true)
			.setDescription(EncryptedString.of("Resists moving off target when manually aiming to keep crosshair centered"));

	private final number resistanceStrength = new number(EncryptedString.of("Resistance"), 0.1, 0.9, 0.45, 0.05)
			.setDescription(EncryptedString.of("How strongly aim resists moving off target"));

	private final bool softFriction = new bool(EncryptedString.of("Soft Slowdown"), true)
			.setDescription(EncryptedString.of("Smoothly decelerates when centered on target for extra stealth"));

	private final number speedChange = new number(EncryptedString.of("Speed Delay"), 0, 1000, 000, 1)
			.setDescription(EncryptedString.of("Time in milliseconds to wait after resetting random speed"));

	private final number randomization = new number(EncryptedString.of("Chance"), 0, 100, 100, 1);

	private final bool yawAssist = new bool(EncryptedString.of("Horizontal"), true);
	private final bool pitchAssist = new bool(EncryptedString.of("Vertical"), true);

	private final number waitFor = new number(EncryptedString.of("Wait on Move"), 0, 1000, 0, 1)
			.setDescription(EncryptedString.of("After you move your mouse aim assist will stop working for the selected amount of time"));

	private final mode<LerpMode> lerp = new mode<>(EncryptedString.of("Lerp"), LerpMode.Smoothstep, LerpMode.class)
			.setDescription(EncryptedString.of("Linear interpolation to use to rotate"));

	private final mode<PosMode> posMode = new mode<>(EncryptedString.of("Pos mode"), PosMode.Lerped, PosMode.class)
			.setDescription(EncryptedString.of("Precision of the target position"));

	private final TimerUtils timer = new TimerUtils();
	private final TimerUtils resetSpeed = new TimerUtils();
	private final double[] gcdRemainder = new double[2];
	private boolean move = true;
	private float pitch, yaw;
	private boolean lastAttackState;
	private LivingEntity currentTarget = null;
	private float lastTrackedYaw, lastTrackedPitch;
	private float currentAimVelocityYaw = 0.0f;
	private float currentAimVelocityPitch = 0.0f;
	private boolean initialized = false;

	public AimAssist() {
		super(EncryptedString.of("Aim Assist"),
				EncryptedString.of("Automatically aims at players for you"),
				-1,
				Category.SWORD);

		addSettings(stickyAim, weaponMode, onLeftClick, onRightClick, entities, aimAt, maxReachMode, stopAtTargetVertical, stopAtTargetHorizontal, radius, seeOnly, ignoreNoHP, lockMode, fov, pitchSpeed, yawSpeed, aimResistance, resistanceStrength, softFriction, speedChange, randomization, yawAssist, pitchAssist, waitFor, lerp, posMode);
	}

	@Override
	public void onEnable() {
		move = true;
		initialized = false;
		currentAimVelocityYaw = 0.0f;
		currentAimVelocityPitch = 0.0f;
		gcdRemainder[0] = 0;
		gcdRemainder[1] = 0;
		pitch = pitchSpeed.getRandomValueFloat();
		yaw = yawSpeed.getRandomValueFloat();

		eventManager.add(HudListener.class, this);
		eventManager.add(MouseMoveListener.class, this);

		timer.reset();
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(HudListener.class, this);
		eventManager.remove(MouseMoveListener.class, this);
		gcdRemainder[0] = 0;
		gcdRemainder[1] = 0;
		currentAimVelocityYaw = 0.0f;
		currentAimVelocityPitch = 0.0f;
		initialized = false;
		super.onDisable();
	}

	@Override
	public void onRenderHud(HudEvent event) {
		if (mc.player == null || mc.world == null || mc.options == null || mc.currentScreen != null)
			return;

		boolean attackPressed = mc.options.attackKey.isPressed();
		if (lockMode.getValue() && attackPressed && !this.lastAttackState) {
			if (mc.crosshairTarget instanceof EntityHitResult hitResult && hitResult.getEntity() instanceof LivingEntity clickedTarget) {
				if (clickedTarget != mc.player) {
					if (entities.isMode(EntityMode.ALL_ENTITIES) || clickedTarget instanceof PlayerEntity) {
						currentTarget = clickedTarget;
					}
				}
			}
		}
		this.lastAttackState = attackPressed;

		if (waitFor.getValueFloat() <= 0) {
			move = true;
		} else if (!move && timer.delay(waitFor.getValueFloat())) {
			move = true;
			timer.reset();
		}

		long windowHandle = mc.getWindow().getHandle();
		boolean isRightClick = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
		boolean isLeftClick = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

		ItemStack heldStack = mc.player.getMainHandStack();
		Item heldItem = heldStack.getItem();
		String heldItemName = heldItem.getTranslationKey().toLowerCase();

		HitManager hitManager = HitManager.getInstance();
		boolean isSlamActive = hitManager != null && hitManager.isEnabled() && hitManager.isSlamActive();

		if (!isSlamActive) {
			boolean isMace = heldItemName.contains("mace");
			boolean isAxe = heldItemName.contains("axe");
			boolean isWeapon = WorldUtils.isWeapon(heldStack);

			switch (weaponMode.getMode()) {
				case MACE_ONLY:
					if (!isMace) return;
					break;
				case WEAPONS_ONLY:
					if (!isWeapon) return;
					break;
				case MACE_AND_WEAPONS:
					if (!isWeapon && !isMace) return;
					break;
				case MACE_AND_AXE:
					if (!isMace && !isAxe) return;
					break;
				case ONLY_AXE:
					if (!isAxe) return;

					boolean hasMaceInHotbar = false;
					for (int i = 0; i < 9; i++) {
						ItemStack stack = mc.player.getInventory().getStack(i);
						if (!stack.isEmpty() && stack.getItem().getTranslationKey().toLowerCase().contains("mace")) {
							hasMaceInHotbar = true;
							break;
						}
					}

					if (!hasMaceInHotbar) return;
					break;
				case ALL:
					break;
			}

			if (onLeftClick.getValue() && !isLeftClick)
				return;
			if (onRightClick.getValue() && !isRightClick)
				return;
		}

		LivingEntity target = null;

		if (isSlamActive && hitManager.getSlamTarget() instanceof LivingEntity slamLiving && slamLiving.isAlive() && !slamLiving.isRemoved()) {
			target = slamLiving;
		} else if (lockMode.getValue()) {
			if (currentTarget != null) {
				if (currentTarget.isRemoved() || !currentTarget.isAlive()) {
					currentTarget = null;
				} else if (mc.player.distanceTo(currentTarget) <= radius.getValueFloat()) {
					target = currentTarget;
				}
			}
		} else {
			target = findTarget(radius.getValueFloat());
			if (stickyAim.getValue() && mc.player.getAttacking() instanceof LivingEntity living && living.distanceTo(mc.player) <= radius.getValueFloat()) {
				if (entities.isMode(EntityMode.ALL_ENTITIES) || living instanceof PlayerEntity) {
					target = living;
				}
			}
		}

		if (target == null)
			return;

		if (ignoreNoHP.getValue() && target.getHealth() <= 0.0F)
			return;

		if (seeOnly.getValue() && !mc.player.canSee(target))
			return;

		if (resetSpeed.delay(speedChange.getValueFloat())) {
			pitch = pitchSpeed.getRandomValueFloat();
			yaw = yawSpeed.getRandomValueFloat();
			resetSpeed.reset();
		}

		Vec3d eyePos = mc.player.getEyePos();
		Vec3d targetBase = posMode.isMode(PosMode.Normal) ? target.getEntityPos() : target.getLerpedPos(RenderUtils.tickProgress());

		Vec3d targetPos;
		if (maxReachMode.getValue()) {
			double halfWidth = target.getWidth() / 2.0;
			double maxOffset = Math.max(0.0, halfWidth - 0.05);
			double clampedX = MathHelper.clamp(eyePos.x, targetBase.x - maxOffset, targetBase.x + maxOffset);
			double minY = targetBase.y + 0.05;
			double maxY = targetBase.y + Math.max(0.05, target.getHeight() - 0.05);
			double clampedY = MathHelper.clamp(eyePos.y, minY, maxY);
			double clampedZ = MathHelper.clamp(eyePos.z, targetBase.z - maxOffset, targetBase.z + maxOffset);
			targetPos = new Vec3d(clampedX, clampedY, clampedZ);
		} else {
			if (aimAt.isMode(AimMode.Head)) {
				targetPos = targetBase.add(0, target.getEyeHeight(target.getPose()), 0);
			} else if (aimAt.isMode(AimMode.Chest)) {
				targetPos = targetBase.add(0, target.getHeight() * 0.65, 0);
			} else {
				targetPos = targetBase.add(0, target.getHeight() * 0.25, 0);
			}
		}
		targetPos = RotationUtils.getWanderingTargetPoint(target, targetPos);

		Rotation rotation = RotationUtils.getDirection(eyePos, targetPos);

		double angleToRotation = RotationUtils.getAngleToRotation(rotation);
		if (fov.getValueInt() < 360 && angleToRotation > (double) fov.getValueInt() / 2)
			return;

		if (!initialized) {
			lastTrackedYaw = mc.player.getYaw();
			lastTrackedPitch = mc.player.getPitch();
			initialized = true;
		}

		float userDeltaYaw = MathHelper.wrapDegrees(mc.player.getYaw() - lastTrackedYaw);
		float userDeltaPitch = mc.player.getPitch() - lastTrackedPitch;

		if (Math.hypot(userDeltaYaw, userDeltaPitch) > 90.0) {
			userDeltaYaw = 0.0f;
			userDeltaPitch = 0.0f;
		}

		float userSpeed = (float) Math.hypot(userDeltaYaw, userDeltaPitch);
		boolean isUserMovingMouse = userSpeed > 0.0005f;

		float toTargetYaw = MathHelper.wrapDegrees((float) (rotation.yaw() - mc.player.getYaw()));
		float toTargetPitch = (float) (rotation.pitch() - mc.player.getPitch());
		float angularDistance = (float) Math.hypot(toTargetYaw, toTargetPitch);

		float dot = 0.0f;
		if (isUserMovingMouse && angularDistance > 0.001f) {
			dot = (userDeltaYaw * toTargetYaw + userDeltaPitch * toTargetPitch) / (userSpeed * angularDistance);
		}

		float currentRadius = radius.getValueFloat();
		boolean lookingAtTarget = WorldUtils.getHitResult(currentRadius) instanceof EntityHitResult hitResult && hitResult.getEntity() == target;
		boolean nearTarget = lookingAtTarget || angularDistance < 7.0f;

		float yawStrength = Math.min(1.0f, yaw / 15.0f);
		float pitchStrength = Math.min(1.0f, pitch / 15.0f);

		if (softFriction.getValue() && !mc.player.isGliding() && !isSlamActive) {
			float friction = (float) MathHelper.clamp(angleToRotation / 3.0, 0.4, 1.0);
			yawStrength *= friction;
			pitchStrength *= friction;
		}

		if (randomization.getValueInt() < 100) {
			float variance = (100 - randomization.getValueInt()) / 100.0f * 0.3f;
			float randScale = 1.0f - (float) (Math.random() * variance);
			yawStrength *= randScale;
			pitchStrength *= randScale;
		}

		float targetYaw = mc.player.getYaw();
		float targetPitch = mc.player.getPitch();

		if (aimResistance.getValue() && nearTarget && isUserMovingMouse && dot < -0.05f) {
			float resist = (float) resistanceStrength.getValue();
			float closeness = lookingAtTarget ? 1.0f : (1.0f - (angularDistance / 7.0f));
			float effectiveResistance = resist * MathHelper.clamp(closeness, 0.35f, 1.0f);
			float frictionMultiplier = 1.0f - effectiveResistance;

			float resistedDeltaYaw = userDeltaYaw * frictionMultiplier;
			float resistedDeltaPitch = userDeltaPitch * frictionMultiplier;

			targetYaw = lastTrackedYaw + resistedDeltaYaw;
			targetPitch = lastTrackedPitch + resistedDeltaPitch;
		} else {
			float motorNoise = (float) (0.96 + Math.random() * 0.08);
			float effectiveYawStrength = yawStrength * motorNoise;
			float effectivePitchStrength = pitchStrength * motorNoise;

			if (isUserMovingMouse && dot > 0.1f) {
				float assistBonus = Math.min(1.4f, 1.0f + (dot * 0.35f));
				effectiveYawStrength *= assistBonus;
				effectivePitchStrength *= assistBonus;
			} else if (!isUserMovingMouse) {
				effectiveYawStrength *= 0.35f;
				effectivePitchStrength *= 0.35f;
			}

			if (lerp.isMode(LerpMode.Smoothstep)) {
				targetYaw = (float) smoothStepLerp(effectiveYawStrength, mc.player.getYaw(), (float) rotation.yaw());
				targetPitch = (float) smoothStepLerp(effectivePitchStrength, mc.player.getPitch(), (float) rotation.pitch());
			} else if (lerp.isMode(LerpMode.Normal)) {
				targetYaw = lerp(effectiveYawStrength, mc.player.getYaw(), (float) rotation.yaw());
				targetPitch = lerp(effectivePitchStrength, mc.player.getPitch(), (float) rotation.pitch());
			} else if (lerp.isMode(LerpMode.EaseOut)) {
				targetYaw = (float) easeOutBackDegrees(mc.player.getYaw(), rotation.yaw(), effectiveYawStrength);
				targetPitch = (float) easeOutBackDegrees(mc.player.getPitch(), rotation.pitch(), effectivePitchStrength);
			}
		}

		float desiredStepY = MathHelper.clamp(MathHelper.wrapDegrees(targetYaw - mc.player.getYaw()), -18.0f, 18.0f);
		float desiredStepP = MathHelper.clamp(targetPitch - mc.player.getPitch(), -14.0f, 14.0f);

		float maxAccelY = 4.5f;
		float maxAccelP = 3.5f;
		float accelY = MathHelper.clamp(desiredStepY - currentAimVelocityYaw, -maxAccelY, maxAccelY);
		float accelP = MathHelper.clamp(desiredStepP - currentAimVelocityPitch, -maxAccelP, maxAccelP);
		currentAimVelocityYaw += accelY;
		currentAimVelocityPitch += accelP;

		targetYaw = mc.player.getYaw() + currentAimVelocityYaw;
		targetPitch = mc.player.getPitch() + currentAimVelocityPitch;

		Rotation quantized = RotationUtils.applyGCD(mc.player.getYaw(), mc.player.getPitch(), targetYaw, targetPitch, gcdRemainder, true);

		if (move) {
			boolean applyingYaw = yawAssist.getValue() && (!stopAtTargetHorizontal.getValue() || !lookingAtTarget);
			boolean applyingPitch = pitchAssist.getValue() && (!stopAtTargetVertical.getValue() || !lookingAtTarget);

			if (applyingYaw) {
				mc.player.setYaw((float) quantized.yaw());
			} else if (applyingPitch && Math.abs(quantized.yaw() - mc.player.getYaw()) > 0.0001f) {
				mc.player.setYaw((float) quantized.yaw());
			}

			if (applyingPitch) {
				mc.player.setPitch((float) quantized.pitch());
			} else if (applyingYaw && Math.abs(quantized.pitch() - mc.player.getPitch()) > 0.0001f) {
				mc.player.setPitch((float) quantized.pitch());
			}
		}

		lastTrackedYaw = mc.player.getYaw();
		lastTrackedPitch = mc.player.getPitch();
	}

	public float lerp(float delta, float start, float end) {
		return start + (MathHelper.wrapDegrees(end - start) * delta);
	}

	public static double easeOutBackDegrees(double start, double end, float speed) {
		double c1 = 1.70158;
		double c3 = 2.70158;
		double x = 1 - Math.pow(1 - (double) speed, 3);

		return start + MathHelper.wrapDegrees(end - start) * (1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2));
	}

	public double smoothStepLerp(double delta, double start, double end) {
		delta = Math.max(0, Math.min(1, delta));
		double t = delta * delta * (3 - 2 * delta);
		return start + MathHelper.wrapDegrees(end - start) * t;
	}

	@Override
	public void onMouseMove(MouseMoveEvent event) {
		if (waitFor.getValueFloat() > 0) {
			move = false;
			timer.reset();
		}
	}

	private LivingEntity findTarget(float range) {
		if (mc.world == null || mc.player == null)
			return null;

		LivingEntity bestTarget = null;
		float minDistance = Float.MAX_VALUE;

		if (entities.isMode(EntityMode.PLAYERS_ONLY)) {
			for (PlayerEntity player : mc.world.getPlayers()) {
				if (player == mc.player || !player.isAlive()) continue;
				if (ignoreNoHP.getValue() && player.getHealth() <= 0.0F) continue;
				if (isFriend(player)) continue;

				float dist = mc.player.distanceTo(player);
				if (dist > range) continue;
				if (seeOnly.getValue() && !mc.player.canSee(player)) continue;

				if (dist < minDistance) {
					minDistance = dist;
					bestTarget = player;
				}
			}
		} else {
			for (Entity entity : mc.world.getEntities()) {
				if (entity == mc.player || !(entity instanceof LivingEntity living) || !living.isAlive() || living.isRemoved()) continue;
				if (ignoreNoHP.getValue() && living.getHealth() <= 0.0F) continue;
				if (living instanceof PlayerEntity player && isFriend(player)) continue;

				float dist = mc.player.distanceTo(living);
				if (dist > range) continue;
				if (seeOnly.getValue() && !mc.player.canSee(living)) continue;

				if (dist < minDistance) {
					minDistance = dist;
					bestTarget = living;
				}
			}
		}

		return bestTarget;
	}

	private boolean isFriend(PlayerEntity player) {
		Friends friends = dlindustries.uk.daduncs.System.INSTANCE.getModuleManager().getModule(Friends.class);
		return friends != null && friends.disableAimAssist.getValue() && dlindustries.uk.daduncs.System.INSTANCE.getFriendManager().isFriend(player);
	}
}