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

package dlindustries.uk.daduncs.module.modules.crystal;

import dlindustries.uk.daduncs.event.events.ItemUseListener;
import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.bind;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

public final class AutoCrystal extends Module implements TickListener, ItemUseListener {
	private final bind activateKey = new bind(EncryptedString.of("Activate Key"), GLFW.GLFW_MOUSE_BUTTON_RIGHT, false)
			.setDescription(EncryptedString.of("Key that does the crystalling"));

	private final number placeDelay = new number(EncryptedString.of("Place Delay"), 0, 20, 3, 1);
	private final number breakDelay = new number(EncryptedString.of("Break Delay"), 0, 20, 2, 1);

	private final number placeChance = new number(EncryptedString.of("Place Chance"), 0, 100, 100, 1)
			.setDescription(EncryptedString.of("Randomization"));
	private final number breakChance = new number(EncryptedString.of("Break Chance"), 0, 100, 100, 1)
			.setDescription(EncryptedString.of("Randomization"));

	private final bool friendSafe = new bool(EncryptedString.of("Friend Safe"), true)
			.setDescription(EncryptedString.of("Doesn't crystal next to a friend"));
	private final bool stopOnKill = new bool(EncryptedString.of("Stop on Kill"), false)
			.setDescription(EncryptedString.of("Won't crystal if a dead player is nearby"));
	private final bool fakePunch = new bool(EncryptedString.of("Fake Punch"), false)
			.setDescription(EncryptedString.of("Will hit every entity and block if you miss a hitcrystal"));
	private final bool clickSimulation = new bool(EncryptedString.of("Click Simulation"), false)
			.setDescription(EncryptedString.of("Makes the CPS hud think you're legit"));
	private final bool damageTick = new bool(EncryptedString.of("Damage Tick"), false)
			.setDescription(EncryptedString.of("Times your crystals for a perfect d-tap"));
	private final bool antiWeakness = new bool(EncryptedString.of("Anti-Weakness"), false)
			.setDescription(EncryptedString.of("Silently switches to a sword and then hits the crystal if you have weakness"));

	private final number particleChance = new number(EncryptedString.of("Particle Chance"), 0, 100, 20, 1)
			.setDescription(EncryptedString.of("Adds block breaking particles to make it seem more legit from your POV (Only works with fake punch)"));

	private TimerUtils placeTimer = new TimerUtils();
	private TimerUtils breakTimer = new TimerUtils();
	public boolean crystalling;

	private boolean wasPressed;
	private int antiWeaknessRestoreSlot = -1;

	public AutoCrystal() {
		super(EncryptedString.of("Auto Crystal"),
				EncryptedString.of("Automatically crystals fast for you"),
				-1,
				Category.CRYSTAL);
		addSettings(activateKey, placeDelay, breakDelay, placeChance, breakChance, friendSafe, stopOnKill, fakePunch, clickSimulation, damageTick, antiWeakness, particleChance);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		eventManager.add(ItemUseListener.class, this);

		placeTimer.reset();
		breakTimer.reset();
		crystalling = false;
		wasPressed = false;
		antiWeaknessRestoreSlot = -1;
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		eventManager.remove(ItemUseListener.class, this);
		crystalling = false;
		wasPressed = false;
		antiWeaknessRestoreSlot = -1;
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.currentScreen != null)
			return;

		if (antiWeaknessRestoreSlot != -1) {
			if (!mc.player.getMainHandStack().isOf(Items.END_CRYSTAL) && !WorldUtils.isSword(mc.player.getMainHandStack())) {
				antiWeaknessRestoreSlot = -1;
			} else if (!AntibanbyDaduncs.isActionSent() && !AntibanbyDaduncs.isSlotPending()) {
				dlindustries.uk.daduncs.managers.Recorder tm = dlindustries.uk.daduncs.managers.Recorder.getInstance();
				if (tm != null) {
					tm.recordModuleEvent("AutoCrystal", "ANTI_WEAKNESS_RESTORE", "Restoring slot to previousSlot " + antiWeaknessRestoreSlot + " at tick start", null);
				}
				InventoryUtils.setInvSlot(antiWeaknessRestoreSlot);
				antiWeaknessRestoreSlot = -1;
			}
		}

		long placeDelayMs = placeDelay.getValueLong() * 50L;
		long breakDelayMs = breakDelay.getValueLong() * 50L;

		if (ClickGUI.randomisedTiming.getValue()) {
			long variance = (long) ClickGUI.VARIANCE;
			long randomOffset = (long) (Math.random() * variance);
			placeDelayMs += randomOffset;
			breakDelayMs += randomOffset;
		}

		boolean dontPlace = !placeTimer.hasReached(placeDelayMs);
		boolean dontBreak = !breakTimer.hasReached(breakDelayMs);

		if (stopOnKill.getValue() && WorldUtils.isDeadBodyNearby())
			return;

		if (friendSafe.getValue() && friendNear())
			return;

		if (mc.player.isUsingItem())
			return;

		if (damageTick.getValue() && damageTickCheck())
			return;

