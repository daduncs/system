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
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.mixin.HandledScreenMixin;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.InventoryUtils;
import dlindustries.uk.daduncs.utils.TimerUtils;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Random;

public final class HoverTotem extends Module implements TickListener {
	private final number delay = new number(EncryptedString.of("Delay"), 0, 20, 0, 1);
	private final bool hotbar = new bool(EncryptedString.of("Hotbar"), true)
			.setDescription(EncryptedString.of("Puts a totem in your hotbar as well, if enabled (Setting below will work if this is enabled)"));
	private final number slot = new number(EncryptedString.of("Totem Slot"), 1, 9, 1, 1)
			.setDescription(EncryptedString.of("Your preferred totem slot"));
	private final bool autoSwitch = new bool(EncryptedString.of("Auto Switch"), false)
			.setDescription(EncryptedString.of("Switches to totem slot when going inside the inventory"));

	private final TimerUtils actionTimer = new TimerUtils();
	private final Random random = new Random();

	private int lastHoveredSlot = -1;
	private int lastSwappedSlot = -1;
	private long targetHoverDelayMs = 0L;

	public HoverTotem() {
		super(EncryptedString.of("Hover Totem"),
				EncryptedString.of("Equips a totem in your totem and offhand slots if a totem is hovered"),
				-1,
				Category.CRYSTAL);
		addSettings(delay, hotbar, slot, autoSwitch);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		resetState();
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
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

		processHoverLogic();
	}

	private void processHoverLogic() {
		if (mc.player == null || mc.world == null || mc.interactionManager == null) {
			resetState();
			return;
		}

		if (!(mc.currentScreen instanceof InventoryScreen inv) || mc.player.currentScreenHandler != inv.getScreenHandler()) {
			if (lastHoveredSlot != -1 || targetHoverDelayMs != 0L) {
				resetState();
			}
			return;
		}

		if (UtilitySwapper.isSwapping) return;

		if (mc.player.isSprinting()) {
			mc.player.setSprinting(false);
			return;
		}

		if (mc.player.isSneaking() || mc.player.isUsingItem() || hasActiveMovementInput()) {
			return;
		}

		Slot hoveredSlot = ((HandledScreenMixin) inv).getFocusedSlot();
		if (hoveredSlot == null) {
			lastHoveredSlot = -1;
			return;
		}

		int slotId = hoveredSlot.id;
		if (slotId < 9 || slotId > 35) {
			lastHoveredSlot = -1;
			return;
		}

		ItemStack stack = hoveredSlot.getStack();
		if (!stack.isOf(Items.TOTEM_OF_UNDYING)) {
			lastHoveredSlot = -1;
			return;
		}

		if (slotId != lastHoveredSlot) {
			lastHoveredSlot = slotId;
			targetHoverDelayMs = hoverDelay(slotId);
			actionTimer.reset();
			return;
		}

		if (!actionTimer.hasReached(targetHoverDelayMs)) {
			return;
		}

		if (!AntibanbyDaduncs.canInventoryActionNow() || !inv.getScreenHandler().getCursorStack().isEmpty()) {
			return;
		}

		int targetHotbar = slot.getValueInt() - 1;
		boolean needsHotbar = hotbar.getValue() && mc.player.getInventory().getStack(targetHotbar).getItem() != Items.TOTEM_OF_UNDYING;
		boolean needsOffhand = !mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);

		if (needsHotbar) {
			mc.interactionManager.clickSlot(inv.getScreenHandler().syncId, slotId, targetHotbar, SlotActionType.SWAP, mc.player);
			lastSwappedSlot = slotId;
			targetHoverDelayMs = clickDelay();
			actionTimer.reset();
		} else if (needsOffhand) {
			mc.interactionManager.clickSlot(inv.getScreenHandler().syncId, slotId, 40, SlotActionType.SWAP, mc.player);
			lastSwappedSlot = slotId;
			targetHoverDelayMs = clickDelay();
			actionTimer.reset();
		}
	}

	private boolean hasActiveMovementInput() {
		if (mc.player == null || mc.player.input == null || mc.player.input.playerInput == null) return false;
		var pi = mc.player.input.playerInput;
		return pi.forward() || pi.backward() || pi.left() || pi.right() || pi.jump() || pi.sneak() || pi.sprint();
	}

	private void switchBeforeOpen() {
		if (mc.player == null || mc.interactionManager == null || mc.currentScreen != null) return;
		if (!AntibanbyDaduncs.canSwapSlotNow() || AntibanbyDaduncs.isSlotPending()) return;
		if (mc.player.isSprinting()) {
			mc.player.setSprinting(false);
		}
		int targetSlot = slot.getValueInt() - 1;
		if (mc.player.getInventory().getSelectedSlot() != targetSlot) {
			InventoryUtils.setInvSlot(targetSlot);
		}
	}

	private long hoverDelay(int hoveredSlotId) {
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

		double distance = getSlotDistance(hoveredSlotId);
		long kineticDelay = Math.round(distance * 0.20);

		return Math.max(20L, baseline + jitter + kineticDelay);
	}

	private long clickDelay() {
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

		return Math.max(20L, baseline + jitter);
	}

	private double getSlotDistance(int slotId) {
		if (slotId < 9 || slotId > 35) return 20.0;
		int col = (slotId - 9) % 9;
		int row = (slotId - 9) / 9;
		double dx = (col - 4) * 18.0;
		double dy = (row * 18.0) + 9.0;
		return Math.sqrt(dx * dx + dy * dy);
	}

	private void resetState() {
		lastHoveredSlot = -1;
		lastSwappedSlot = -1;
		targetHoverDelayMs = 0L;
		actionTimer.reset();
	}
}
