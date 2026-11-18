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
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.item.WindChargeItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

public final class Grappler extends Module implements TickListener, HudListener {

	private static final int MAX_INTERCEPT_TICKS = 100;
	private static final double WIND_BOX_Y_OFFSET = -0.15F;
	// Sample both single-axis and combined deviations from vanilla's launch direction.
	private static final Vec3d[] WIND_SPREAD_SAMPLES = {
			Vec3d.ZERO, new Vec3d(0.017, 0, 0), new Vec3d(-0.017, 0, 0),
			new Vec3d(0, 0.017, 0), new Vec3d(0, -0.017, 0),
			new Vec3d(0, 0, 0.017), new Vec3d(0, 0, -0.017),
			new Vec3d(0.010, 0.010, 0.010), new Vec3d(0.010, 0.010, -0.010),
			new Vec3d(0.010, -0.010, 0.010), new Vec3d(0.010, -0.010, -0.010),
			new Vec3d(-0.010, 0.010, 0.010), new Vec3d(-0.010, 0.010, -0.010),
			new Vec3d(-0.010, -0.010, 0.010), new Vec3d(-0.010, -0.010, -0.010)
	};
	private static final int NOMINAL_TIMING_WEIGHT = 5;

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
	private int latestPearlIdBeforeThrow = -1;

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
	public void onPlayerChanged() {
		holdSilentTicks = -1;
		reset();
	}

	@Override
	public void onRenderHud(HudEvent event) {
		if (!SilentRot.isAvailableTo(this)) return;
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
			holdSilentTicks = -1;
			SilentRot.stop(this);
			reset();
			return;
		}