		boolean isCrystalInHand = mc.player.getMainHandStack().isOf(Items.END_CRYSTAL);
		boolean isRmbPressed = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
		boolean isActivateKeyPressed = (activateKey.getKey() != -1 && KeyUtils.isKeyPressed(activateKey.getKey()));

		boolean isKeyDown = isCrystalInHand && (isActivateKeyPressed || isRmbPressed);

		if (!isKeyDown) {

			if (wasPressed) {
				antiWeaknessRestoreSlot = -1;
				if (mc.crosshairTarget instanceof EntityHitResult hit) {
					attemptBreakEntity(hit, true);
				}

				placeTimer.reset();
				breakTimer.reset();
				crystalling = false;
				wasPressed = false;
			}
			return;
		}

		boolean isFirstPlace = !wasPressed;
		wasPressed = true;
		crystalling = true;

		if (mc.player.getMainHandStack().getItem() != Items.END_CRYSTAL)
			return;

		int randomInt = MathUtils.randomInt(1, 100);

		if (mc.crosshairTarget instanceof BlockHitResult hit) {
			if (mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {

				if ((!dontPlace || isFirstPlace) && randomInt <= placeChance.getValueInt()) {
					if (!AntibanbyDaduncs.isActionSent() && !AntibanbyDaduncs.isSlotPending()) {
						if ((BlockUtils.isBlock(hit.getBlockPos(), Blocks.OBSIDIAN) || BlockUtils.isBlock(hit.getBlockPos(), Blocks.BEDROCK)) && CrystalUtils.canPlaceCrystalClientAssumeObsidian(hit.getBlockPos())) {

							if (clickSimulation.getValue())
								MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);

							dlindustries.uk.daduncs.managers.Recorder tm = dlindustries.uk.daduncs.managers.Recorder.getInstance();
							if (tm != null) {
								tm.recordModuleEvent("AutoCrystal", "PLACE_CRYSTAL",
										"WorldUtils.placeBlock at " + hit.getBlockPos().toShortString() + " side=" + hit.getSide() + ", placeTimer=" + placeTimer.getElapsedTime() + "ms", null);
							}

							WorldUtils.placeBlock(hit, true);

							placeTimer.reset();
						}
					}
				}

				if (fakePunch.getValue()) {
					if (!dontBreak && randomInt <= breakChance.getValueInt()) {

						if (BlockUtils.isBlock(hit.getBlockPos(), Blocks.OBSIDIAN) || BlockUtils.isBlock(hit.getBlockPos(), Blocks.BEDROCK))
							return;

						if (!canFakePunchBlock(hit.getBlockPos()))
							return;

						if (!AntibanbyDaduncs.isActionSent() && !AntibanbyDaduncs.isSlotPending()) {
							if (clickSimulation.getValue()) {
								MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_LEFT);
							}

							boolean brokeOrProgressed = mc.interactionManager.isBreakingBlock()
									? mc.interactionManager.updateBlockBreakingProgress(hit.getBlockPos(), hit.getSide())
									: mc.interactionManager.attackBlock(hit.getBlockPos(), hit.getSide());
							if (brokeOrProgressed) {
								mc.player.swingHand(Hand.MAIN_HAND);
							}

							breakTimer.reset();
						}
					}

					if (!dontPlace && randomInt <= placeChance.getValueInt() && dontBreak) {
						if (clickSimulation.getValue())
							MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
					}
				}
			}

