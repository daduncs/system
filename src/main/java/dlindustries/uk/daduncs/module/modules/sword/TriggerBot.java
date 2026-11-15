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
import dlindustries.uk.daduncs.event.events.AttackListener;
import dlindustries.uk.daduncs.event.events.ItemUseListener;
import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.gui.Notice;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.modules.client.Friends;
import dlindustries.uk.daduncs.module.modules.mace.MaceSwap;
import dlindustries.uk.daduncs.module.modules.crystal.HitManager;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.range;
import dlindustries.uk.daduncs.module.setting.mode;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

public final class TriggerBot extends Module implements TickListener, AttackListener, ItemUseListener {

	public enum ClickType {
		LEFT_CLICK("Left Click"),
		RIGHT_CLICK("Right Click"),
		ANY_CLICK("Any Click"),
		AUTOMATIC("Automatic");

		private final String name;

		ClickType(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	private final bool enableSwords = new bool(EncryptedString.of("Enable Swords"), true)
			.setDescription(EncryptedString.of("Allows triggerbot to work with Swords"));
	private final bool enableAxes = new bool(EncryptedString.of("Enable Axes"), true)
			.setDescription(EncryptedString.of("Allows triggerbot to work with Axes"));
	private final bool enableMaces = new bool(EncryptedString.of("Enable Maces"), true)
			.setDescription(EncryptedString.of("Allows triggerbot to work with Maces"));
	private final bool enableOthers = new bool(EncryptedString.of("Enable Other Items"), false)
			.setDescription(EncryptedString.of("Allows triggerbot to work with all other items"));

	private final mode<ClickType> swordClick = new mode<>(EncryptedString.of("Sword Trigger"), ClickType.LEFT_CLICK, ClickType.class);
	private final mode<ClickType> axeClick = new mode<>(EncryptedString.of("Axe Trigger"), ClickType.LEFT_CLICK, ClickType.class);
	private final mode<ClickType> maceClick = new mode<>(EncryptedString.of("Mace Trigger"), ClickType.RIGHT_CLICK, ClickType.class);
	private final mode<ClickType> otherClick = new mode<>(EncryptedString.of("Other Trigger"), ClickType.LEFT_CLICK, ClickType.class);

	private final range swordDelay = new range(EncryptedString.of("Sword Delay"), 50, 1000, 1, 540, 550);
	private final range axeDelay = new range(EncryptedString.of("Axe Delay"), 50, 1000, 1, 780, 800);
	private final range maceDelay = new range(EncryptedString.of("Mace Delay"), 50, 1000, 1, 50, 50);

	private final bool inScreen = new bool(EncryptedString.of("Work In Screen"), false);
	private final bool whileUse = new bool(EncryptedString.of("While Use"), false);
	private final bool checkShield = new bool(EncryptedString.of("Check Shield"), false);
	private final bool onlyCritSword = new bool(EncryptedString.of("Only Crit Sword"), false);
	private final bool onlyCritAxe = new bool(EncryptedString.of("Only Crit Axe"), false);
	private final bool swing = new bool(EncryptedString.of("Swing Hand"), true);
	private final bool whileAscend = new bool(EncryptedString.of("While Ascending"), false);
	private final bool clickSimulation = new bool(EncryptedString.of("Click Simulation"), true);
	private final bool strayBypass = new bool(EncryptedString.of("Bypass Mode"), true);
	private final bool allEntities = new bool(EncryptedString.of("All Entities"), false);
	private final bool useShield = new bool(EncryptedString.of("Use Shield"), false);
	private final number shieldTime = new number(EncryptedString.of("Shield Time"), 100, 1000, 350, 1);
	private final bool sticky = new bool(EncryptedString.of("Same Player"), false);

	private final TimerUtils timer = new TimerUtils();
	private int currentDelay = 0;
	private boolean isMaceAttack = false;

	public TriggerBot() {
		super(EncryptedString.of("Trigger Bot"),
				EncryptedString.of("Automatically hits entities with fully customizable triggers"),
				-1,
				Category.SWORD);
		setTag(EncryptedString.of("bannable in swordpvp"));
		addSettings(enableSwords, enableAxes, enableMaces, enableOthers,
				swordClick, axeClick, maceClick, otherClick,
				swordDelay, axeDelay, maceDelay,
				inScreen, whileUse, checkShield, whileAscend, sticky,
				onlyCritSword, onlyCritAxe, swing, clickSimulation,
				strayBypass, allEntities, useShield, shieldTime);
	}

	@Override
	public void onEnable() {
		currentDelay = swordDelay.getRandomValueInt();
		eventManager.add(TickListener.class, this);
		eventManager.add(AttackListener.class, this);
		eventManager.add(ItemUseListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		eventManager.remove(AttackListener.class, this);
		eventManager.remove(ItemUseListener.class, this);
		super.onDisable();
	}

	@SuppressWarnings("all")
	@Override
	public void onTick() {
		try {
			if (mc.currentScreen != null || mc.player.isSpectator()) return;

			HitManager hitManager = HitManager.getInstance();
			if (hitManager != null && hitManager.isEnabled() && hitManager.isSlamActive()) {
				return;
			}

			MaceSwap maceSwap = MaceSwap.getInstance();
			if (maceSwap != null && maceSwap.isEnabled() && maceSwap.isSlamActive()) {
				return;
			}

			ItemStack mainHandStack = mc.player.getMainHandStack();

			boolean isSword = mainHandStack.isIn(ItemTags.SWORDS);
			boolean isAxe = mainHandStack.isIn(ItemTags.AXES);
			boolean isMace = mainHandStack.isOf(Items.MACE);
			boolean isSpear = mainHandStack.contains(DataComponentTypes.PIERCING_WEAPON);
			boolean isOther = !isSword && !isAxe && !isMace && !isSpear;

			if (isSpear) {
				return;
			}

			if ((isSword && !enableSwords.getValue()) ||
					(isAxe && !enableAxes.getValue()) ||
					(isMace && !enableMaces.getValue()) ||
					(isOther && !enableOthers.getValue())) {
				return;
			}

			ClickType requiredClick = isMace ? maceClick.getMode() :
					isAxe ? axeClick.getMode() :
							isSword ? swordClick.getMode() :
									otherClick.getMode();

			if (!checkClickCondition(requiredClick)) {
				return;
			}

			if (!whileUse.getValue()) {
				if (mc.player.isUsingItem()) return;
				if (requiredClick != ClickType.RIGHT_CLICK && requiredClick != ClickType.ANY_CLICK) {
					ItemStack offHandStack = mc.player.getOffHandStack();
					if ((offHandStack.getComponents().contains(DataComponentTypes.FOOD) ||
							offHandStack.isOf(Items.SHIELD)) &&
							isMousePressed(GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
						return;
					}
				}
			}

			if (!whileAscend.getValue() && !mc.player.isOnGround()) {
				if (isMace) {
					if (mc.player.getVelocity().y > 0) return;
				} else {
					if (mc.player.getVelocity().y > 0 || mc.player.fallDistance <= 0.0F) return;
				}
			}

			range delaySetting = isAxe ? axeDelay : (isMace ? maceDelay : swordDelay);
			boolean enforceCrit = (isSword && onlyCritSword.getValue()) || (isAxe && onlyCritAxe.getValue());

			handleAttack(delaySetting, isMace, enforceCrit);

		} catch (Exception ignored) {}
	}

	private boolean checkClickCondition(ClickType clickType) {
		return switch (clickType) {
			case LEFT_CLICK -> isMousePressed(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			case RIGHT_CLICK -> isMousePressed(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			case ANY_CLICK -> isMousePressed(GLFW.GLFW_MOUSE_BUTTON_LEFT) || isMousePressed(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			case AUTOMATIC -> true;
		};
	}

	private boolean isMousePressed(int button) {
		if (mc.getWindow() == null) return false;
		return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), button) == GLFW.GLFW_PRESS;
	}

	private void handleAttack(range delaySetting, boolean isMace, boolean enforceCrit) {

		if (!AntibanbyDaduncs.canAttackNow()) {
			return;
		}

		if (!(mc.crosshairTarget instanceof EntityHitResult hit)) return;
		Entity entity = hit.getEntity();

		if (!isValidEntity(entity)) return;

		if (sticky.getValue() && mc.player.getAttacking() != null && entity != mc.player.getAttacking())
			return;

		if (entity instanceof PlayerEntity player) {
			MaceSwap maceSwap = MaceSwap.getInstance();
			boolean canStun = maceSwap != null && maceSwap.canStunSlam(player);
			if (!canStun && checkShield.getValue() && player.isBlocking() && !WorldUtils.isShieldFacingAway(player))
				return;
		}

		if (enforceCrit && (mc.player.fallDistance <= 0.0F || mc.player.getAttackCooldownProgress(0.5F) <= 0.90F))
			return;

		Vec3d eyePos = mc.player.getEyePos();
		Vec3d lookVec = WorldUtils.getPlayerLookVec(mc.player);
		double maxReach = isMace ? 2.90 : 2.95;
		if (!mc.player.isOnGround() && mc.player.getVelocity().y < 0) {
			maxReach -= Math.min(0.35, Math.abs(mc.player.getVelocity().y));
		}
		Vec3d vel = mc.player.getVelocity();
		double horizSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
		if (horizSpeed > 0.05) {
			maxReach -= Math.min(0.20, horizSpeed * 0.6);
		}

		if (!WorldUtils.doesRaycastHitEntity(mc.player, entity, maxReach)) {
			return;
		}

		if (isMace && entity instanceof LivingEntity living) {
			if (living.hurtTime > 1 && !isAboutToLand()) {
				return;
			}
		}

		if (timer.delay(currentDelay)) {
			if (mc.player.isUsingItem()) {
				if (mc.interactionManager != null) {
					mc.interactionManager.stopUsingItem(mc.player);
				}
				if (useShield.getValue() && mc.player.getOffHandStack().isOf(Items.SHIELD)) {
					MouseSimulation.mouseRelease(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				}
				return;
			}

			HitManager hitManager = HitManager.getInstance();
			if (hitManager != null && hitManager.isEnabled()) {
				hitManager.swapWeapon(entity);
			}

			MaceSwap maceSwap = MaceSwap.getInstance();
			if (maceSwap != null && maceSwap.isEnabled()) {
				maceSwap.handleAttack(entity);
			}

			if (!AntibanbyDaduncs.canAttackNow()) {
				return;
			}

			boolean holdingMace = isMace || (maceSwap != null && maceSwap.isEnabled() && maceSwap.isSwapActive());
			if (holdingMace) isMaceAttack = true;

			WorldUtils.hitEntity(entity, true);

			if (holdingMace) isMaceAttack = false;

			int baseDelay = delaySetting.getRandomValueInt();
			if (!isMace && ClickGUI.randomisedTiming.getValue()) {
				int var = (int) ClickGUI.VARIANCE;
				if (var > 0) {
					baseDelay += (int) ((Math.random() * 2 - 1) * var);
				}
			}
			currentDelay = Math.max(50, baseDelay);
			timer.reset();
		} else if (useShield.getValue() && mc.player.getOffHandStack().isOf(Items.SHIELD)) {
			MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT, shieldTime.getValueInt());
		}
	}

	private boolean isAboutToLand() {
		if (mc.player == null || mc.world == null) return true;
		if (mc.player.isOnGround()) return true;

		double fallSpeed = Math.abs(mc.player.getVelocity().y);
		double checkDist = Math.max(1.2, fallSpeed * 1.5);
		Vec3d start = mc.player.getEntityPos();
		Vec3d end = start.add(0, -checkDist, 0);

		BlockHitResult hit = mc.world.raycast(new RaycastContext(
				start,
				end,
				RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE,
				mc.player
		));

		return hit.getType() != HitResult.Type.MISS;
	}

	private boolean isValidEntity(Entity entity) {
		if (entity == null || entity == mc.player || entity.isRemoved() || !entity.isAlive()) {
			return false;
		}
		if (entity instanceof LivingEntity living && (living.isDead() || living.getHealth() <= 0.0F)) {
			return false;
		}
		if (entity.isSpectator() || entity.isInvulnerable()) {
			return false;
		}
		if (entity instanceof PlayerEntity player) {
			Friends friends = System.INSTANCE.getModuleManager().getModule(Friends.class);
			if (friends != null && friends.isEnabled() && friends.antiAttack.getValue() &&
					System.INSTANCE.getFriendManager().isFriend(player)) {
				return false;
			}
			return true;
		}
		if (strayBypass.getValue() && entity instanceof ZombieEntity) {
			return true;
		}
		return allEntities.getValue();
	}

	@Override
	public void onAttack(AttackEvent event) {
		if (isMaceAttack) return;

		MaceSwap maceSwap = MaceSwap.getInstance();
		if (maceSwap != null && maceSwap.isEnabled() && maceSwap.isSwapActive()) return;

		if (AntibanbyDaduncs.isAttackSent() || !isMousePressed(GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
			event.cancel();
		}
	}

	public boolean isMaceAttackActive() {
		return isMaceAttack;
	}

	public boolean isRightClickTriggerActive() {
		if (!isEnabled() || mc.player == null) return false;
		if (!inScreen.getValue() && mc.currentScreen != null) return false;

		ItemStack mainHandStack = mc.player.getMainHandStack();
		boolean isSword = mainHandStack.isIn(ItemTags.SWORDS);
		boolean isAxe = mainHandStack.isIn(ItemTags.AXES);
		boolean isMace = mainHandStack.isOf(Items.MACE);
		boolean isOther = !isSword && !isAxe && !isMace;

		if ((isSword && !enableSwords.getValue()) ||
				(isAxe && !enableAxes.getValue()) ||
				(isMace && !enableMaces.getValue()) ||
				(isOther && !enableOthers.getValue())) {
			return false;
		}

		ClickType requiredClick = isMace ? maceClick.getMode() :
				isAxe ? axeClick.getMode() :
						isSword ? swordClick.getMode() :
								otherClick.getMode();

		return requiredClick == ClickType.RIGHT_CLICK || requiredClick == ClickType.ANY_CLICK;
	}

	@Override
	public void onItemUse(ItemUseEvent event) {
		if (mc.player == null) return;

		if (AntibanbyDaduncs.isAttackSent()) {
			event.cancel();
			return;
		}

		if (isRightClickTriggerActive() && isMousePressed(GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
			if (!whileUse.getValue() || (mc.crosshairTarget instanceof EntityHitResult hit && isValidEntity(hit.getEntity()))) {
				event.cancel();
			}
		}
	}
}