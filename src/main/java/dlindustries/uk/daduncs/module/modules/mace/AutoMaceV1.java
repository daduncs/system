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

import dlindustries.uk.daduncs.System;
import dlindustries.uk.daduncs.event.events.AttackListener;
import dlindustries.uk.daduncs.event.events.HudListener;
import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.gui.Notice;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.mixin.ClientPlayerInteractionManagerAccessor;
import dlindustries.uk.daduncs.mixin.KeyBindingAccessor;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.crystal.HitManager;
import dlindustries.uk.daduncs.module.modules.sword.AimAssist;
import dlindustries.uk.daduncs.module.modules.sword.TriggerBot;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.module.setting.range;
import dlindustries.uk.daduncs.utils.*;
import dlindustries.uk.daduncs.utils.rotation.Rotation;
import dlindustries.uk.daduncs.utils.rotation.SilentAim;
import dlindustries.uk.daduncs.utils.rotation.SilentRot;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttackRangeComponent;
import net.minecraft.component.type.KineticWeaponComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AutoMaceV1 extends Module implements TickListener, AttackListener, HudListener {

	public AutoMaceV1() {
		super(EncryptedString.of("AutoMaceV1"), EncryptedString.of("A simple auto mace yogis made"), -1, Category.MACE);
		addSettings(delay, height, switchBack, breachSwap, spearReach, doublePop, aim, aimSpeed, silent, silentSpeed, spam);
	}

	private final number delay = new number(EncryptedString.of("Delay"), 0, 1000, 200, 1);
	private final number height = new number(EncryptedString.of("Height"), 0.5, 5, 1.5, 0.1);
	private final bool switchBack = new bool(EncryptedString.of("Switch Back"), true);
	private final bool breachSwap = new bool(EncryptedString.of("Breach Swap"), true);
	private final bool spearReach = new bool(EncryptedString.of("Spear Reach"), true);
	private final bool doublePop = new bool(EncryptedString.of("Double Pop"), true);
	private final bool aim = new bool(EncryptedString.of("Aim"), true);
	private final number aimSpeed = new number(EncryptedString.of("Aim Speed"), 50, 2000, 1000, 10);
	private final bool silent = new bool(EncryptedString.of("Silent Rotations"), false);
	private final range silentSpeed = new range(EncryptedString.of("Silent Speed"), 0, 600, 1, 100, 150);
	private final bool spam = new bool(EncryptedString.of("Spam"), true);

	private static final int SLAM = 1;
	private static final int DONE = 2;
	private static final int POP = 3;
	private static final int CHARGE = 4;
	private static final int SLAM_WAIT = 3;
	private static final int BREAK_WAIT = 40;
	private static final int RETRY = 10;
	private static final int POP_WAIT = 10;
	private static final int CHARGE_WAIT = 200;
	private static final double CHARGE_RANGE = 48;
	private static final double CHARGE_WIDTH = 6;
	private static final float TOTEM_HEALTH = 9f;
	private static final long SHIELD_DOWN = 5000;
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private final TimerUtils timer = new TimerUtils();
	private final double[] gcd = new double[2];
	private PlayerEntity target;
	private boolean wasOnGround;
	private boolean jumped;
	private boolean swung;
	private boolean holding;
	private boolean stabbed;
	private int step;
	private int broke;
	private int slamWait;
	private int prevSlot = -1;
	private final Map<Integer, Long> shieldDown = new HashMap<>();

	@Override
	public void onEnable() {
		List<String> disabled = new ArrayList<>();
		for (Class<? extends Module> type : List.of(TriggerBot.class, AimAssist.class, HitManager.class, MaceSwap.class)) {
			Module module = System.INSTANCE.getModuleManager().getModule(type);
			if (module != null && module.isEnabled()) {
				module.setEnabled(false);
				disabled.add(module.getName().toString());
			}
		}

		if (!disabled.isEmpty())
			Notice.show(join(disabled) + (disabled.size() == 1 ? " was" : " were") + " turned off to not interfere with the AutoMace");

		eventManager.add(TickListener.class, this);
		eventManager.add(AttackListener.class, this);
		eventManager.add(HudListener.class, this);

		target = null;
		shieldDown.clear();
		jumped = false;
		swung = false;
		holding = false;
		stabbed = false;
		step = 0;
		broke = 0;
		slamWait = 0;
		prevSlot = -1;
		timer.reset();
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		eventManager.remove(AttackListener.class, this);
		eventManager.remove(HudListener.class, this);

		if (step == CHARGE) {
			release();
		}

		if (prevSlot != -1) {
			swapBack();
		}

		target = null;
		jumped = false;
		swung = false;
		stabbed = false;
		step = 0;
		broke = 0;
		slamWait = 0;
		prevSlot = -1;
		SilentAim.stop(this);
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.currentScreen != null || mc.player == null) return;

		trackJump();

		if (grappling()) {
			if (step == CHARGE) release();
			return;
		}

		if (broke > 0) broke--;

		if (step == SLAM) {
			slam();
			return;
		}

		if (step == CHARGE) {
			charging();
			return;
		}

		if (step == POP) {
			smash();
			return;
		}

		if (step == DONE || prevSlot != -1) {
			swapBack();
			return;
		}

		if (doublePop.getValue() && !stabbed && charge()) return;

		target = findTarget();

		if (target == null) return;
		if (System.INSTANCE.getFriendManager().isFriend(target)) return;

		int mace = maceSlot();
		int axe = InventoryUtils.getAxeSlot();

		if (mace == -1) return;

		if (!spam.getValue()) {
			if (!timer.hasReached(delay.getValue())) return;
			if (swung) return;
		}

		int currentSlot = mc.player.getInventory().getSelectedSlot();

		if (!canHit()) return;

		if (broke < BREAK_WAIT - RETRY && shielded(target) && axe != -1) {
			prevSlot = currentSlot;
			if (selectSlot(axe) && attackTarget(target)) {
				shieldDown.put(target.getId(), java.lang.System.currentTimeMillis() + SHIELD_DOWN);
				timer.reset();
				broke = BREAK_WAIT;
				slamWait = 0;
				step = SLAM;
			}
			return;
		}

		if (target.hurtTime > 1 && !aboutToLand() && !passing()) return;

		prevSlot = currentSlot;
		if (selectSlot(mace)) {
			attackTarget(target);
			timer.reset();
			swung = true;
			step = DONE;
		}
	}

	@Override
	public void onAttack(AttackEvent event) {
		if (mc.player == null) return;
		if (step != 0 || prevSlot != -1 || broke > 0) return;
		if (spearReach.getValue() && reachWithSpear()) return;

		if (!breachSwap.getValue()) return;
		if (!WorldUtils.isSword(mc.player.getMainHandStack())) return;
		if (maceSlot() == -1) return;

		if (!(mc.crosshairTarget instanceof EntityHitResult hit)) return;
		if (!(hit.getEntity() instanceof PlayerEntity player)) return;
		if (System.INSTANCE.getFriendManager().isFriend(player)) return;

		target = player;
		int axe = InventoryUtils.getAxeSlot();

		if (shielded(player) && axe != -1) {
			prevSlot = mc.player.getInventory().getSelectedSlot();
			if (selectSlot(axe)) {
				shieldDown.put(player.getId(), java.lang.System.currentTimeMillis() + SHIELD_DOWN);
				broke = BREAK_WAIT;
				slamWait = 0;
				step = SLAM;
			}
			return;
		}

		if (!jumped) return;
		int breach = breachSlot();
		if (breach == -1) return;

		prevSlot = mc.player.getInventory().getSelectedSlot();
		if (selectSlot(breach)) {
			step = DONE;
		}
	}

	@Override
	public void onRenderHud(HudEvent event) {
		if (mc.player == null) return;

		if (grappling()) {
			SilentAim.stop(this);
			return;
		}

		if (silent.getValue()) {
			silentAim();
			return;
		}

		SilentAim.stop(this);
		if (!aim.getValue()) return;
		if (target == null || target.isRemoved() || !target.isAlive() || mc.currentScreen != null) return;
		if (!airborne() || maceSlot() == -1) return;
		if (step != CHARGE && !inRange()) return;

		if (held(holding ? 0f : -0.15f)) {
			holding = true;
			return;
		}

		holding = false;

		Vec3d baseTarget = target.getBoundingBox().getCenter();
		Vec3d wanderingTarget = RotationUtils.getWanderingTargetPoint(target, baseTarget);
		Rotation look = RotationUtils.getDirection(mc.player.getEyePos(), wanderingTarget);
		float seconds = mc.getRenderTickCounter().getFixedDeltaTicks() / 20f;

		float yaw = towards(mc.player.getYaw(), (float) look.yaw(), seconds, 1.0f, 30.0f);
		float pitch = towards(mc.player.getPitch(), (float) look.pitch(), seconds, 0.4f, 12.0f);

		Rotation smooth = RotationUtils.applyGCD(mc.player.getYaw(), mc.player.getPitch(), yaw, pitch, gcd, true);

		mc.player.setYaw((float) smooth.yaw());
		mc.player.setPitch(MathHelper.clamp((float) smooth.pitch(), -90.0F, 90.0F));
	}

	private void silentAim() {
		boolean valid = aim.getValue() && target != null && !target.isRemoved() && target.isAlive() && mc.currentScreen == null
				&& airborne() && maceSlot() != -1 && (step == CHARGE || inRange());

		if (valid && SilentAim.rotateTo(this, target, silentSpeed.getRandomValueFloat(), silentSpeed.getRandomValueFloat(), 50f))
			return;

		SilentAim.rotateBack(this, silentSpeed.getRandomValueFloat(), silentSpeed.getRandomValueFloat());
	}

	private Vec3d look() {
		if (silent.getValue() && SilentRot.on())
			return WorldUtils.getPlayerLookVec(SilentRot.yaw(), SilentRot.pitch());

		return WorldUtils.getPlayerLookVec(mc.player);
	}

	private static String join(List<String> names) {
		if (names.size() == 1)
			return names.get(0);

		return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
	}

	private void trackJump() {
		if (mc.player == null) return;
		if (mc.player.isOnGround()) {
			jumped = false;
			swung = false;
			stabbed = false;
		} else if (wasOnGround && mc.player.getVelocity().y > 0) {
			jumped = true;
		} else if (mc.player.getVelocity().y > 0.3 && step != CHARGE) {
			stabbed = false;
		}

		wasOnGround = mc.player.isOnGround();
	}

	private void slam() {
		if (target == null || target.isRemoved() || !target.isAlive() || ++slamWait > SLAM_WAIT) {
			slamWait = 0;
			step = DONE;
			return;
		}

		int mace = maceSlot();
		if (mace == -1) {
			slamWait = 0;
			step = DONE;
			return;
		}

		if (!canReach()) return;

		if (selectSlot(mace) && attackTarget(target)) {
			timer.reset();
			swung = true;
			slamWait = 0;
			step = DONE;
		}
	}

	private boolean charge() {
		if (!airborne() || maceSlot() == -1) return false;

		PlayerEntity below = chargeTarget();
		if (below == null || shielded(below)) return false;

		int spear = chargeSlot(below);
		if (spear == -1) return false;

		AttackRangeComponent range = mc.player.getInventory().getStack(spear).get(DataComponentTypes.ATTACK_RANGE);
		if (range == null || !open(below, contact(below, range.getEffectiveMaxRange(mc.player)))) return false;

		target = below;
		prevSlot = mc.player.getInventory().getSelectedSlot();
		if (!selectSlot(spear)) return true;

		mc.options.useKey.setPressed(true);
		stabbed = true;
		slamWait = 0;
		step = CHARGE;
		return true;
	}

	private PlayerEntity chargeTarget() {
		if (mc.world == null) return null;

		PlayerEntity best = null;
		double bestDistance = CHARGE_RANGE;

		for (PlayerEntity player : mc.world.getPlayers()) {
			if (player == mc.player || !player.isAlive()) continue;
			if (System.INSTANCE.getFriendManager().isFriend(player)) continue;
			if (mc.player.getY() < player.getY() + player.getHeight()) continue;
			if (Math.hypot(player.getX() - mc.player.getX(), player.getZ() - mc.player.getZ()) > CHARGE_WIDTH) continue;

			double distance = mc.player.distanceTo(player);
			if (distance > bestDistance || !visible(player)) continue;

			best = player;
			bestDistance = distance;
		}

		return best;
	}

	private boolean visible(PlayerEntity player) {
		Vec3d eye = mc.player.getEyePos();
		Vec3d center = player.getBoundingBox().getCenter();

		return mc.world.raycast(new RaycastContext(eye, center, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player)).getType() == HitResult.Type.MISS;
	}

	private void charging() {
		if (target == null || target.isRemoved() || !target.isAlive() || mc.player.isOnGround() || shielded(target)) {
			release();
			slamWait = 0;
			step = DONE;
			return;
		}

		AttackRangeComponent range = mc.player.getMainHandStack().get(DataComponentTypes.ATTACK_RANGE);

		if (range == null || ++slamWait > CHARGE_WAIT) {
			slamWait = 0;
			step = POP;
			smash();
			return;
		}

		mc.options.useKey.setPressed(true);

		if (WorldUtils.getEyeToBoxDistance(mc.player, target) <= range.getEffectiveMaxRange(mc.player)) {
			slamWait = 0;
			step = POP;
		}
	}

	private int contact(PlayerEntity player, double reach) {
		Vec3d eye = mc.player.getEyePos();
		Vec3d velocity = mc.player.getVelocity();
		int ticks = 0;

		while (ticks < CHARGE_WAIT && distance(eye, player) > reach) {
			velocity = drag(velocity);
			eye = eye.add(velocity);
			ticks++;
		}

		return ticks;
	}

	private Vec3d drag(Vec3d velocity) {
		return new Vec3d(velocity.x * 0.91, (velocity.y - 0.08) * 0.98, velocity.z * 0.91);
	}

	private boolean open(PlayerEntity player, int ticks) {
		long now = java.lang.System.currentTimeMillis();
		long down = shieldDown.getOrDefault(player.getId(), 0L);

		// not charge if shield back
		return down < now || down > now + ticks * 50L + 150L;
	}

	private boolean grappling() {
		Grappler grappler = System.INSTANCE.getModuleManager().getModule(Grappler.class);
		return grappler != null && grappler.isEnabled() && grappler.isThrowing();
	}

	private void release() {
		mc.options.useKey.setPressed(KeyUtils.isKeyPressed(((KeyBindingAccessor) mc.options.useKey).getBoundKey().getCode()));
	}

	private void smash() {
		if (target == null || target.isRemoved() || !target.isAlive() || mc.player.isOnGround() || ++slamWait > POP_WAIT) {
			release();
			slamWait = 0;
			step = DONE;
			return;
		}

		int mace = maceSlot();
		if (mace == -1) {
			release();
			slamWait = 0;
			step = DONE;
			return;
		}

		if (!canHit()) {
			mc.options.useKey.setPressed(true);
			return;
		}

		release();
		mc.player.clearActiveItem();

		if (selectSlot(mace) && attackTarget(target)) {
			timer.reset();
			swung = true;
			slamWait = 0;
			step = DONE;
		}
	}

	private boolean doublePops(PlayerEntity player, ItemStack spear) {
		KineticWeaponComponent kinetic = spear.get(DataComponentTypes.KINETIC_WEAPON);
		AttackRangeComponent range = spear.get(DataComponentTypes.ATTACK_RANGE);
		if (kinetic == null || range == null) return false;

		Vec3d eye = mc.player.getEyePos();
		Vec3d velocity = mc.player.getVelocity();
		double contact = range.getEffectiveMaxRange(mc.player);
		double peak = eye.y;
		int ticks = 0;

		while (distance(eye, player) > contact) {
			if (++ticks > CHARGE_WAIT) return false;
			velocity = drag(velocity);
			eye = eye.add(velocity);
			// count fall from top
			peak = Math.max(peak, eye.y);
		}

		int charged = ticks - kinetic.delayTicks();
		int maxCharge = kinetic.damageConditions().map(KineticWeaponComponent.Condition::maxDurationTicks).orElse(CHARGE_WAIT);
		if (charged < 0 || charged > maxCharge) return false;

		Vec3d toward = player.getBoundingBox().getCenter().subtract(eye).normalize();
		float spearHit = 1f + MathHelper.floor(velocity.multiply(20).dotProduct(toward) * kinetic.damageMultiplier());

		float fall = (float) (mc.player.fallDistance + peak - eye.y) + 1.5f;
		float maceHit = 1.2f + smashBonus(fall) + 0.5f * density() * fall;

		float health = player.getHealth() + player.getAbsorptionAmount();

		return afterArmor(player, spearHit) >= health && afterArmor(player, maceHit - spearHit) >= TOTEM_HEALTH;
	}

	private static double distance(Vec3d eye, PlayerEntity player) {
		Box box = player.getBoundingBox();

		double x = MathHelper.clamp(eye.x, box.minX, box.maxX);
		double y = MathHelper.clamp(eye.y, box.minY, box.maxY);
		double z = MathHelper.clamp(eye.z, box.minZ, box.maxZ);

		return eye.distanceTo(new Vec3d(x, y, z));
	}

	private int density() {
		int mace = maceSlot();
		if (mace == -1) return 0;

		for (var entry : mc.player.getInventory().getStack(mace).getEnchantments().getEnchantmentEntries())
			if (entry.getKey().matchesKey(Enchantments.DENSITY)) return entry.getIntValue();

		return 0;
	}

	private static float smashBonus(float fall) {
		return 4f * Math.min(fall, 3f) + 2f * MathHelper.clamp(fall - 3f, 0f, 5f) + Math.max(fall - 8f, 0f);
	}

	private static float afterArmor(PlayerEntity player, float damage) {
		if (damage <= 0f) return 0f;

		float armor = player.getArmor();
		float toughness = (float) player.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
		float blocked = MathHelper.clamp(armor - damage / (2f + toughness / 4f), armor * 0.2f, 20f);

		damage *= 1f - blocked / 25f;
		return damage * (1f - Math.min(protection(player), 20) / 25f);
	}

	private static int protection(PlayerEntity player) {
		int total = 0;

		for (EquipmentSlot slot : ARMOR)
			for (var entry : player.getEquippedStack(slot).getEnchantments().getEnchantmentEntries())
				if (entry.getKey().matchesKey(Enchantments.PROTECTION)) total += entry.getIntValue();

		return total;
	}

	private boolean reachWithSpear() {
		if (mc.crosshairTarget instanceof EntityHitResult) return false;
		if (mc.player.getMainHandStack().contains(DataComponentTypes.PIERCING_WEAPON)) return false;

		int spear = spearSlot();
		if (spear == -1) return false;

		AttackRangeComponent range = mc.player.getInventory().getStack(spear).get(DataComponentTypes.ATTACK_RANGE);
		if (range == null || spearTarget(range) == null) return false;

		prevSlot = mc.player.getInventory().getSelectedSlot();
		if (selectSlot(spear)) step = DONE;
		return true;
	}

	private PlayerEntity spearTarget(AttackRangeComponent range) {
		if (mc.world == null) return null;

		for (PlayerEntity player : mc.world.getPlayers()) {
			if (player == mc.player || !player.isAlive()) continue;
			if (System.INSTANCE.getFriendManager().isFriend(player)) continue;
			if (inSpearRange(player, range)) return player;
		}

		return null;
	}

	private boolean inSpearRange(PlayerEntity player, AttackRangeComponent range) {
		double distance = WorldUtils.getEyeToBoxDistance(mc.player, player);
		double far = range.getEffectiveMaxRange(mc.player) - 0.2;

		if (distance < range.getEffectiveMinRange(mc.player) + 0.1 || distance > far) return false;

		return sees(player, far);
	}

	private void swapBack() {
		if (switchBack.getValue() && prevSlot != -1) {
			if (AntibanbyDaduncs.isActionSent() || AntibanbyDaduncs.isSlotPending()) {
				return;
			}
			if (selectSlot(prevSlot)) {
				prevSlot = -1;
				step = 0;
			}
			return;
		}

		prevSlot = -1;
		step = 0;
	}

	private PlayerEntity findTarget() {
		if (mc.world == null || mc.player == null) return null;

		PlayerEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		double scan = 2.5 + lead();

		for (PlayerEntity player : mc.world.getPlayers()) {
			if (player == mc.player || !player.isAlive()) continue;
			if (System.INSTANCE.getFriendManager().isFriend(player)) continue;

			double dx = player.getX() - mc.player.getX();
			double dz = player.getZ() - mc.player.getZ();
			double horizontalDistance = Math.hypot(dx, dz);

			if (horizontalDistance > scan) continue;
			if (mc.player.getEyeY() < player.getY()) continue;
			if (mc.player.getY() < player.getY() + player.getHeight() - 0.5 && !skipped(player)) continue;

			if (horizontalDistance < bestDistance) {
				bestDistance = horizontalDistance;
				best = player;
			}
		}

		if (best != null) return best;

		if (mc.crosshairTarget instanceof EntityHitResult hit
				&& hit.getEntity() instanceof PlayerEntity player
				&& player.isAlive()
				&& !System.INSTANCE.getFriendManager().isFriend(player)) {
			return player;
		}

		return null;
	}

	private boolean selectSlot(int slot) {
		if (mc.player == null || mc.getNetworkHandler() == null || slot < 0 || slot > 8) return false;

		if (mc.player.getInventory().getSelectedSlot() == slot) {
			return true;
		}

		if (AntibanbyDaduncs.isSlotPending() && AntibanbyDaduncs.getPendingSlot() == slot) {
			return false;
		}

		if (AntibanbyDaduncs.isActionSent()) {
			return false;
		}

		if (mc.player.isUsingItem() && mc.interactionManager != null) {
			mc.interactionManager.stopUsingItem(mc.player);
		}

		mc.player.getInventory().setSelectedSlot(slot);
		if (mc.interactionManager != null) {
			((ClientPlayerInteractionManagerAccessor) mc.interactionManager).syncSlot();
		} else {
			mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
		}
		return true;
	}

	private boolean attackTarget(PlayerEntity target) {
		if (mc.player == null || mc.interactionManager == null || target == null) return false;
		if (AntibanbyDaduncs.isActionSent() || AntibanbyDaduncs.isAttackSent() || AntibanbyDaduncs.isSlotPending() || AntibanbyDaduncs.isMovementSent()) return false;

		mc.interactionManager.attackEntity(mc.player, target);
		mc.player.swingHand(Hand.MAIN_HAND);
		return true;
	}

	private double getSafeReach() {
		double safeReach = 2.90;
		if (!mc.player.isOnGround() && mc.player.getVelocity().y < 0) {
			safeReach -= Math.min(0.30, Math.abs(mc.player.getVelocity().y));
		}
		Vec3d vel = mc.player.getVelocity();
		double horizSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
		if (horizSpeed > 0.05 && !closingIn(vel)) {
			safeReach -= Math.min(0.20, horizSpeed * 0.6);
		}
		return safeReach;
	}

	private boolean closingIn(Vec3d vel) {
		if (target == null) return false;

		return vel.x * (target.getX() - mc.player.getX()) + vel.z * (target.getZ() - mc.player.getZ()) > 0;
	}

	private double lead() {
		Vec3d vel = mc.player.getVelocity();

		return Math.hypot(vel.x, vel.z) * 3;
	}

	private boolean inRange() {
		double range = 6 + lead();

		return mc.player.squaredDistanceTo(target) <= range * range;
	}

	private boolean canReach() {
		if (mc.player == null || target == null || target.isRemoved() || !target.isAlive()) return false;

		double safeReach = getSafeReach();

		if (WorldUtils.getEyeToBoxDistance(mc.player, target) > safeReach) return false;

		return sees(target, safeReach);
	}

	private boolean sees(PlayerEntity player, double reach) {
		Vec3d eye = mc.player.getEyePos();
		Vec3d end = eye.add(look().multiply(reach));
		Vec3d point = player.getBoundingBox().raycast(eye, end).orElse(null);
		if (point == null) return false;

		return mc.world.raycast(new RaycastContext(eye, point, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player)).getType() == HitResult.Type.MISS;
	}

	private boolean canHit() {
		if (!falling() || !canReach()) return false;

		double head = mc.player.getY() - (target.getY() + target.getHeight());

		double next = head + (mc.player.getVelocity().y - 0.08) * 0.98;

		if (head > -0.5) return head < height.getValue() || next < -0.5;

		return !target.isOnGround() || skipped(target);
	}

	private boolean skipped(PlayerEntity player) {
		return mc.player.lastY > player.getY() + player.getHeight() - 0.5;
	}

	private boolean held(float grow) {
		Vec3d eye = mc.player.getEyePos();
		Vec3d look = look();

		return target.getBoundingBox().expand(grow).raycast(eye, eye.add(look.multiply(getSafeReach()))).isPresent();
	}

	private boolean shielded(PlayerEntity player) {
		if (player == null || mc.player == null) return false;
		if (!player.isUsingItem() || !player.getActiveItem().contains(DataComponentTypes.BLOCKS_ATTACKS)) return false;

		Vec3d toUs = mc.player.getEntityPos().subtract(player.getEntityPos());
		Vec3d flat = new Vec3d(toUs.x, 0, toUs.z);

		if (flat.length() < 1.5) return true;

		return flat.normalize().dotProduct(player.getRotationVector(0f, player.getHeadYaw())) > -0.5;
	}

	private boolean aboutToLand() {
		if (mc.player == null || mc.world == null) return false;
		if (mc.player.isOnGround()) return true;
		if (mc.player.getVelocity().y >= 0) return false;

		return !mc.world.isSpaceEmpty(mc.player, mc.player.getBoundingBox().offset(0, mc.player.getVelocity().y * 2.0, 0));
	}

	private boolean passing() {
		Vec3d vel = mc.player.getVelocity();
		Vec3d next = mc.player.getEyePos().add(vel);
		Box box = target.getBoundingBox();

		double x = MathHelper.clamp(next.x, box.minX, box.maxX);
		double y = MathHelper.clamp(next.y, box.minY, box.maxY);
		double z = MathHelper.clamp(next.z, box.minZ, box.maxZ);

		if (next.distanceTo(new Vec3d(x, y, z)) > getSafeReach()) return true;

		return target.isOnGround() && mc.player.getY() + vel.y < box.maxY - 0.5;
	}

	private boolean falling() {
		return airborne() && mc.player.fallDistance > 1.5;
	}

	private boolean airborne() {
		if (mc.player == null) return false;
		if (mc.player.isOnGround()) return false;

		return !mc.player.isGliding() && !mc.player.isTouchingWater();
	}

	private float towards(float from, float to, float seconds, float rate, float cap) {
		float diff = MathHelper.wrapDegrees(to - from);
		float factor = 1f - (float) Math.exp(-aimSpeed.getValueFloat() * rate * seconds * 0.03f * (1f + (float) lead()));
		float step = diff * factor;
		float maxStep = cap * Math.max(1.0f, seconds * 20.0f);
		step = MathHelper.clamp(step, -maxStep, maxStep);
		return from + step;
	}

	private int maceSlot() {
		int plain = plainMaceSlot();

		return plain != -1 ? plain : InventoryUtils.getMaceSlot();
	}

	private int plainMaceSlot() {
		if (mc.player == null) return -1;

		for (int i = 0; i < 9; i++) {
			ItemStack stack = mc.player.getInventory().getStack(i);

			if (stack.isOf(Items.MACE) && !enchanted(stack, Enchantments.BREACH)) return i;
		}

		return -1;
	}

	private int breachSlot() {
		if (mc.player == null) return -1;

		for (int i = 0; i < 9; i++) {
			ItemStack stack = mc.player.getInventory().getStack(i);

			if (stack.isOf(Items.MACE) && enchanted(stack, Enchantments.BREACH)) return i;
		}

		return -1;
	}

	private int spearSlot() {
		if (mc.player == null) return -1;

		for (int i = 0; i < 9; i++) {
			ItemStack stack = mc.player.getInventory().getStack(i);

			if (stack.contains(DataComponentTypes.PIERCING_WEAPON) && !enchanted(stack, Enchantments.LUNGE)) return i;
		}

		return -1;
	}

	private int chargeSlot(PlayerEntity player) {
		int best = -1;
		float weakest = Float.MAX_VALUE;

		for (int i = 0; i < 9; i++) {
			ItemStack stack = mc.player.getInventory().getStack(i);
			KineticWeaponComponent kinetic = stack.get(DataComponentTypes.KINETIC_WEAPON);

			if (kinetic == null || kinetic.damageMultiplier() >= weakest) continue;
			if (!doublePops(player, stack)) continue;

			best = i;
			weakest = kinetic.damageMultiplier();
		}

		return best;
	}

	private static boolean enchanted(ItemStack stack, RegistryKey<Enchantment> key) {
		for (RegistryEntry<Enchantment> enchant : stack.getEnchantments().getEnchantments())
			if (enchant.matchesKey(key)) return true;

		return false;
	}
}
