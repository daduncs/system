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

package dlindustries.uk.daduncs.module.modules.misc;

import dlindustries.uk.daduncs.event.events.HudListener;
import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bind;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.mode;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.InventoryUtils;
import dlindustries.uk.daduncs.utils.KeyUtils;
import dlindustries.uk.daduncs.utils.RotationUtils;
import dlindustries.uk.daduncs.utils.rotation.Rotation;
import dlindustries.uk.daduncs.utils.rotation.SilentRot;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;

public final class KeyPearl extends Module implements TickListener, HudListener {

	public enum Mode {
		Fov,
		Distance
	}

	private final bind activateKey = new bind(EncryptedString.of("Activate Key"), -1, false);
	private final bind aggroPearlKey = new bind(EncryptedString.of("Aggro Pearl Key"), -1, false);

	private final mode<Mode> mode = new mode<>(EncryptedString.of("Mode"), Mode.Fov, Mode.class);

	private final bool silent = new bool(EncryptedString.of("Silent"), true)
			.setDescription(EncryptedString.of("Silently aim aggro pearl without flicking your screen"));

	private final number distance = new number(EncryptedString.of("Distance"), -5.0, 5.0, 1.0, 0.1)
			.setTag(EncryptedString.of("read desc"))
			.setShowDescription(true)
			.setDescription(EncryptedString.of("preferred landing distance between you and the opponent after throwing an aggro pearl"));

	private final bool predict = new bool(EncryptedString.of("Predict"), true)
			.setTag(EncryptedString.of("read desc"))
			.setShowDescription(true)
			.setDescription(EncryptedString.of("predicts where target will go based on target velocity"));

	private final bool switchBack = new bool(EncryptedString.of("Switch Back"), true);
	private final number switchslot = new number(EncryptedString.of("Switch Slot"), 1, 9, 1, 1)
			.setDescription(EncryptedString.of("The slot it returns to after pearling (e.g., your sword slot)"));

	private int stage = 0;
	private int startTick = -1;
	private int rotateStartAge = -1;
	private int stepCounter = 0;
	private int holdSilentTicks = -1;
	private int lastStepTick = -1;

	private boolean wasNormalPressed = false;
	private boolean wasAggroPressed = false;
	private boolean isAggro = false;

	private PlayerEntity aggroTarget;

	private float targetYaw = 0.0f;
	private float targetPitch = 0.0f;
	private float currentYaw = 0.0f;
	private float currentPitch = 0.0f;
	private float startRotYaw = 0.0f;
	private float startRotPitch = 0.0f;
	private long flickStartTime = 0L;
	private final double[] gcdRemainder = new double[2];