			if (mc.crosshairTarget.getType() == HitResult.Type.MISS) {
				if (fakePunch.getValue()) {
					if (!dontBreak && randomInt <= breakChance.getValueInt()) {
						if (!AntibanbyDaduncs.isActionSent() && !AntibanbyDaduncs.isSlotPending()) {
							if (mc.interactionManager.hasLimitedAttackSpeed())
								mc.attackCooldown = 10;

							if (clickSimulation.getValue())
								MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_LEFT);

							mc.player.swingHand(Hand.MAIN_HAND);

							breakTimer.reset();
						}
					}

					if (!dontPlace && randomInt <= placeChance.getValueInt() && dontBreak) {
						if (clickSimulation.getValue())
							MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
					}
				}
			}
		}

		randomInt = MathUtils.randomInt(1, 100);

		if (mc.crosshairTarget instanceof EntityHitResult hit) {
			if (!dontBreak && randomInt <= breakChance.getValueInt()) {
				attemptBreakEntity(hit, false);
			}
		}
	}

	private void attemptBreakEntity(EntityHitResult hit, boolean forceBreak) {
		Entity entity = hit.getEntity();

		if (entity instanceof PlayerEntity)
			return;

		if (!fakePunch.getValue() && !(entity instanceof EndCrystalEntity || entity instanceof SlimeEntity))
			return;

		if (!AntibanbyDaduncs.canAttackNow())
			return;

		int previousSlot = mc.player.getInventory().getSelectedSlot();
		boolean needAntiWeakness = (entity instanceof EndCrystalEntity || entity instanceof SlimeEntity) && antiWeakness.getValue() && cantBreakCrystal();

		dlindustries.uk.daduncs.managers.Recorder tm = dlindustries.uk.daduncs.managers.Recorder.getInstance();
		if (tm != null) {
			tm.recordModuleEvent("AutoCrystal", "ATTEMPT_BREAK",
					"Entity " + entity.getName().getString() + " (id=" + entity.getId() + ", force=" + forceBreak + ", needAntiWeakness=" + needAntiWeakness + ", prevSlot=" + previousSlot + ")", null);
		}

		if (needAntiWeakness) {
			if (tm != null) {
				tm.recordModuleEvent("AutoCrystal", "ANTI_WEAKNESS_SWAP", "Selecting sword (slot=" + previousSlot + " -> sword)", null);
			}
			InventoryUtils.selectSword();
		}

		if (clickSimulation.getValue())
			MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_LEFT);

		WorldUtils.hitEntity(entity, true);

		if (!forceBreak) {
			breakTimer.reset();
		}

		if(antiWeakness.getValue() && needAntiWeakness) {
			antiWeaknessRestoreSlot = previousSlot;
		}
	}

	public java.util.Map<String, Object> recorder() {
		java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
		map.put("crystalling", crystalling);
		map.put("wasPressed", wasPressed);
		map.put("placeTimerElapsedMs", placeTimer.getElapsedTime());
		map.put("breakTimerElapsedMs", breakTimer.getElapsedTime());
		map.put("placeDelay", placeDelay.getValue());
		map.put("breakDelay", breakDelay.getValue());
		map.put("placeChance", placeChance.getValue());
		map.put("breakChance", breakChance.getValue());
		map.put("antiWeakness", antiWeakness.getValue());
		map.put("fakePunch", fakePunch.getValue());
		map.put("clickSimulation", clickSimulation.getValue());
		map.put("damageTick", damageTick.getValue());
		return map;
	}

	@Override
	public void onItemUse(ItemUseEvent event) {
		if (mc.player == null) return;
		if (mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) {
			boolean isRmbPressed = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
			boolean isActivateKeyPressed = (activateKey.getKey() != -1 && KeyUtils.isKeyPressed(activateKey.getKey()));
			boolean isKeyDown = isActivateKeyPressed || isRmbPressed;
			if (crystalling || isKeyDown || !(mc.crosshairTarget instanceof BlockHitResult h
					&& mc.crosshairTarget.getType() == HitResult.Type.BLOCK
					&& (BlockUtils.isBlock(h.getBlockPos(), Blocks.OBSIDIAN) || BlockUtils.isBlock(h.getBlockPos(), Blocks.BEDROCK)))) {
				event.cancel();
			}
		}
	}

	private boolean canFakePunchBlock(net.minecraft.util.math.BlockPos pos) {
		if (mc.world == null || mc.player == null)
			return false;
		if (mc.player.getMainHandStack().contains(DataComponentTypes.PIERCING_WEAPON))
			return false;

		BlockState state = mc.world.getBlockState(pos);
		if (state.isAir()
				|| state.isOf(Blocks.WATER)
				|| state.isOf(Blocks.LAVA)
				|| state.isOf(Blocks.BUBBLE_COLUMN)
				|| state.isOf(Blocks.MOVING_PISTON)
				|| state.isOf(Blocks.FIRE)
				|| state.isOf(Blocks.SOUL_FIRE)) {
			return false;
		}
		if (state.isOf(Blocks.LIGHT)
				&& !mc.player.getMainHandStack().isOf(Items.LIGHT)
				&& !mc.player.getOffHandStack().isOf(Items.LIGHT)) {
			return false;
		}
		return state.getHardness(mc.world, pos) >= 0.0f;
	}

	private boolean friendNear() {
		if (mc.crosshairTarget == null || mc.world == null)
			return false;

		for (net.minecraft.entity.player.PlayerEntity player : mc.world.getPlayers()) {
			if (player == mc.player || !dlindustries.uk.daduncs.System.INSTANCE.getFriendManager().isFriend(player))
				continue;

			if (player.getEntityPos().distanceTo(mc.crosshairTarget.getPos()) < 6)
				return true;
		}

		return false;
	}

	private boolean cantBreakCrystal() {
		assert mc.player != null;
		StatusEffectInstance weakness = mc.player.getStatusEffect(StatusEffects.WEAKNESS);
		StatusEffectInstance strength = mc.player.getStatusEffect(StatusEffects.STRENGTH);
		return (!(weakness == null || strength != null && strength.getAmplifier() > weakness.getAmplifier() || WorldUtils.isTool(mc.player.getMainHandStack())));
	}

	private boolean damageTickCheck() {
		return mc.world.getPlayers().stream()
				.filter(e -> e != mc.player)
				.filter(e -> e.squaredDistanceTo(mc.player) < 36)
				.filter(e -> e.getLastAttacker() == null)
				.filter(e -> !e.isOnGround())
				.anyMatch(e -> e.hurtTime >= 2)
				&& !(mc.player.getAttacking() instanceof PlayerEntity);
	}
}