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

package dlindustries.uk.daduncs.module.modules.mace;

import dlindustries.uk.daduncs.event.events.HudListener;
import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bind;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.InventoryUtils;
import dlindustries.uk.daduncs.utils.KeyUtils;
import dlindustries.uk.daduncs.utils.RotationUtils;
import dlindustries.uk.daduncs.utils.rotation.Rotation;
import dlindustries.uk.daduncs.utils.rotation.SilentRot;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public final class Grappler extends Module implements TickListener, HudListener {

	private final bind activateKey = new bind(EncryptedString.of("Activate Key"), GLFW.GLFW_MOUSE_BUTTON_MIDDLE, false)
			.setDescription(EncryptedString.of("Key to activate pearl catching (default: Middle Mouse Button)"));

	private final bool silent = new bool(EncryptedString.of("Silent"), true)
			.setDescription(EncryptedString.of("Silently aim wind charge without flicking your screen"));

	private final bool switchBack = new bool(EncryptedString.of("Switch Back"), true)
			.setDescription(EncryptedString.of("Switch back to original hotbar slot after throwing"));

	private final number timeoutTicks = new number(EncryptedString.of("Timeout Ticks"), 10, 60, 30, 1)
			.setDescription(EncryptedString.of("Max ticks to track before timing out"));

	private int stage = 0;
	private int prevSlot = -1;
	private int startTick = -1;
	private int pearlThrownAge = -1;
	private int rotateStartAge = -1;
	private int stepCounter = 0;
	private int holdSilentTicks = -1;
	private int lastStepTick = -1;
	private int cachedPing = 50;

	private boolean wasKeyPressed = false;
	private boolean windChargeThrown = false;

	private Vec3d pearlStartPos = Vec3d.ZERO;
	private Vec3d pearlStartVel = Vec3d.ZERO;

	private float targetYaw = 0.0f;
	private float targetPitch = 0.0f;
	private float currentYaw = 0.0f;
	private float currentPitch = 0.0f;
	private float startRotYaw = 0.0f;
	private float startRotPitch = 0.0f;
	private float pearlThrowYaw = 0.0f;
	private double lastDistH = 0.0;
	private long flickStartTime = 0L;
	private final double[] gcdRemainder = new double[2];

	public Grappler() {
		super(EncryptedString.of("Grappler"),
				EncryptedString.of("Catches your ender pearl mid-air with a wind charge in any direction you look"),
				-1,
				Category.MACE);
		addSettings(activateKey, silent, switchBack, timeoutTicks);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		eventManager.add(HudListener.class, this);
		reset();
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		eventManager.remove(HudListener.class, this);
		holdSilentTicks = -1;
		SilentRot.stop(this);
		finish();
		super.onDisable();
	}

	@Override
	public void onRenderHud(HudEvent event) {
		if (mc.player == null || stage != 5 || silent.getValue())
			return;

		long elapsed = System.currentTimeMillis() - flickStartTime;
		double progress = Math.min(1.0, (double) elapsed / 50.0);
		double eased = RotationUtils.minimumJerk(progress);

		float interpYaw = startRotYaw + (float) (MathHelper.wrapDegrees(targetYaw - startRotYaw) * eased);
		float interpPitch = startRotPitch + (float) ((targetPitch - startRotPitch) * eased);

		Rotation rot = RotationUtils.applyGCD(mc.player.getYaw(), mc.player.getPitch(), interpYaw, interpPitch, gcdRemainder, true);
		mc.player.setYaw((float) rot.yaw());
		mc.player.setPitch((float) rot.pitch());
		currentYaw = (float) rot.yaw();
		currentPitch = (float) rot.pitch();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.world == null) {
			SilentRot.stop(this);
			reset();
			return;
		}

		if (mc.currentScreen != null) {
			if (stage != 0) {
				finish();
			}
			return;
		}

		boolean isKeyDown = KeyUtils.isKeyPressed(activateKey.getKey());
		if (stage == 0 && isKeyDown && !wasKeyPressed) {
			startCatchSequence();
		}
		wasKeyPressed = isKeyDown;

		if (stage != 0 && startTick >= 0 && mc.player.age - startTick > timeoutTicks.getValueInt()) {
			finish();
			return;
		}

		if (holdSilentTicks >= 0) {
			if (mc.player.age < holdSilentTicks) {
				if (silent.getValue()) {
					SilentRot.set(currentYaw, currentPitch, this);
				}
			} else {
				SilentRot.stop(this);
				holdSilentTicks = -1;
				currentYaw = 0.0f;
				currentPitch = 0.0f;
			}
		}

		switch (stage) {
			case 1: {
				if (!hasHotbarItems()) {
					finish();
					break;
				}
				if (AntibanbyDaduncs.isSlotPending()) {
					break;
				}
				InventoryUtils.selectItemFromHotbar(Items.ENDER_PEARL);
				stage = 2;
				break;
			}
			case 2: {
				if (!mc.player.getMainHandStack().isOf(Items.ENDER_PEARL)) {
					if (!AntibanbyDaduncs.isSlotPending()) {
						InventoryUtils.selectItemFromHotbar(Items.ENDER_PEARL);
					}
					return;
				}

				if (AntibanbyDaduncs.isActionSent() || AntibanbyDaduncs.isAttackSent() || AntibanbyDaduncs.isSlotPending()) {
					return;
				}

				if (mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND).isAccepted()) {
					mc.player.swingHand(Hand.MAIN_HAND);
				}

				Vec3d playerVel = mc.player.getVelocity();
				pearlStartPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
				Vec3d look = RotationUtils.getPlayerLookVec(mc.player.getYaw(), mc.player.getPitch());
				pearlStartVel = look.multiply(1.5).add(playerVel.x, mc.player.isOnGround() ? 0.0 : playerVel.y, playerVel.z);
				pearlThrownAge = mc.player.age;
				pearlThrowYaw = mc.player.getYaw();

				stage = 4;
				break;
			}
			case 4: {
				if (AntibanbyDaduncs.isActionSent() || AntibanbyDaduncs.isSlotPending()) {
					return;
				}
				if (!InventoryUtils.selectItemFromHotbar(Items.WIND_CHARGE)) {
					finish();
					break;
				}
				stage = 5;
				stepCounter = 0;
				windChargeThrown = false;
				rotateStartAge = -1;
				lastStepTick = -1;
				flickStartTime = System.currentTimeMillis();
				startRotYaw = mc.player.getYaw();
				startRotPitch = mc.player.getPitch();
				gcdRemainder[0] = 0;
				gcdRemainder[1] = 0;

				if (calculateTargetRotation()) {
					stepRotation();
				}
				break;
			}
			case 5: {
				handleAimAndFire();
				break;
			}
			case 6: {
				if (AntibanbyDaduncs.isActionSent() || AntibanbyDaduncs.isSlotPending()) {
					return;
				}
				finish();
				break;
			}
		}
	}

	public boolean isThrowing() {
		return stage != 0 || holdSilentTicks >= 0;
	}

	private void startCatchSequence() {
		if (!hasHotbarItems()) {
			return;
		}

		prevSlot = mc.player.getInventory().getSelectedSlot();
		pearlThrownAge = -1;
		windChargeThrown = false;
		startTick = mc.player.age;
		rotateStartAge = -1;
		stepCounter = 0;
		gcdRemainder[0] = 0;
		gcdRemainder[1] = 0;
		pearlThrowYaw = mc.player.getYaw();
		currentYaw = mc.player.getYaw();
		currentPitch = mc.player.getPitch();
		startRotYaw = mc.player.getYaw();
		startRotPitch = mc.player.getPitch();
		stage = 1;
	}

	private void handleAimAndFire() {
		if (windChargeThrown) {
			return;
		}
		if (!mc.player.getMainHandStack().isOf(Items.WIND_CHARGE)) {
			if (!AntibanbyDaduncs.isSlotPending()) {
				InventoryUtils.selectItemFromHotbar(Items.WIND_CHARGE);
			}
			return;
		}

		stepCounter++;

		if (calculateTargetRotation()) {
			stepRotation();

			boolean ageCheck = rotateStartAge >= 0 && mc.player.age > rotateStartAge;
			boolean pitchCheck = Math.abs(currentPitch - targetPitch) <= 4.0f;
			boolean yawCheck = (lastDistH < 0.08 || Math.abs(targetPitch) >= 80.0f || Math.abs(currentPitch) >= 80.0f)
					|| Math.abs(MathHelper.wrapDegrees(currentYaw - targetYaw)) <= 4.0f;
			boolean angleCheck = pitchCheck && yawCheck;

			if (ageCheck && angleCheck) {
				fireWindCharge(true);
				return;
			}
		}

		if (stepCounter > 20) {
			if (rotateStartAge >= 0 && mc.player.age > rotateStartAge) {
				fireWindCharge(true);
			} else if (rotateStartAge < 0) {
				fireWindCharge(false);
			}
		}
	}

	private void fireWindCharge(boolean aligned) {
		if (AntibanbyDaduncs.isActionSent() || AntibanbyDaduncs.isAttackSent() || AntibanbyDaduncs.isSlotPending()) {
			return;
		}

		if (silent.getValue()) {
			if (aligned) {
				float realYaw = mc.player.getYaw();
				float realPitch = mc.player.getPitch();

				mc.player.setYaw(currentYaw);
				mc.player.setPitch(currentPitch);
				SilentRot.set(currentYaw, currentPitch, this);

				if (mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND).isAccepted()) {
					mc.player.swingHand(Hand.MAIN_HAND);
				}

				mc.player.setYaw(realYaw);
				mc.player.setPitch(realPitch);
				holdSilentTicks = mc.player.age + 2;
			} else {
				if (mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND).isAccepted()) {
					mc.player.swingHand(Hand.MAIN_HAND);
				}
			}
		} else {
			mc.player.setYaw(currentYaw);
			mc.player.setPitch(currentPitch);

			if (mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND).isAccepted()) {
				mc.player.swingHand(Hand.MAIN_HAND);
			}
		}

		windChargeThrown = true;
		stage = 6;
	}

	private void stepRotation() {
		if (lastStepTick == mc.player.age) {
			if (silent.getValue()) {
				SilentRot.set(currentYaw, currentPitch, this);
			}
			return;
		}
		lastStepTick = mc.player.age;

		double sens = mc.options != null && mc.options.getMouseSensitivity() != null
				? mc.options.getMouseSensitivity().getValue() : 0.5;
		double fSens = sens * 0.6 + 0.2;
		float f = (float) (fSens * fSens * fSens * 8.0 * 0.15);
		int maxSteps = Math.max(1, (int) (60.0f / f));

		float startY = rotateStartAge >= 0 ? currentYaw : mc.player.getYaw();
		float startP = rotateStartAge >= 0 ? currentPitch : MathHelper.clamp(mc.player.getPitch(), -90.0f, 90.0f);

		float diffY = MathHelper.wrapDegrees(targetYaw - startY);
		int nYaw = Math.round(diffY / f);
		if (nYaw > maxSteps) nYaw = maxSteps;
		else if (nYaw < -maxSteps) nYaw = -maxSteps;
		currentYaw = startY + (float) nYaw * f;

		float diffP = targetPitch - startP;
		int nPitch = Math.round(diffP / f);
		if (nPitch > maxSteps) nPitch = maxSteps;
		else if (nPitch < -maxSteps) nPitch = -maxSteps;

		int maxUp = (int) Math.floor((90.0f - startP) / f);
		int maxDown = (int) Math.ceil((-90.0f - startP) / f);
		if (nPitch > maxUp) nPitch = maxUp;
		if (nPitch < maxDown) nPitch = maxDown;
		currentPitch = MathHelper.clamp(startP + (float) nPitch * f, -90.0f, 90.0f);

		if (rotateStartAge < 0) {
			rotateStartAge = mc.player.age;
		}

		if (silent.getValue()) {
			SilentRot.set(currentYaw, currentPitch, this);
		} else {
			mc.player.setYaw(currentYaw);
			mc.player.setPitch(currentPitch);
		}
	}

	private boolean calculateTargetRotation() {
		if (mc.world == null || mc.player == null || pearlThrownAge < 0) {
			return false;
		}

		double pingTicks = getPingTicks();
		Vec3d playerVel = mc.player.getVelocity();
		double vy = playerVel.y;
		if (!mc.player.isGliding()) {
			int n = (int) pingTicks;
			for (int i = 0; i < n; ++i) {
				vy = (vy - 0.08) * 0.98;
			}
			double rem = pingTicks - (double) n;
			if (rem > 0.0) {
				vy += ((vy - 0.08) * 0.98 - vy) * rem;
			}
		}

		Vec3d predictedPlayerVel = new Vec3d(playerVel.x, mc.player.isOnGround() ? 0.0 : vy, playerVel.z);
		Vec3d predictedPlayerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ())
				.add(playerVel.x * pingTicks, playerVel.y * pingTicks, playerVel.z * pingTicks);

		double elapsedPearlTicks = (double) (mc.player.age - pearlThrownAge) + pingTicks;
		if (elapsedPearlTicks < 0.0) {
			elapsedPearlTicks = 0.0;
		}

		Vec3d pearlPos = pearlStartPos;
		Vec3d pearlVel = pearlStartVel;
		int n = (int) elapsedPearlTicks;
		for (int i = 0; i < n; ++i) {
			pearlVel = new Vec3d(pearlVel.x, pearlVel.y - 0.03, pearlVel.z).multiply(0.99);
			pearlPos = pearlPos.add(pearlVel);
		}
		double rem = elapsedPearlTicks - (double) n;
		if (rem > 0.0) {
			Vec3d nextVel = new Vec3d(pearlVel.x, pearlVel.y - 0.03, pearlVel.z).multiply(0.99);
			pearlPos = pearlPos.add(nextVel.multiply(rem));
		}

		double interceptTime = solveInterceptTime(predictedPlayerPos, predictedPlayerVel, pearlPos, pearlVel);
		if (interceptTime <= 0.0) {
			return false;
		}

		Vec3d futurePearlPos = predictPearlPos(pearlPos, pearlVel, interceptTime);
		futurePearlPos = new Vec3d(futurePearlPos.x, futurePearlPos.y - 0.2, futurePearlPos.z);

		Vec3d diff = futurePearlPos.subtract(predictedPlayerPos).subtract(predictedPlayerVel.multiply(interceptTime));
		if (diff.lengthSquared() < 1.0E-6) {
			return false;
		}

		double yaw = Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0;
		double distH = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
		double pitch = -Math.toDegrees(Math.atan2(diff.y, distH));

		lastDistH = distH;
		targetPitch = MathHelper.clamp((float) pitch, -90.0f, 90.0f);
		if (distH < 0.08 || Math.abs(targetPitch) >= 80.0f) {
			targetYaw = pearlThrownAge >= 0 ? pearlThrowYaw : mc.player.getYaw();
		} else {
			targetYaw = (float) yaw;
		}
		return true;
	}

	private double solveInterceptTime(Vec3d playerPos, Vec3d playerVel, Vec3d pearlPos, Vec3d pearlVel) {
		Vec3d currentPearlPos = pearlPos;
		Vec3d currentPearlVel = pearlVel;
		double dist = pearlPos.subtract(playerPos).length();

		for (int i = 1; i <= 100; ++i) {
			currentPearlVel = new Vec3d(currentPearlVel.x, currentPearlVel.y - 0.03, currentPearlVel.z).multiply(0.99);
			currentPearlPos = currentPearlPos.add(currentPearlVel);
			double windChargeDist = 1.5 * Math.max(0.0, (double) i - 1.0);
			double currentDist = currentPearlPos.subtract(playerPos).subtract(playerVel.multiply((double) i)).length() - windChargeDist;
			if (currentDist <= 0.0) {
				double d4 = dist - currentDist;
				double d5 = d4 <= 1.0E-9 ? 0.0 : dist / d4;
				return (double) (i - 1) + d5;
			}
			dist = currentDist;
		}
		return 0.0;
	}

	private Vec3d predictPearlPos(Vec3d startPos, Vec3d startVel, double ticks) {
		int n = (int) Math.floor(ticks);
		Vec3d pos = startPos;
		Vec3d vel = startVel;
		for (int i = 0; i < n; ++i) {
			vel = new Vec3d(vel.x, vel.y - 0.03, vel.z).multiply(0.99);
			pos = pos.add(vel);
		}
		double rem = ticks - (double) n;
		if (rem > 0.0) {
			vel = new Vec3d(vel.x, vel.y - 0.03, vel.z).multiply(0.99);
			pos = pos.add(vel.multiply(rem));
		}
		return pos;
	}

	private double getPingTicks() {
		try {
			if (mc.getNetworkHandler() != null && mc.player != null) {
				PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
				if (entry != null && entry.getLatency() > 0) {
					cachedPing = Math.min(entry.getLatency(), 400);
				}
			}
		} catch (Throwable ignored) {
		}
		return (double) cachedPing / 50.0;
	}

	private boolean hasHotbarItems() {
		if (mc.player == null) return false;
		return InventoryUtils.hasItemInHotbar(i -> i == Items.ENDER_PEARL)
				&& InventoryUtils.hasItemInHotbar(i -> i == Items.WIND_CHARGE);
	}

	private void finish() {
		if (switchBack.getValue() && prevSlot >= 0 && mc.player != null) {
			if (!AntibanbyDaduncs.isActionSent() && !AntibanbyDaduncs.isSlotPending()) {
				InventoryUtils.setInvSlot(prevSlot);
			}
		}
		if (holdSilentTicks < 0) {
			SilentRot.stop(this);
		}
		reset();
	}

	private void reset() {
		stage = 0;
		prevSlot = -1;
		startTick = -1;
		pearlThrownAge = -1;
		rotateStartAge = -1;
		stepCounter = 0;
		lastStepTick = -1;
		windChargeThrown = false;
		pearlStartPos = Vec3d.ZERO;
		pearlStartVel = Vec3d.ZERO;
		targetYaw = 0.0f;
		targetPitch = 0.0f;
		startRotYaw = 0.0f;
		startRotPitch = 0.0f;
		pearlThrowYaw = 0.0f;
		lastDistH = 0.0;
		flickStartTime = 0L;
		gcdRemainder[0] = 0;
		gcdRemainder[1] = 0;
		if (holdSilentTicks < 0) {
			currentYaw = 0.0f;
			currentPitch = 0.0f;
		}
	}
}