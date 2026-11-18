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

import dlindustries.uk.daduncs.System;
import dlindustries.uk.daduncs.event.events.AttackListener;
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
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

public final class DtapSetup extends Module implements TickListener, ItemUseListener, AttackListener {
	private final bind activateKey = new bind(EncryptedString.of("crystal bind"), GLFW.GLFW_KEY_UNKNOWN, false)
			.setDescription(EncryptedString.of("your crystal keybind"));
	private final bool checkPlace = new bool(EncryptedString.of("Check Place"), false)
			.setDescription(EncryptedString.of("Checks if you can place the obsidian on that block"));
	private final number switchDelay = new number(EncryptedString.of("Switch Delay"), 0, 20, 0, 1);
	private final number switchChance = new number(EncryptedString.of("Switch Chance"), 0, 100, 100, 1);
	private final number placeDelay = new number(EncryptedString.of("Place Delay"), 0, 20, 0, 1);
	private final number placeChance = new number(EncryptedString.of("Place Chance"), 0, 100, 100, 1).setDescription(EncryptedString.of("Randomization"));
	private final bool clickSimulation = new bool(EncryptedString.of("Click Simulation"), false)
			.setDescription(EncryptedString.of("Records module clicks as simulated input"));

	private final TimerUtils switchTimer = new TimerUtils();
	private final TimerUtils placeTimer = new TimerUtils();
	private long lastObsidianPlaceTime = 0;
	private BlockPos lastObsidianPos;
	private boolean activateKeyWasPressed;
	private boolean active;
	private boolean crystalling;

	public DtapSetup() {
		super(EncryptedString.of("DtapSetup"),
				EncryptedString.of("automatically sets up an obsidian crystal the second you press your crystal keybind provided you are holding right click"),
				-1,
				Category.CRYSTAL);
		addSettings(activateKey, checkPlace, switchDelay, switchChance, placeDelay, placeChance, clickSimulation);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		eventManager.add(ItemUseListener.class, this);
		eventManager.add(AttackListener.class, this);
		reset();

		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		eventManager.remove(ItemUseListener.class, this);
		eventManager.remove(AttackListener.class, this);
		reset();
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.currentScreen != null || mc.player == null || mc.player.isUsingItem())
			return;

		long switchDelayMs = switchDelay.getValueLong() * 50L;
		long placeDelayMs = placeDelay.getValueLong() * 50L;

		if (ClickGUI.randomisedTiming.getValue()) {
			long variance = (long) ClickGUI.VARIANCE;
			switchDelayMs += (long) (Math.random() * variance);
			placeDelayMs += (long) (Math.random() * variance);
		}

		boolean switchReady = switchTimer.hasReached(switchDelayMs);
		boolean placeReady = placeTimer.hasReached(placeDelayMs);

		boolean isActivateKeyPressed = (activateKey.getKey() != -1 && KeyUtils.isKeyPressed(activateKey.getKey()));
		boolean isRmbPressed = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
		boolean isHoldingCrystal = mc.player.isHolding(Items.END_CRYSTAL);

		boolean fJustPressed = isActivateKeyPressed && !activateKeyWasPressed;
		activateKeyWasPressed = isActivateKeyPressed;

		if (fJustPressed && crystalling) {
			if (mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
				BlockPos targetPos = hit.getBlockPos();
				boolean isObiOrBedrock = BlockUtils.isBlock(targetPos, Blocks.OBSIDIAN) || BlockUtils.isBlock(targetPos, Blocks.BEDROCK);
				boolean isSameRecentObi = targetPos.equals(lastObsidianPos) && (java.lang.System.currentTimeMillis() - lastObsidianPlaceTime < 500);

				if (!isObiOrBedrock && !isSameRecentObi) {
					crystalling = false;
					active = false;
					switchTimer.reset();
					placeTimer.reset();
				}
			}
		}

		if (isActivateKeyPressed || (crystalling && isRmbPressed && (isHoldingCrystal || mc.player.isHolding(Items.OBSIDIAN)))) {
			if (mc.crosshairTarget instanceof BlockHitResult hitResult && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
				if (!active && !BlockUtils.canPlaceBlockClient(hitResult.getBlockPos()) && checkPlace.getValue())
					return;
			}

			ItemStack mainHandStack = mc.player.getMainHandStack();

			if (!(WorldUtils.isWeapon(mainHandStack) || mainHandStack.isOf(Items.MACE) || mainHandStack.isOf(Items.END_CRYSTAL) || mainHandStack.isOf(Items.OBSIDIAN))) {
				reset();
				return;
			}

			active = true;

			if (!crystalling) {
				if (mc.crosshairTarget instanceof BlockHitResult hit) {
					if (hit.getType() == HitResult.Type.MISS)
						return;

					if (!BlockUtils.isBlock(hit.getBlockPos(), Blocks.OBSIDIAN)) {
						if (BlockUtils.isBlock(hit.getBlockPos(), Blocks.RESPAWN_ANCHOR) && BlockUtils.isAnchorCharged(hit.getBlockPos()))
							return;

						resetKeys();

						if (!mc.player.getMainHandStack().isOf(Items.OBSIDIAN)) {
							if (!switchReady || !AntibanbyDaduncs.canSwapSlotNow() || AntibanbyDaduncs.isSlotPending()) {
								return;
							}

							int randomNum = MathUtils.randomInt(1, 100);
							if (randomNum <= switchChance.getValueInt()) {
								if (InventoryUtils.selectItemFromHotbar(Items.OBSIDIAN)) {
									switchTimer.reset();
									dlindustries.uk.daduncs.managers.Recorder tm = dlindustries.uk.daduncs.managers.Recorder.getInstance();
									if (tm != null) {
										tm.recordModuleEvent("dtapsetup", "SWITCH_OBSIDIAN", "Selecting OBSIDIAN from hotbar", null);
									}
								}
							}
						}

						if (mc.player.getMainHandStack().isOf(Items.OBSIDIAN)) {
							if (!placeReady || mc.interactionManager == null || mc.interactionManager.isBreakingBlock()
									|| !AntibanbyDaduncs.canInteractBlockNow(net.minecraft.util.Hand.MAIN_HAND, hit)
									|| AntibanbyDaduncs.isSlotPending()) {
								return;
							}

							int randomNum = MathUtils.randomInt(1, 100);

							if (randomNum <= placeChance.getValueInt()) {
								if (WorldUtils.placeBlock(hit, true)) {
									if (clickSimulation.getValue())
										MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);

									dlindustries.uk.daduncs.managers.Recorder tm = dlindustries.uk.daduncs.managers.Recorder.getInstance();
									if (tm != null) {
										tm.recordModuleEvent("dtapsetup", "PLACE_OBSIDIAN", "WorldUtils.placeBlock at " + hit.getBlockPos().toShortString() + " -> crystalling=true", null);
									}

									lastObsidianPlaceTime = java.lang.System.currentTimeMillis();
									lastObsidianPos = hit.getBlockPos();
									placeTimer.reset();
									crystalling = true;

									resetKeys();
									switchTimer.reset();
									return;
								}
							}
						}
					} else {
						resetKeys();
						crystalling = true;
					}
				}
			}