		if (mc.currentScreen != null) {
			holdSilentTicks = -1;
			SilentRot.stop(this);
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

		if (!SilentRot.isAvailableTo(this)) return;
		switch (stage) {
			case 1: {
				if (!hasHotbarItems()) {
					finish();
					break;
				}
				if (mc.player.isUsingItem() || !AntibanbyDaduncs.canSwapSlotNow() || AntibanbyDaduncs.isSlotPending()) {
					break;
				}
				if (InventoryUtils.selectItemFromHotbar(Items.ENDER_PEARL)) {
					stage = 2;
				}
				break;
			}
			case 2: {
				if (!mc.player.getMainHandStack().isOf(Items.ENDER_PEARL)) {
					if (!mc.player.isUsingItem() && AntibanbyDaduncs.canSwapSlotNow() && !AntibanbyDaduncs.isSlotPending()) {
						InventoryUtils.selectItemFromHotbar(Items.ENDER_PEARL);
					}
					return;
				}

				if (mc.player.isUsingItem() || !AntibanbyDaduncs.canUseItemNow(Hand.MAIN_HAND)
						|| mc.player.getItemCooldownManager().isCoolingDown(mc.player.getMainHandStack())) {
					return;
				}

				if (!dlindustries.uk.daduncs.utils.WorldUtils.useItem(mc.player, Hand.MAIN_HAND)) return;

				EnderPearlEntity previousPearl = findLatestOwnedPearl();
				latestPearlIdBeforeThrow = previousPearl == null ? -1 : previousPearl.getId();
				pearlStartPos = mc.player.getEyePos().add(0.0, -0.1F, 0.0);
				Vec3d look = RotationUtils.getPlayerLookVec(mc.player.getYaw(), mc.player.getPitch());
				pearlStartVel = look.multiply(1.5).add(getLaunchMovement());
				pearlThrownAge = mc.player.age;
				pearlThrowYaw = mc.player.getYaw();

				stage = 4;
				break;
			}
			case 4: {
				if (mc.player.isUsingItem() || !AntibanbyDaduncs.canSwapSlotNow() || AntibanbyDaduncs.isSlotPending()) {
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
			if (!mc.player.isUsingItem() && AntibanbyDaduncs.canSwapSlotNow() && !AntibanbyDaduncs.isSlotPending()) {
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
		if (mc.player.isUsingItem() || !AntibanbyDaduncs.canUseItemNow(Hand.MAIN_HAND)
				|| mc.player.getItemCooldownManager().isCoolingDown(mc.player.getMainHandStack())) {
			return;
		}

		if (silent.getValue()) {
			if (aligned) SilentRot.set(currentYaw, currentPitch, this);
		} else {
			mc.player.setYaw(currentYaw);
			mc.player.setPitch(currentPitch);
		}

		boolean used = dlindustries.uk.daduncs.utils.WorldUtils.useItem(mc.player, Hand.MAIN_HAND);
		if (used && aligned && silent.getValue()) holdSilentTicks = mc.player.age + 2;

		if (!used) return;
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

		// Both uses follow their movement packets, so their relative launch time is the tick gap.
		int pearlAge = Math.max(0, mc.player.age - pearlThrownAge);
		int elapsedTicks = pearlAge;
		Vec3d pearlPos = pearlStartPos;
		Vec3d pearlVel = pearlStartVel;
		EnderPearlEntity pearl = findLatestOwnedPearl();
		if (pearl != null && pearl.getId() > latestPearlIdBeforeThrow && pearl.age <= elapsedTicks) {
			// Use the spawned pearl to account for the random spread applied at launch.
			pearlPos = pearl.getEntityPos();
			pearlVel = pearl.getVelocity();
			elapsedTicks -= pearl.age;
		}
		for (int i = 0; i < elapsedTicks; i++) {
			pearlVel = nextPearlVelocity(pearlVel);
			pearlPos = pearlPos.add(pearlVel);
		}

		Vec3d aim = solveInterceptDirection(mc.player.getEyePos(), getLaunchMovement(), pearlPos, pearlVel,
				pearlAge, WindChargeItem.POWER, EntityType.WIND_CHARGE.getDimensions());
		if (aim == null) {
			return false;
		}

		lastDistH = aim.horizontalLength();
		targetPitch = (float) -Math.toDegrees(Math.atan2(aim.y, lastDistH));
		targetYaw = lastDistH < 1.0E-6 ? pearlThrowYaw : (float) (Math.toDegrees(Math.atan2(aim.z, aim.x)) - 90.0);
		return true;
	}

	private EnderPearlEntity findLatestOwnedPearl() {
		EnderPearlEntity latest = null;
		for (Entity entity : mc.world.getEntities()) {
			if (entity instanceof EnderPearlEntity pearl && pearl.getOwner() == mc.player
					&& (latest == null || pearl.getId() > latest.getId())) {
				latest = pearl;
			}
		}
		return latest;
	}

	private Vec3d getLaunchMovement() {
		Vec3d movement = mc.player.getEntityPos().subtract(mc.player.getLastRenderPos());
		return mc.player.isOnGround() ? new Vec3d(movement.x, 0.0, movement.z) : movement;
	}

	private static Vec3d nextPearlVelocity(Vec3d velocity) {
		return velocity.add(0.0, -0.03, 0.0).multiply(0.99F);
	}

	private Vec3d solveInterceptDirection(Vec3d origin, Vec3d movement, Vec3d pearlPos, Vec3d pearlVel,
			int pearlAge, double speed, EntityDimensions windSize) {
		// Index 1 is the pearl at charge launch. The adjacent phases model a one-tick packet timing shift.
		Vec3d[] path = new Vec3d[MAX_INTERCEPT_TICKS + 4];
		path[1] = pearlPos.subtract(origin);
		path[0] = path[1].subtract(pearlVel);
		int lastPoint = 1;
		for (int i = 2; i < path.length; i++) {
			pearlVel = nextPearlVelocity(pearlVel);
			Vec3d nextPearlPos = pearlPos.add(pearlVel);
			BlockHitResult impact = traceBlocks(pearlPos, nextPearlPos);
			path[i] = impact.getPos().subtract(origin);
			lastPoint = i;
			if (impact.getType() != HitResult.Type.MISS) break;
			pearlPos = nextPearlPos;
		}
		Box windBox = windSize.getBoxAt(new Vec3d(0.0, WIND_BOX_Y_OFFSET, 0.0));
		Box[] hitboxes = new Box[lastPoint];
		for (int i = 0; i < hitboxes.length; i++) {
			hitboxes[i] = windBox.expand(MathHelper.clamp((pearlAge + i - 2) / 20.0F, 0.0F, 0.3F));
		}
		double centerY = WIND_BOX_Y_OFFSET + windSize.height() * 0.5;
		double maxRadiusError = windBox.getCenter().distanceTo(new Vec3d(windBox.maxX, windBox.maxY, windBox.maxZ))
				+ Math.sqrt(3.0) * 0.3;
		Vec3d bestAim = null;
		int bestScore = 0;

		// The pearl sweeps its segment against the wind charge's current box, before the charge moves.
		int lastCandidateTick = Math.min(MAX_INTERCEPT_TICKS, lastPoint - 2);
		for (int tick = 1; tick <= lastCandidateTick; tick++) {
			Vec3d inheritedOffset = movement.multiply(tick).add(0.0, centerY, 0.0);
			Vec3d start = path[tick + 1].subtract(inheritedOffset);
			Vec3d end = path[tick + 2].subtract(inheritedOffset);
			double reach = speed * tick;
			for (int sample = 0; sample <= 8; sample++) {
				Vec3d aim = start.lerp(end, sample / 8.0);
				double distance = aim.length();
				if (Math.abs(distance - reach) > maxRadiusError) continue;

				Vec3d velocity = aim.multiply(speed / distance).add(movement);
				int hitTick = findPearlHit(path, hitboxes, velocity, tick, 0);
				if (hitTick < 0) continue;
				int score = 0;
				for (Vec3d spread : WIND_SPREAD_SAMPLES) {
					Vec3d sampledVelocity = velocity.add(spread);
					if (findPearlHit(path, hitboxes, sampledVelocity, tick, 0) >= 0) score += NOMINAL_TIMING_WEIGHT;
					if (findPearlHit(path, hitboxes, sampledVelocity, tick, -1) >= 0) score++;
					if (findPearlHit(path, hitboxes, sampledVelocity, tick, 1) >= 0) score++;
				}
				if (score > bestScore && traceBlocks(origin, origin.add(velocity.multiply(hitTick))).getType() == HitResult.Type.MISS) {
					bestScore = score;
					bestAim = aim;
					if (score == WIND_SPREAD_SAMPLES.length * (NOMINAL_TIMING_WEIGHT + 2)) return bestAim;
				}
			}
		}
		return bestAim;
	}

	private BlockHitResult traceBlocks(Vec3d start, Vec3d end) {
		return mc.world.getCollisionsIncludingWorldBorder(new RaycastContext(start, end,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
	}

	private static int findPearlHit(Vec3d[] path, Box[] hitboxes, Vec3d velocity, int nearTick, int phase) {
		int lastTick = Math.min(Math.min(MAX_INTERCEPT_TICKS, nearTick + 4), hitboxes.length - 2 - phase);
		for (int tick = Math.max(0, nearTick - 4); tick <= lastTick; tick++) {
			int index = tick + 1 + phase;
			Vec3d charge = velocity.multiply(tick);
			if (hitboxes[index].raycast(path[index].subtract(charge), path[index + 1].subtract(charge)).isPresent()) {
				return tick;
			}
		}
		return -1;
	}

	private boolean hasHotbarItems() {
		if (mc.player == null) return false;
		return InventoryUtils.hasItemInHotbar(i -> i == Items.ENDER_PEARL)
				&& InventoryUtils.hasItemInHotbar(i -> i == Items.WIND_CHARGE);
	}

	private void finish() {
		if (switchBack.getValue() && prevSlot >= 0 && mc.player != null) {
			if (!mc.player.isUsingItem() && AntibanbyDaduncs.canSwapSlotNow() && !AntibanbyDaduncs.isSlotPending()) {
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
