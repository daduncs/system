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
import dlindustries.uk.daduncs.event.events.PacketSendListener;
import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.gui.Notice;
import dlindustries.uk.daduncs.imixin.IKeyBinding;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.managers.SlotManager;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.crystal.HitManager;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.module.setting.range;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.InventoryUtils;
import dlindustries.uk.daduncs.utils.MouseSimulation;
import dlindustries.uk.daduncs.utils.WorldUtils;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class MaceSwap extends Module implements AttackListener, TickListener, PacketSendListener {

	private final range delay = new range(EncryptedString.of("Delay"), 0, 20, 1, 1, 1)
			.setDescription(EncryptedString.of("Ticks to wait before restoring slot"));
	private final number useDensityAbove = new number(EncryptedString.of("Density Above"), 0.0, 8.0, 1.5, 0.1)
			.setDescription(EncryptedString.of("Blocks of fall distance to prefer Density over Breach"));
	private final bool stunSlam = new bool(EncryptedString.of("Stun Slam"), true)
			.setDescription(EncryptedString.of("Hits opponent with mace when their shield is disabled"));
	private final bool inputSimulation = new bool(EncryptedString.of("Input Simulation"), false)
			.setDescription(EncryptedString.of("Simulates hotbar key presses"));
	private final bool switchBack = new bool(EncryptedString.of("Switch Back"), true)
			.setDescription(EncryptedString.of("Swaps back to previous slot after attack"));
	private final bool allowSwords = new bool(EncryptedString.of("Allow Swords"), true)
			.setDescription(EncryptedString.of("Allow mace swap when holding swords"));
	private final bool allowAxes = new bool(EncryptedString.of("Allow Axes"), true)
			.setDescription(EncryptedString.of("Allow mace swap when holding axes"));
	private final bool allowOthers = new bool(EncryptedString.of("Allow Others"), false)
			.setDescription(EncryptedString.of("Allow mace swap when holding other items"));

	private static boolean shouldUseAfterAttack = false;
	private static PlayerEntity pendingShieldBreakTarget = null;
	private static boolean isSlamming = false;
	private static int slamTimeoutTicks = 0;
	private static boolean actionSentThisTick = false;
	private static MaceSwap instance;

	public MaceSwap() {
		super(EncryptedString.of("Mace Swap"),
				EncryptedString.of("Swaps to mace for attacks"),
				-1,
				Category.MACE);
		addSettings(delay, useDensityAbove, stunSlam, inputSimulation, switchBack, allowSwords, allowAxes, allowOthers);
		instance = this;
	}

	public static MaceSwap getInstance() {
		return instance;
	}

	public static boolean isSwapActive() {
		return SlotManager.isOwnerActive(MaceSwap.class);
	}

	public boolean isSlamActive() {
		return isSlamming || pendingShieldBreakTarget != null;
	}

	public boolean canStunSlam(PlayerEntity target) {
		return isEnabled() && stunSlam.getValue() && isShielding(target) && InventoryUtils.getAxeSlot() != -1 && InventoryUtils.hasMaceInHotbar();
	}

	@Override
	public void onEnable() {
		List<String> disabled = new ArrayList<>();
		for (Class<? extends Module> type : List.of(HitManager.class)) {
			Module module = System.INSTANCE.getModuleManager().getModule(type);
			if (module != null && module.isEnabled()) {
				module.setEnabled(false);
				disabled.add(module.getName().toString());
			}
		}

		if (!disabled.isEmpty()) {
			Notice.show(join(disabled) + (disabled.size() == 1 ? " was" : " were") + " turned off to not interfere with the Mace Swap");
		}

		SlotManager.cancel(this);
		shouldUseAfterAttack = false;
		pendingShieldBreakTarget = null;
		isSlamming = false;
		slamTimeoutTicks = 0;
		actionSentThisTick = false;

		eventManager.add(AttackListener.class, this);
		eventManager.add(TickListener.class, this);
		eventManager.add(PacketSendListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(AttackListener.class, this);
		eventManager.remove(TickListener.class, this);
		eventManager.remove(PacketSendListener.class, this);

		SlotManager.cancel(this);
		shouldUseAfterAttack = false;
		pendingShieldBreakTarget = null;
		isSlamming = false;
		slamTimeoutTicks = 0;
		super.onDisable();
	}

	@Override
	public void onPacketSend(PacketSendEvent event) {
		if (mc.player == null) return;
		Packet<?> packet = event.packet;

		if (packet instanceof ClientTickEndC2SPacket) {
			actionSentThisTick = false;
		} else if (AntibanbyDaduncs.isActionPacket(packet)) {
			actionSentThisTick = true;
		}
	}

	@Override
	public void onAttack(AttackEvent event) {
		if (mc.player == null || mc.currentScreen != null) return;
		if (!isAllowedWeapon()) return;
		if (SlotManager.hasActiveSwaps() && !SlotManager.isOwnerActive(this)) return;

		if (mc.crosshairTarget instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target) {
			if (!isEntityAllowed(target)) return;

			if (target instanceof PlayerEntity playerTarget && isShielding(playerTarget)) {
				if (stunSlam.getValue()) {
					int axeSlot = InventoryUtils.getAxeSlot();
					if (axeSlot != -1) {
						if (!WorldUtils.isAxe(mc.player.getMainHandStack())) {
							SlotManager.swapTo(this, axeSlot, inputSimulation.getValue(), 0, false, null);
						}
						pendingShieldBreakTarget = playerTarget;
						slamTimeoutTicks = 5;
					}
				}
				return;
			}

			processSwap(target);
		}
	}

	public void handleAttack(Entity entity) {
		if (mc.player == null || mc.currentScreen != null || !(entity instanceof LivingEntity target)) return;
		if (!isAllowedWeapon()) return;
		if (SlotManager.hasActiveSwaps() && !SlotManager.isOwnerActive(this)) return;
		if (!isEntityAllowed(target)) return;

		if (target instanceof PlayerEntity playerTarget && isShielding(playerTarget)) {
			if (stunSlam.getValue()) {
				int axeSlot = InventoryUtils.getAxeSlot();
				if (axeSlot != -1) {
					if (!WorldUtils.isAxe(mc.player.getMainHandStack())) {
						SlotManager.swapTo(this, axeSlot, inputSimulation.getValue(), 0, false, null);
					}
					pendingShieldBreakTarget = playerTarget;
					slamTimeoutTicks = 5;
				}
			}
			return;
		}

		processSwap(target);
	}

	private void processSwap(LivingEntity target) {
		if (mc.player == null || mc.player.isUsingItem()) {
			return;
		}
		if (actionSentThisTick || AntibanbyDaduncs.isActionSent() || AntibanbyDaduncs.isSlotPending()) {
			return;
		}

		boolean preferDensity = mc.player.fallDistance > useDensityAbove.getValue();
		RegistryKey<Enchantment> maceEnchantment = preferDensity ? Enchantments.DENSITY : Enchantments.BREACH;

		int maceSlot = InventoryUtils.findMace(maceEnchantment, preferDensity);
		if (maceSlot == -1) return;

		if (!SlotManager.isOwnerActive(this)) {
			int ticks = delay.getRandomValueInt();
			SlotManager.swapTo(this, maceSlot, inputSimulation.getValue(), ticks, switchBack.getValue(), this::restoreUseKeyIfNeeded);
		}
	}

	@Override
	public void onTick() {
		actionSentThisTick = false;

		if (mc.player == null || mc.currentScreen != null) {
			pendingShieldBreakTarget = null;
			slamTimeoutTicks = 0;
			return;
		}

		if (!stunSlam.getValue()) {
			pendingShieldBreakTarget = null;
			slamTimeoutTicks = 0;
			return;
		}

		if (pendingShieldBreakTarget == null) return;

		PlayerEntity target = pendingShieldBreakTarget;

		if (!target.isAlive() || target.isRemoved() || !isEntityAllowed(target)) {
			pendingShieldBreakTarget = null;
			slamTimeoutTicks = 0;
			SlotManager.cancel(this);
			return;
		}

		if (actionSentThisTick || AntibanbyDaduncs.isActionSent() || AntibanbyDaduncs.isSlotPending()) {
			return;
		}

		slamTimeoutTicks--;
		if (slamTimeoutTicks <= 0) {
			pendingShieldBreakTarget = null;
			SlotManager.cancel(this);
			return;
		}

		double eyeToBox = WorldUtils.getEyeToBoxDistance(mc.player, target);
		if (eyeToBox > 4.5) {
			pendingShieldBreakTarget = null;
			SlotManager.cancel(this);
			return;
		}

		double safeReach = 2.90;
		if (!mc.player.isOnGround() && mc.player.getVelocity().y < 0) {
			safeReach -= Math.min(0.30, Math.abs(mc.player.getVelocity().y));
		}
		Vec3d vel = mc.player.getVelocity();
		double horizSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
		if (horizSpeed > 0.05) {
			safeReach -= Math.min(0.20, horizSpeed * 0.6);
		}

		boolean inSafeReach = eyeToBox <= safeReach;
		boolean rayHits = WorldUtils.doesRaycastHitEntity(mc.player, target, safeReach);
		if (!rayHits && inSafeReach) {
			Vec3d eyePos = mc.player.getEyePos();
			Vec3d lookVec = WorldUtils.getPlayerLookVec(mc.player);
			Box targetBox = target.getBoundingBox().expand(0.20);
			rayHits = targetBox.raycast(eyePos, eyePos.add(lookVec.multiply(safeReach))).isPresent();
		}

		if (target.hurtTime > 1 && !isAboutToLand()) {
			return;
		}

		if (!inSafeReach || !rayHits) {
			return;
		}

		boolean preferDensity = mc.player.fallDistance > useDensityAbove.getValue();
		RegistryKey<Enchantment> maceEnchantment = preferDensity ? Enchantments.DENSITY : Enchantments.BREACH;
		int maceSlot = InventoryUtils.findMace(maceEnchantment, preferDensity);
		if (maceSlot == -1) {
			pendingShieldBreakTarget = null;
			SlotManager.cancel(this);
			return;
		}

		if (mc.player.isUsingItem()) {
			return;
		}

		isSlamming = true;
		try {
			int ticks = Math.max(1, delay.getRandomValueInt());
			if (SlotManager.swapToIfNeeded(this, maceSlot, inputSimulation.getValue(), ticks, switchBack.getValue(), this::restoreUseKeyIfNeeded)) {
				if (mc.interactionManager != null
						&& mc.player.getInventory().getSelectedSlot() == maceSlot
						&& !AntibanbyDaduncs.isSlotPending()
						&& AntibanbyDaduncs.canAttackNow()) {
					mc.interactionManager.attackEntity(mc.player, target);
					mc.player.swingHand(Hand.MAIN_HAND);
					actionSentThisTick = true;
					pendingShieldBreakTarget = null;
				}
			}
		} finally {
			isSlamming = false;
		}
	}

	private boolean isAboutToLand() {
		if (mc.player == null) return false;
		if (mc.player.isOnGround()) return true;
		if (mc.player.getVelocity().y >= 0) return false;
		Box box = mc.player.getBoundingBox().offset(0, mc.player.getVelocity().y * 2.0, 0);
		return mc.world != null && !mc.world.isSpaceEmpty(mc.player, box);
	}

	private boolean isAllowedWeapon() {
		if (mc.player == null) return false;
		ItemStack mainHand = mc.player.getMainHandStack();
		if (mainHand.isEmpty()) return allowOthers.getValue();
		if (WorldUtils.isSword(mainHand)) return allowSwords.getValue();
		if (WorldUtils.isAxe(mainHand)) return allowAxes.getValue();
		return allowOthers.getValue();
	}

	private boolean isEntityAllowed(LivingEntity target) {
		if (target == null || target == mc.player || !target.isAlive()) return false;
		if (target instanceof PlayerEntity player && System.INSTANCE.getFriendManager().isFriend(player)) {
			return false;
		}
		return true;
	}

	public static boolean isShielding(PlayerEntity player) {
		if (player == null) return false;
		if (WorldUtils.isShieldFacingAway(player)) return false;
		if (player.isBlocking()) return true;
		ItemStack activeItem = player.getActiveItem();
		return player.isUsingItem() && !activeItem.isEmpty() && (activeItem.getItem() instanceof ShieldItem || activeItem.isOf(Items.SHIELD));
	}

	private void restoreUseKeyIfNeeded() {
		if (!shouldUseAfterAttack) return;
		shouldUseAfterAttack = false;
		if (mc.options.useKey != null) {
			if (((IKeyBinding) mc.options.useKey).isActuallyPressed()) {
				mc.options.useKey.setPressed(true);
			}
		}
	}

	private static String join(List<String> names) {
		if (names.size() == 1)
			return names.get(0);

		return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
	}
}