			if (crystalling) {
				if (!mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) {
					if (!switchReady || !AntibanbyDaduncs.canSwapSlotNow() || AntibanbyDaduncs.isSlotPending()) {
						return;
					}

					int randomNum = MathUtils.randomInt(1, 100);

					if (randomNum <= switchChance.getValueInt()) {
						if (InventoryUtils.selectItemFromHotbar(Items.END_CRYSTAL)) {
							dlindustries.uk.daduncs.managers.Recorder tm = dlindustries.uk.daduncs.managers.Recorder.getInstance();
							if (tm != null) {
								tm.recordModuleEvent("dtapsetup", "SWITCH_CRYSTAL", "Selecting END_CRYSTAL from hotbar", null);
							}
							switchTimer.reset();
						}
					}
				}

				if (mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) {
					AutoCrystal autoCrystal = System.INSTANCE.getModuleManager().getModule(AutoCrystal.class);

					if (autoCrystal != null && !autoCrystal.isEnabled()) {
						dlindustries.uk.daduncs.managers.Recorder tm = dlindustries.uk.daduncs.managers.Recorder.getInstance();
						if (tm != null) {
							tm.recordModuleEvent("dtapsetup", "DELEGATE_AUTOCRYSTAL", "Calling autoCrystal.onTick() while holding END_CRYSTAL", null);
						}
						autoCrystal.onTick();
					}
				}
			}
		} else {
			reset();
		}
	}

	public java.util.Map<String, Object> recorder() {
		java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
		map.put("active", active);
		map.put("crystalling", crystalling);
		map.put("switchTimerElapsedMs", switchTimer.getElapsedTime());
		map.put("placeTimerElapsedMs", placeTimer.getElapsedTime());
		map.put("timeSinceLastObiPlaceMs", java.lang.System.currentTimeMillis() - lastObsidianPlaceTime);
		map.put("switchDelay", switchDelay.getValue());
		map.put("placeDelay", placeDelay.getValue());
		map.put("switchChance", switchChance.getValue());
		map.put("placeChance", placeChance.getValue());
		map.put("checkPlace", checkPlace.getValue());
		return map;
	}

	@Override
	public void onItemUse(ItemUseEvent event) {
		if (mc.player == null)
			return;

		ItemStack mainHandStack = mc.player.getMainHandStack();

		if (mainHandStack.isOf(Items.OBSIDIAN)) {
			if (active || crystalling || (java.lang.System.currentTimeMillis() - lastObsidianPlaceTime < 500)) {
				event.cancel();
				dlindustries.uk.daduncs.managers.Recorder tm = dlindustries.uk.daduncs.managers.Recorder.getInstance();
				if (tm != null) {
					tm.recordModuleEvent("dtapsetup", "CANCEL_OBSIDIAN_USE", "Cancelled vanilla right-click with OBSIDIAN", null);
				}
			}
		} else if (mainHandStack.isOf(Items.END_CRYSTAL)) {
			if (active || crystalling) {
				event.cancel();
			}
		}
	}

	public void reset() {
		switchTimer.reset();
		placeTimer.reset();
		active = false;
		crystalling = false;
		activateKeyWasPressed = false;
		lastObsidianPos = null;
	}

	@Override
	public void onPlayerChanged() {
		reset();
		lastObsidianPlaceTime = 0;
	}

	@Override
	public void onAttack(AttackEvent event) {
		if (mc.player == null) return;
		if (mc.player.getMainHandStack().isOf(Items.END_CRYSTAL) && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS)
			event.cancel();
	}

	private void resetKeys() {
		if (mc.interactionManager != null && mc.interactionManager.isBreakingBlock()) {
			mc.interactionManager.cancelBlockBreaking();
		}
		if (mc.options == null) return;
		if (mc.options.hotbarKeys != null) {
			for (net.minecraft.client.option.KeyBinding hotbarKey : mc.options.hotbarKeys) {
				while (hotbarKey.wasPressed()) {}
			}
		}
		if (mc.options.useKey != null) {
			while (mc.options.useKey.wasPressed()) {}
			mc.options.useKey.setPressed(false);
		}
	}
}