	public KeyPearl() {
		super(EncryptedString.of("Key Pearl"),
				EncryptedString.of("Switches to an ender pearl and throws it when you press a bind"),
				-1,
				Category.MISC);
		addSettings(activateKey, aggroPearlKey, mode, silent, distance, predict, switchBack, switchslot);
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
		if (mc.player == null || stage != 2 || !isAggro || silent.getValue())
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

		boolean isNormalDown = KeyUtils.isKeyPressed(activateKey.getKey());
		boolean isAggroDown = KeyUtils.isKeyPressed(aggroPearlKey.getKey());

		if (stage == 0 && isNormalDown && !wasNormalPressed) {
			startNormalSequence();
		} else if (stage == 0 && isAggroDown && !wasAggroPressed) {
			startAggroSequence();
		}

		wasNormalPressed = isNormalDown;
		wasAggroPressed = isAggroDown;

		if (stage != 0 && startTick >= 0 && mc.player.age - startTick > 30) {
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
				if (!hasPearlInHotbar()) {
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

				if (!isAggro) {
					if (AntibanbyDaduncs.isActionSent() || AntibanbyDaduncs.isAttackSent() || AntibanbyDaduncs.isSlotPending()) {
						return;
					}

					if (mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND).isAccepted()) {
						mc.player.swingHand(Hand.MAIN_HAND);
					}
					stage = 3;
					return;
				}

				stepCounter++;

				if (aggroTarget != null && aggroTarget.isAlive()) {
					double[] angles = calculatePearlAngles(aggroTarget);
					if (angles != null) {
						targetYaw = (float) angles[0];
						targetPitch = (float) angles[1];
					}
				}

				stepRotation();

				boolean ageCheck = rotateStartAge >= 0 && mc.player.age > rotateStartAge;
				boolean pitchCheck = Math.abs(currentPitch - targetPitch) <= 4.0f;
				boolean yawCheck = Math.abs(MathHelper.wrapDegrees(currentYaw - targetYaw)) <= 4.0f;
				boolean angleCheck = pitchCheck && yawCheck;

				if (ageCheck && angleCheck) {
					firePearl(true);
					return;
				}

				if (stepCounter > 20) {
					if (rotateStartAge >= 0 && mc.player.age > rotateStartAge) {
						firePearl(true);
					} else if (rotateStartAge < 0) {
						firePearl(false);
					}
				}
				break;
			}
			case 3: {
				if (AntibanbyDaduncs.isActionSent() || AntibanbyDaduncs.isSlotPending()) {
					return;
				}

				if (switchBack.getValue() && mc.player != null) {
					int slot = Math.max(1, Math.min(9, switchslot.getValueInt())) - 1;
					InventoryUtils.setInvSlot(slot);
				}
				finish();
				break;
			}
		}
	}

	private void startNormalSequence() {
		if (mc.player == null || !hasPearlInHotbar()) {
			return;
		}
		isAggro = false;
		startTick = mc.player.age;
		stage = 1;
	}

	private void startAggroSequence() {
		if (mc.player == null || !hasPearlInHotbar()) {
			return;
		}

		PlayerEntity target = findTarget();
		if (target == null) {
			return;
		}

		double[] angles = calculatePearlAngles(target);
		if (angles == null) {
			return;
		}

		aggroTarget = target;
		isAggro = true;
		startTick = mc.player.age;
		targetYaw = (float) angles[0];
		targetPitch = (float) angles[1];
		currentYaw = mc.player.getYaw();
		currentPitch = mc.player.getPitch();
		startRotYaw = mc.player.getYaw();
		startRotPitch = mc.player.getPitch();
		rotateStartAge = -1;
		stepCounter = 0;
		lastStepTick = -1;
		flickStartTime = System.currentTimeMillis();
		gcdRemainder[0] = 0;
		gcdRemainder[1] = 0;
		stage = 1;
	}

	private void firePearl(boolean aligned) {
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

		stage = 3;
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

	private PlayerEntity findTarget() {
		if (mc.world == null || mc.player == null) return null;

		PlayerEntity bestTarget = null;
		double bestMetric = Double.MAX_VALUE;

		for (PlayerEntity player : mc.world.getPlayers()) {
			if (player == mc.player || !player.isAlive()) continue;
			if (player.getHealth() <= 0.0F) continue;
			if (dlindustries.uk.daduncs.System.INSTANCE.getFriendManager().isFriend(player)) continue;

			double dist = mc.player.distanceTo(player);
			if (dist > 55.0) continue;

			double metric;
			if (mode.isMode(Mode.Fov)) {
				Rotation rot = RotationUtils.getDirection(mc.player.getEyePos(), player.getBoundingBox().getCenter());
				metric = RotationUtils.getAngleToRotation(rot);
			} else {
				metric = dist;
			}

			if (metric < bestMetric) {
				double[] angles = calculatePearlAngles(player);
				if (angles != null) {
					bestMetric = metric;
					bestTarget = player;
				}
			}
		}

		return bestTarget;
	}

	private double[] calculatePearlAngles(PlayerEntity target) {
		if (target == null || !target.isAlive() || mc.player == null) return null;

		Vec3d startPos = new Vec3d(mc.player.getX(), mc.player.getEyeY() - 0.1, mc.player.getZ());
		Vec3d playerVel = mc.player.getVelocity();
		Vec3d throwVel = new Vec3d(playerVel.x, mc.player.isOnGround() ? 0.0 : playerVel.y, playerVel.z);

		double flightTicks = Math.max(10.0, mc.player.distanceTo(target) / 1.5);

		int passes = predict.getValue() ? 3 : 1;
		double[] res = null;

		for (int pass = 0; pass < passes; pass++) {
			Vec3d targetPos = simulateTargetTrajectory(target, (int) Math.round(flightTicks));

			double dx = targetPos.x - mc.player.getX();
			double dz = targetPos.z - mc.player.getZ();
			double horizDist = Math.hypot(dx, dz);
			if (horizDist < 0.5) return null;

			double dirX = dx / horizDist;
			double dirZ = dz / horizDist;

			double landingDist = Math.max(0.5, horizDist - distance.getValue());
			double landX = mc.player.getX() + dirX * landingDist;
			double landZ = mc.player.getZ() + dirZ * landingDist;
			double landGroundY = getGroundYAt(landX, targetPos.y, landZ);

			res = solvePearlAngles(startPos, throwVel, landX, landGroundY, landZ);
			if (res == null) return null;

			flightTicks = res[2];
		}

		return res != null ? new double[]{res[0], res[1]} : null;
	}

	private int getBlastProtectionLevel(PlayerEntity player) {
		if (player == null) return 0;
		int total = 0;
		for (EquipmentSlot slot : new EquipmentSlot[]{
				EquipmentSlot.HEAD,
				EquipmentSlot.CHEST,
				EquipmentSlot.LEGS,
				EquipmentSlot.FEET}) {
			ItemStack stack = player.getEquippedStack(slot);
			if (stack == null || stack.isEmpty()) continue;
			for (var entry : stack.getEnchantments().getEnchantments()) {
				if (entry.matchesKey(Enchantments.BLAST_PROTECTION)) {
					total += stack.getEnchantments().getLevel(entry);
				}
			}
		}
		return total;
	}

	private double getKnockbackResistance(PlayerEntity player) {
		if (player == null) return 0.0;
		return player.getAttributeValue(EntityAttributes.KNOCKBACK_RESISTANCE);
	}

	private Vec3d simulateTargetTrajectory(PlayerEntity target, int ticks) {
		if (target == null) return mc.player != null ? new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()) : Vec3d.ZERO;
		if (!predict.getValue() || ticks <= 0) {
			return new Vec3d(target.getX(), target.getY(), target.getZ());
		}

		Vec3d pos = new Vec3d(target.getX(), target.getY(), target.getZ());
		Vec3d vel = target.getVelocity();

		int blastLevel = getBlastProtectionLevel(target);
		double blastReduction = Math.min(0.85, blastLevel * 0.15);
		double kbResist = Math.min(0.85, getKnockbackResistance(target));
		double protectionFactor = (1.0 - blastReduction) * (1.0 - kbResist);

		boolean isExplosionLaunch = target.hurtTime >= 8 || vel.horizontalLength() > 0.40 || Math.abs(vel.y) > 0.45;

		if (mc.world != null) {
			for (Entity e : mc.world.getEntities()) {
				if (e instanceof EndCrystalEntity crystal) {
					double d = crystal.distanceTo(target);
					if (d <= 6.0 && (crystal.isRemoved() || crystal.age <= 2)) {
						isExplosionLaunch = true;
						if (vel.lengthSquared() < 0.05) {
							Vec3d crystalPos = new Vec3d(crystal.getX(), crystal.getY(), crystal.getZ());
							Vec3d dir = target.getEyePos().subtract(crystalPos).normalize();
							double impact = (1.0 - d / 12.0);
							vel = dir.multiply(impact * 1.5 * protectionFactor);
						}
						break;
					}
				}
			}
		}

		double currX = pos.x;
		double currY = pos.y;
		double currZ = pos.z;
		double currVx = vel.x;
		double currVy = vel.y;
		double currVz = vel.z;

		boolean onGround = target.isOnGround();
		boolean isKnockback = isExplosionLaunch || target.hurtTime > 0;

		for (int t = 1; t <= ticks; t++) {
			if (onGround) {
				double slipperiness = getBlockSlipperiness(currX, currY - 0.1, currZ);
				double friction = slipperiness * 0.91;

				if (isKnockback) {
					currVx *= friction;
					currVz *= friction;
					if (Math.hypot(currVx, currVz) < 0.06) {
						currVx = 0;
						currVz = 0;
						isKnockback = false;
					}
				}
				if (currVy <= 0) {
					currVy = 0;
				}
			} else {
				currVx *= 0.91;
				currVz *= 0.91;
				currVy = (currVy - 0.08) * 0.98;
				currVy = Math.max(-3.92, currVy);
			}

			if (isPositionInWater(currX, currY, currZ)) {
				currVx *= 0.80;
				currVz *= 0.80;
				currVy = (currVy - 0.02) * 0.80;
				onGround = false;
			} else if (isPositionInCobweb(currX, currY, currZ)) {
				currVx *= 0.25;
				currVz *= 0.25;
				currVy *= 0.05;
			}

			double nextY = currY + currVy;
			Box boxY = new Box(currX - 0.3, nextY, currZ - 0.3, currX + 0.3, nextY + 1.8, currZ + 0.3);
			if (hasBlockCollision(boxY)) {
				if (currVy < 0) {
					currY = getGroundYAt(currX, currY, currZ);
					currVy = 0;
					onGround = true;
				} else if (currVy > 0) {
					currVy = 0;
				}
			} else {
				currY = nextY;
				Box stepCheck = new Box(currX - 0.3, currY - 0.05, currZ - 0.3, currX + 0.3, currY, currZ + 0.3);
				onGround = hasBlockCollision(stepCheck);
			}

			double nextX = currX + currVx;
			Box boxX = new Box(nextX - 0.3, currY, currZ - 0.3, nextX + 0.3, currY + 1.8, currZ + 0.3);
			if (hasBlockCollision(boxX)) {
				currVx = 0;
			} else {
				currX = nextX;
			}

			double nextZ = currZ + currVz;
			Box boxZ = new Box(currX - 0.3, currY, nextZ - 0.3, currX + 0.3, currY + 1.8, nextZ + 0.3);
			if (hasBlockCollision(boxZ)) {
				currVz = 0;
			} else {
				currZ = nextZ;
			}
		}

		return new Vec3d(currX, currY, currZ);
	}

	private double getBlockSlipperiness(double x, double y, double z) {
		if (mc.world == null) return 0.6;
		BlockPos pos = new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
		return mc.world.getBlockState(pos).getBlock().getSlipperiness();
	}

	private boolean isPositionInWater(double x, double y, double z) {
		if (mc.world == null) return false;
		BlockPos pos = new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
		return mc.world.getFluidState(pos).isIn(FluidTags.WATER);
	}

	private boolean isPositionInCobweb(double x, double y, double z) {
		if (mc.world == null) return false;
		BlockPos pos = new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
		return mc.world.getBlockState(pos).isOf(Blocks.COBWEB);
	}

	private boolean hasBlockCollision(Box box) {
		if (mc.world == null) return false;
		int minX = MathHelper.floor(box.minX);
		int maxX = MathHelper.floor(box.maxX);
		int minY = MathHelper.floor(box.minY);
		int maxY = MathHelper.floor(box.maxY);
		int minZ = MathHelper.floor(box.minZ);
		int maxZ = MathHelper.floor(box.maxZ);

		BlockPos.Mutable mutable = new BlockPos.Mutable();
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					mutable.set(x, y, z);
					var state = mc.world.getBlockState(mutable);
					if (state.isAir()) continue;
					var shape = state.getCollisionShape(mc.world, mutable);
					if (shape.isEmpty()) continue;
					List<Box> boxes = shape.getBoundingBoxes();
					for (Box blockBox : boxes) {
						if (box.intersects(blockBox.offset(mutable))) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	private double getGroundYAt(double x, double startY, double z) {
		if (mc.world == null) return startY;
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		int by = (int) Math.floor(startY + 1.5);
		for (int y = by; y >= by - 20 && y >= mc.world.getBottomY(); y--) {
			BlockPos check = new BlockPos(bx, y, bz);
			var state = mc.world.getBlockState(check);
			if (!state.isAir() && !state.getCollisionShape(mc.world, check).isEmpty()) {
				double topY = state.getCollisionShape(mc.world, check).getMax(Direction.Axis.Y);
				return check.getY() + topY;
			}
		}
		return startY;
	}

	private double[] solvePearlAngles(Vec3d startPos, Vec3d throwVel, double landX, double landGroundY, double landZ) {
		double dx = landX - startPos.x;
		double dz = landZ - startPos.z;
		double targetDist = Math.hypot(dx, dz);
		if (targetDist < 0.5) return null;

		double baseYaw = Math.toDegrees(Math.atan2(-dx, dz));

		double bestPitch = 0.0;
		double bestError = Double.MAX_VALUE;
		double bestTicks = 20.0;

		for (double p = -55.0; p <= 75.0; p += 0.5) {
			double[] sim = simulateTrajectory(p, baseYaw, startPos, throwVel, landGroundY, landX, landZ, targetDist);
			if (sim[4] < 0.5) continue;

			double err = Math.hypot(sim[0] - landX, sim[2] - landZ);
			if (err < bestError) {
				bestError = err;
				bestPitch = p;
				bestTicks = sim[3];
			}
		}

		if (bestError > 8.0) return null;

		double lo = bestPitch - 1.0;
		double hi = bestPitch + 1.0;
		for (double p = lo; p <= hi; p += 0.02) {
			double[] sim = simulateTrajectory(p, baseYaw, startPos, throwVel, landGroundY, landX, landZ, targetDist);
			if (sim[4] < 0.5) continue;

			double err = Math.hypot(sim[0] - landX, sim[2] - landZ);
			if (err < bestError) {
				bestError = err;
				bestPitch = p;
				bestTicks = sim[3];
			}
		}

		double[] sim = simulateTrajectory(bestPitch, baseYaw, startPos, throwVel, landGroundY, landX, landZ, targetDist);
		double actualYaw = Math.toDegrees(Math.atan2(-(sim[0] - startPos.x), sim[2] - startPos.z));
		double yawDrift = baseYaw - actualYaw;
		double correctedYaw = MathHelper.wrapDegrees((float) (baseYaw + yawDrift));

		lo = bestPitch - 0.5;
		hi = bestPitch + 0.5;
		for (double p = lo; p <= hi; p += 0.01) {
			double[] sim2 = simulateTrajectory(p, correctedYaw, startPos, throwVel, landGroundY, landX, landZ, targetDist);
			if (sim2[4] < 0.5) continue;

			double err = Math.hypot(sim2[0] - landX, sim2[2] - landZ);
			if (err < bestError) {
				bestError = err;
				bestPitch = p;
				bestTicks = sim2[3];
			}
		}

		double bestYaw = correctedYaw;
		for (double yOffset = -0.3; yOffset <= 0.3; yOffset += 0.02) {
			double candidateYaw = MathHelper.wrapDegrees((float) (correctedYaw + yOffset));
			double[] sim3 = simulateTrajectory(bestPitch, candidateYaw, startPos, throwVel, landGroundY, landX, landZ, targetDist);
			if (sim3[4] < 0.5) continue;

			double err = Math.hypot(sim3[0] - landX, sim3[2] - landZ);
			if (err < bestError) {
				bestError = err;
				bestYaw = candidateYaw;
				bestTicks = sim3[3];
			}
		}

		if (bestError > 2.0) return null;

		return new double[]{bestYaw, bestPitch, bestTicks};
	}

	private double[] simulateTrajectory(double pitchDeg, double yawDeg, Vec3d startPos, Vec3d throwVel, double targetGroundY, double targetX, double targetZ, double totalDist) {
		double pitchRad = Math.toRadians(pitchDeg);
		double yawRad = Math.toRadians(yawDeg);

		double speed = 1.5;
		double vx = -Math.sin(yawRad) * Math.cos(pitchRad) * speed + throwVel.x;
		double vy = -Math.sin(pitchRad) * speed + throwVel.y;
		double vz =  Math.cos(yawRad) * Math.cos(pitchRad) * speed + throwVel.z;

		double x = startPos.x;
		double y = startPos.y;
		double z = startPos.z;

		double prevX = x, prevY = y, prevZ = z;

		for (int tick = 1; tick <= 300; tick++) {
			prevX = x;
			prevY = y;
			prevZ = z;

			x += vx;
			y += vy;
			z += vz;

			vx *= 0.99;
			vy = vy * 0.99 - 0.03;
			vz *= 0.99;

			if (mc.world != null) {
				BlockHitResult hit = mc.world.raycast(new RaycastContext(
						new Vec3d(prevX, prevY, prevZ),
						new Vec3d(x, y, z),
						RaycastContext.ShapeType.COLLIDER,
						RaycastContext.FluidHandling.NONE,
						mc.player
				));

				if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
					double distFromStart = Math.hypot(hit.getPos().x - startPos.x, hit.getPos().z - startPos.z);
					if (distFromStart < totalDist - 1.5 && hit.getPos().y > targetGroundY + 1.2) {
						return new double[]{x, y, z, tick, 0.0};
					}
					double flightTicks = (tick - 1) + (hit.getPos().distanceTo(new Vec3d(prevX, prevY, prevZ)) / Math.hypot(x - prevX, z - prevZ));
					return new double[]{hit.getPos().x, hit.getPos().y, hit.getPos().z, flightTicks, 1.0};
				}
			}

			if (y <= targetGroundY && prevY > targetGroundY) {
				double frac = (targetGroundY - prevY) / (y - prevY);
				double impactX = prevX + frac * (x - prevX);
				double impactZ = prevZ + frac * (z - prevZ);
				double flightTicks = (tick - 1) + frac;
				return new double[]{impactX, targetGroundY, impactZ, flightTicks, 1.0};
			}
		}

		return new double[]{x, y, z, 300.0, 0.0};
	}

	private boolean hasPearlInHotbar() {
		return mc.player != null && InventoryUtils.hasItemInHotbar(i -> i == Items.ENDER_PEARL);
	}

	private void finish() {
		if (holdSilentTicks < 0) {
			SilentRot.stop(this);
		}
		reset();
	}

	private void reset() {
		stage = 0;
		isAggro = false;
		aggroTarget = null;
		startTick = -1;
		rotateStartAge = -1;
		stepCounter = 0;
		lastStepTick = -1;
		targetYaw = 0.0f;
		targetPitch = 0.0f;
		startRotYaw = 0.0f;
		startRotPitch = 0.0f;
		flickStartTime = 0L;
		gcdRemainder[0] = 0;
		gcdRemainder[1] = 0;
		if (holdSilentTicks < 0) {
			currentYaw = 0.0f;
			currentPitch = 0.0f;
		}
	}
}