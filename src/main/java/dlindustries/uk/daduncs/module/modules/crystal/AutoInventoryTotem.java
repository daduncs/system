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

import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.event.events.MouseUpdateListener;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.mode;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.InventoryUtils;
import dlindustries.uk.daduncs.utils.TimerUtils;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class AutoInventoryTotem extends Module implements TickListener, MouseUpdateListener {
	public enum Mode {
		Blatant, Random
	}

	private enum State {
		IDLE,
		AWAIT_OPEN_DELAY,
		EQUIP_OFFHAND,
		AWAIT_INTER_CLICK,
		EQUIP_HOTBAR,
		COMPLETED,
		AWAIT_CLOSE
	}

	private final mode<Mode> mode = new mode<>(EncryptedString.of("Mode"), Mode.Random, Mode.class)
			.setDescription(EncryptedString.of("Whether to randomize the toteming pattern or no"));
	private final number delay = new number(EncryptedString.of("Delay"), 0, 20, 1, 1);
	private final bool hotbar = new bool(EncryptedString.of("Hotbar"), true)
			.setDescription(EncryptedString.of("Puts a totem in your hotbar as well, if enabled (Setting below will work if this is enabled)"));
	private final number totemSlot = new number(EncryptedString.of("Totem Slot"), 1, 9, 9, 1)
			.setDescription(EncryptedString.of("Your preferred totem slot"));
	private final bool autoSwitch = new bool(EncryptedString.of("Auto Switch"), true)
			.setDescription(EncryptedString.of("Switches to totem slot when going inside the inventory"));
	private final bool forceTotem = new bool(EncryptedString.of("Force Totem"), true)
			.setDescription(EncryptedString.of("Puts the totem in the slot, regardless if its space is taken up by something else"));
	private final bool autoOpen = new bool(EncryptedString.of("Auto Open"), false)
			.setDescription(EncryptedString.of("will get you banned on servers with totemguard installed."))
			.setTag(EncryptedString.of("BANNABLE"))
			.setShowDescription(true);
	private final number stayOpenFor = new number(EncryptedString.of("Stay Open For"), 0, 20, 0, 1);

	private final TimerUtils actionTimer = new TimerUtils();
	private final TimerUtils closeTimer = new TimerUtils();
	private final Random random = new Random();

	private State state = State.IDLE;
	private InventoryScreen activeScreen;
	private InventoryScreen openedScreen;
	private long targetOpenDelayMs = 0L;
	private long targetInterClickDelayMs = 0L;
	private long targetStayOpenMs = 0L;
	private int lastSwappedSlot = -1;
	private int earliestClickTick = -1;

	public AutoInventoryTotem() {
		super(EncryptedString.of("Auto Inv Totem"),
				EncryptedString.of("Automatically equips a totem in your offhand and main hand if empty"),
				-1,
				Category.CRYSTAL);
		addSettings(mode, delay, hotbar, totemSlot, autoSwitch, forceTotem, autoOpen, stayOpenFor);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		eventManager.add(MouseUpdateListener.class, this);
		resetState();
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		eventManager.remove(MouseUpdateListener.class, this);
		resetState();
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.world == null) {
			resetState();
			return;
		}

		if (UtilitySwapper.isSwapping) return;

		if (autoSwitch.getValue() && InventoryUtils.isPlayerInventoryOpenQueued()) {
			switchBeforeOpen();
		}

		if (autoOpen.getValue() && shouldOpenScreen()) {
			if (mc.player.isSprinting()) {
				stopSprintBeforeClick();
				return;
			}
			if (mc.player.isUsingItem() || mc.player.isSneaking() || hasActiveMovementInput()) {
				return;
			}
			if (autoSwitch.getValue()) {
				switchBeforeOpen();
			}
			var screen = new InventoryScreen(mc.player);
			mc.setScreen(screen);
			if (mc.currentScreen != screen) return;
			openedScreen = activeScreen = screen;
			state = State.AWAIT_OPEN_DELAY;
			targetOpenDelayMs = reactionDelay();
			actionTimer.reset();
			lastSwappedSlot = -1;
			return;
		}

		processInventoryLogic();
	}

	@Override
	public void onMouseUpdate() {
		// Screen clicks may run between game ticks, as vanilla GUI input does.
		// Auto Open remains on the tick path and keeps its movement checks.
		processInventoryLogic();
	}

	private boolean hasActiveMovementInput() {
		if (mc.player == null || mc.player.input == null || mc.player.input.playerInput == null) return false;
		var pi = mc.player.input.playerInput;
		return pi.forward() || pi.backward() || pi.left() || pi.right() || pi.jump() || pi.sneak();
	}

	private void processInventoryLogic() {
		if (mc.player == null || mc.world == null || mc.interactionManager == null) {
			resetState();
			return;
		}

		if (!(mc.currentScreen instanceof InventoryScreen screen)
				|| mc.player.currentScreenHandler != screen.getScreenHandler()) {
			if (activeScreen != null) resetState();
			return;
		}
		if (screen != activeScreen) {
			resetState();
			activeScreen = screen;
		}

		if (UtilitySwapper.isSwapping) return;
		if (!screen.getScreenHandler().getCursorStack().isEmpty()) return;

		if (mc.player.age < earliestClickTick) return;

		if (state == State.IDLE) {
			if (mc.player.isSprinting()) {
				stopSprintBeforeClick();
			}
			state = State.AWAIT_OPEN_DELAY;
			boolean needsOffhand = mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING;
			int candidateSlot = -1;
			if (needsOffhand) {
				candidateSlot = getBestTotemSlot(-1, 4, 0);
			} else if (hotbar.getValue()) {
				int targetHotbar = totemSlot.getValueInt() - 1;
				ItemStack hotbarStack = mc.player.getInventory().getStack(targetHotbar);
				boolean needsHotbar = hotbarStack.isEmpty() || (forceTotem.getValue() && hotbarStack.getItem() != Items.TOTEM_OF_UNDYING);
				if (needsHotbar) {
					candidateSlot = getBestTotemSlot(-1, 4, 0);
				}
			}
			targetOpenDelayMs = reactionDelay(candidateSlot);
			actionTimer.reset();
			lastSwappedSlot = -1;
			return;
		}

		if (state == State.AWAIT_OPEN_DELAY) {
			if (mc.player.isSprinting()) {
				stopSprintBeforeClick();
				return;
			}
			if (actionTimer.hasReached(targetOpenDelayMs)) {
				state = State.EQUIP_OFFHAND;
			} else {
				return;
			}
		}

		if (state == State.EQUIP_OFFHAND) {
			if (mc.player.isSprinting()) {
				stopSprintBeforeClick();
				return;
			}
			if (mc.player.isSneaking() || mc.player.isUsingItem() || hasActiveMovementInput()) {
				return;
			}

			// Explicit inventory slots do not depend on the selected hotbar slot.
			if (!AntibanbyDaduncs.canInventoryActionNow()) {
				return;
			}

			boolean needsOffhand = mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING;
			if (needsOffhand) {
				int slot = getBestTotemSlot(-1, 4, 0);
				if (slot != -1) {
					int syncId = ((InventoryScreen) mc.currentScreen).getScreenHandler().syncId;
					mc.interactionManager.clickSlot(syncId, slot, 40, SlotActionType.SWAP, mc.player);
					lastSwappedSlot = slot;
		
					int nextSlot = -1;
					if (hotbar.getValue()) {
						int targetHotbar = totemSlot.getValueInt() - 1;
						ItemStack hotbarStack = mc.player.getInventory().getStack(targetHotbar);
						boolean needsHotbar = hotbarStack.isEmpty() || (forceTotem.getValue() && hotbarStack.getItem() != Items.TOTEM_OF_UNDYING);
						if (needsHotbar) {
							nextSlot = findTotemSlotFromSlot(lastSwappedSlot);
						}
					}
					targetInterClickDelayMs = clickDelay(lastSwappedSlot, nextSlot);
					actionTimer.reset();
					state = State.AWAIT_INTER_CLICK;
					return;
				}
			}
			state = State.EQUIP_HOTBAR;
		}

		if (state == State.AWAIT_INTER_CLICK) {
			if (actionTimer.hasReached(targetInterClickDelayMs)) {
				state = State.EQUIP_HOTBAR;
			} else {
				return;
			}
		}

		if (state == State.EQUIP_HOTBAR) {
			if (mc.player.isSprinting()) {
				stopSprintBeforeClick();
				return;
			}
			if (mc.player.isSneaking() || mc.player.isUsingItem() || hasActiveMovementInput()) {
				return;
			}

			// Explicit inventory slots do not depend on the selected hotbar slot.
			if (!AntibanbyDaduncs.canInventoryActionNow()) {
				return;
			}

			if (hotbar.getValue()) {
				int targetHotbar = totemSlot.getValueInt() - 1;
				ItemStack hotbarStack = mc.player.getInventory().getStack(targetHotbar);
				boolean needsHotbar = hotbarStack.isEmpty() || (forceTotem.getValue() && hotbarStack.getItem() != Items.TOTEM_OF_UNDYING);

				if (needsHotbar) {
					int slot = findTotemSlotFromSlot(lastSwappedSlot);
					if (slot != -1) {
						int syncId = ((InventoryScreen) mc.currentScreen).getScreenHandler().syncId;
						mc.interactionManager.clickSlot(syncId, slot, targetHotbar, SlotActionType.SWAP, mc.player);
						lastSwappedSlot = slot;
					}
				}
			}

			state = State.COMPLETED;
			if (autoOpen.getValue() && screen == openedScreen) {
				targetStayOpenMs = stayOpenDelay();
				closeTimer.reset();
				state = State.AWAIT_CLOSE;
				return; // Close on a later update, not in the click callback.
			}
		}

		if (state == State.AWAIT_CLOSE) {
			if (closeTimer.hasReached(targetStayOpenMs)) {
				if (screen == openedScreen && autoOpen.getValue()) {
					mc.player.closeHandledScreen();
				}
				resetState();
			}
		}
	}

	private void stopSprintBeforeClick() {
		mc.player.setSprinting(false);
		// Let the player tick send the sprint stop before frame-driven GUI clicks.
		earliestClickTick = mc.player.age + 1;
	}

	private void switchBeforeOpen() {
		if (mc.player == null || mc.interactionManager == null || mc.currentScreen != null) return;
		if (!AntibanbyDaduncs.canSwapSlotNow() || AntibanbyDaduncs.isSlotPending()) return;
		int targetSlot = totemSlot.getValueInt() - 1;
		if (mc.player.getInventory().getSelectedSlot() != targetSlot) {
			InventoryUtils.setInvSlot(targetSlot);
		}
		if (mc.player.isSprinting()) {
			stopSprintBeforeClick();
		}
	}

	private int getBestTotemSlot(int excludeSlot, int cursorCol, int cursorRow) {
		if (mc.player == null) return -1;
		PlayerInventory inv = mc.player.getInventory();

		if (mode.isMode(Mode.Blatant)) {
			for (int i = 9; i < 36; i++) {
				if (i == excludeSlot) continue;
				if (inv.getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
					return i;
				}
			}
			return -1;
		}

		List<Integer> availableSlots = new ArrayList<>();
		for (int i = 9; i < 36; i++) {
			if (i == excludeSlot) continue;
			if (inv.getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
				availableSlots.add(i);
			}
		}
		if (availableSlots.isEmpty()) return -1;
		if (availableSlots.size() == 1) return availableSlots.get(0);

		int totalWeight = 0;
		int[] weights = new int[availableSlots.size()];
		for (int i = 0; i < availableSlots.size(); i++) {
			int slot = availableSlots.get(i);
			double dist = getDistanceToCursor(slot, cursorCol, cursorRow);
			int weight = Math.max(2, (int) Math.round(140.0 / (10.0 + dist)));
			weights[i] = weight;
			totalWeight += weight;
		}

		int pick = random.nextInt(totalWeight);
		int cumulative = 0;
		for (int i = 0; i < availableSlots.size(); i++) {
			cumulative += weights[i];
			if (pick < cumulative) {
				return availableSlots.get(i);
			}
		}

		return availableSlots.get(random.nextInt(availableSlots.size()));
	}

	private int findTotemSlotFromSlot(int fromSlot) {
		if (fromSlot < 9 || fromSlot >= 36) {
			return getBestTotemSlot(-1, 4, 0);
		}
		int col = (fromSlot - 9) % 9;
		int row = (fromSlot - 9) / 9;
		return getBestTotemSlot(fromSlot, col, row);
	}

	private double getDistanceToCursor(int slot, int cursorCol, int cursorRow) {
		if (slot < 9 || slot >= 36) return 30.0;
		int slotCol = (slot - 9) % 9;
		int slotRow = (slot - 9) / 9;
		double deltaX = (slotCol - cursorCol) * 18.0;
		double deltaY;
		if (cursorRow == 0 && cursorCol == 4) {
			deltaY = (slotRow * 18.0) + 9.0;
		} else {
			deltaY = (slotRow - cursorRow) * 18.0;
		}
		return Math.sqrt(deltaX * deltaX + deltaY * deltaY);
	}

	private double slotDist(int slot1, int slot2) {
		if (slot1 < 9 || slot1 >= 36 || slot2 < 9 || slot2 >= 36) return 18.0;
		int c1 = (slot1 - 9) % 9;
		int r1 = (slot1 - 9) / 9;
		int c2 = (slot2 - 9) % 9;
		int r2 = (slot2 - 9) / 9;
		double dx = (c2 - c1) * 18.0;
		double dy = (r2 - r1) * 18.0;
		return Math.sqrt(dx * dx + dy * dy);
	}

	private long reactionDelay() {
		return reactionDelay(-1);
	}

	private long reactionDelay(int targetSlot) {
		if (delay.getValueLong() == 0) return 0L;
		long userDelayMs = delay.getValueLong() * 50L;
		long baseline = userDelayMs > 0 ? userDelayMs : 35L;

		double gaussian = random.nextGaussian() * 11.0;
		double exponential = -8.0 * Math.log(1.0 - Math.min(0.98, random.nextDouble()));
		long jitter = Math.round(gaussian + exponential - 5.0);
		jitter = Math.max(-15L, Math.min(45L, jitter));

		if (ClickGUI.randomisedTiming.getValue()) {
			double maxVariance = ClickGUI.VARIANCE;
			jitter += (long) ((random.nextDouble() * 2.0 - 1.0) * (maxVariance * 0.35));
		}

		double distance = targetSlot != -1 ? getDistanceToCursor(targetSlot, 4, 0) : 10.0;
		long kineticDelay = Math.round(distance * 0.28);

		return Math.max(20L, baseline + jitter + kineticDelay);
	}

	private long clickDelay(int fromSlot, int toSlot) {
		if (delay.getValueLong() == 0) return 0L;
		long userDelayMs = delay.getValueLong() * 50L;
		long baseline = userDelayMs > 0 ? Math.max(30L, (long) (userDelayMs * 0.75)) : 35L;

		double gaussian = random.nextGaussian() * 9.0;
		double exponential = -6.0 * Math.log(1.0 - Math.min(0.98, random.nextDouble()));
		long jitter = Math.round(gaussian + exponential - 4.0);
		jitter = Math.max(-12L, Math.min(35L, jitter));

		if (ClickGUI.randomisedTiming.getValue()) {
			double maxVariance = ClickGUI.VARIANCE;
			jitter += (long) ((random.nextDouble() * 2.0 - 1.0) * (maxVariance * 0.3));
		}

		double transitionDist = (fromSlot != -1 && toSlot != -1) ? slotDist(fromSlot, toSlot) : 18.0;
		long kineticDelay = Math.round(transitionDist * 0.22);

		return Math.max(20L, baseline + jitter + kineticDelay);
	}

	private long stayOpenDelay() {
		if (stayOpenFor.getValueLong() == 0) return 0L;
		long userStayMs = stayOpenFor.getValueLong() * 50L;
		long baseline = userStayMs > 0 ? userStayMs : 25L;
		long jitter = Math.round(Math.abs(random.nextGaussian() * 15.0));
		return Math.max(15L, baseline + jitter);
	}

	public boolean shouldOpenScreen() {
		if (!InventoryUtils.canOpenPlayerInventory() || AntibanbyDaduncs.isInventoryOpenReserved()
				|| !AntibanbyDaduncs.canInventoryActionNow()) return false;
		int targetHotbar = totemSlot.getValueInt() - 1;
		boolean offhandMissing = mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING;
		ItemStack stack = mc.player.getInventory().getStack(targetHotbar);
		boolean hotbarMissing = hotbar.getValue() && (stack.isEmpty() || (forceTotem.getValue() && !stack.isOf(Items.TOTEM_OF_UNDYING)));

		return (offhandMissing || hotbarMissing) &&
				InventoryUtils.countItemExceptHotbar(item -> item == Items.TOTEM_OF_UNDYING) > 0;
	}

	public boolean shouldCloseScreen() {
		if (mc.player == null) return false;
		if (hotbar.getValue()) {
			return mc.player.getInventory().getStack(totemSlot.getValueInt() - 1).getItem() == Items.TOTEM_OF_UNDYING &&
					mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING;
		} else {
			return mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING;
		}
	}

	private void resetState() {
		activeScreen = null;
		openedScreen = null;
		earliestClickTick = -1;
		state = State.IDLE;
		lastSwappedSlot = -1;
		actionTimer.reset();
		closeTimer.reset();
	}
}
