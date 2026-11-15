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

package dlindustries.uk.daduncs.module.modules.cart;

import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.modules.crystal.UtilitySwapper;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.mode;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.FakeInvScreen;
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

public final class InvCart extends Module implements TickListener {
    public enum Mode {
        Blatant, Random
    }

    private final mode<Mode> mode = new mode<>(EncryptedString.of("Mode"), Mode.Blatant, Mode.class)
            .setDescription(EncryptedString.of("Whether to randomize the carting pattern or no"));
    private final number delay = new number(EncryptedString.of("Delay"), 0, 20, 0, 1);
    private final bool offhand = new bool(EncryptedString.of("Offhand"), true).setDescription(EncryptedString.of("Puts a cart in your offhand"));
    private final bool hotbar = new bool(EncryptedString.of("Hotbar"), true).setDescription(EncryptedString.of("Puts a cart in your hotbar as well, if enabled (Setting below will work if this is enabled)"));
    private final number cartSlot = new number(EncryptedString.of("Cart Slot"), 1, 9, 9, 1)
            .setDescription(EncryptedString.of("Your preferred cart slot"));
    private final bool autoSwitch = new bool(EncryptedString.of("Auto Switch"), true)
            .setDescription(EncryptedString.of("Switches to cart slot when going inside the inventory"));
    private final bool forceCart = new bool(EncryptedString.of("Force Cart"), true).setDescription(EncryptedString.of("Puts the cart in the slot, regardless if its space is taken up by something else"));
    private final bool autoOpen = new bool(EncryptedString.of("Auto Open"), false)
            .setDescription(EncryptedString.of("Automatically opens and closes the inventory for you"));
    private final number stayOpenFor = new number(EncryptedString.of("Stay Open For"), 0, 20, 0, 1);

    private final TimerUtils actionTimer = new TimerUtils();
    private final TimerUtils closeTimer = new TimerUtils();
    private int lastActionTick = -1;

    public InvCart() {
        super(EncryptedString.of("InvCart"),
                EncryptedString.of("Automatically equips a cart in your offhand and main hand if empty"),
                -1,
                Category.CART);
        addSettings(mode, delay, offhand, hotbar, cartSlot, autoSwitch, forceCart, autoOpen, stayOpenFor);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        actionTimer.reset();
        closeTimer.reset();
        lastActionTick = -1;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        lastActionTick = -1;
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (UtilitySwapper.isSwapping) return;

        if (shouldOpenScreen() && autoOpen.getValue()) {
            if (mc.player.isSprinting()) {
                mc.player.setSprinting(false);
                return;
            }
            if (mc.player.isUsingItem() || mc.player.isSneaking() || hasActiveMovementInput()) {
                return;
            }
            if (autoSwitch.getValue() && AntibanbyDaduncs.canSwapSlotNow() && !AntibanbyDaduncs.isSlotPending()) {
                int targetSlot = cartSlot.getValueInt() - 1;
                if (mc.player.getInventory().getSelectedSlot() != targetSlot) {
                    InventoryUtils.setInvSlot(targetSlot);
                }
            }
            mc.setScreen(new FakeInvScreen(mc.player));
            actionTimer.reset();
            closeTimer.reset();
            lastActionTick = mc.player.age;
            return;
        }

        if (!(mc.currentScreen instanceof InventoryScreen || mc.currentScreen instanceof FakeInvScreen)) {
            return;
        }

        long delayMs = delay.getValueLong() * 50L;
        long stayOpenMs = stayOpenFor.getValueLong() * 50L;
        if (ClickGUI.randomisedTiming.getValue()) {
            long variance = (long) ClickGUI.VARIANCE;
            long delayOffset = (long) (Math.random() * variance);
            long stayOffset = (long) (Math.random() * variance);
            delayMs += delayOffset;
            stayOpenMs += stayOffset;
        }

        PlayerInventory inventory = mc.player.getInventory();

        if (mc.player.isSprinting()) {
            mc.player.setSprinting(false);
            return;
        }
        if (mc.player.isSneaking() || mc.player.isUsingItem() || hasActiveMovementInput()) {
            return;
        }

        if (actionTimer.hasReached(delayMs)) {
            if (AntibanbyDaduncs.isActionSent() || AntibanbyDaduncs.isSlotPending()) {
                return;
            }

            if (offhand.getValue() && mc.player.getOffHandStack().getItem() != Items.TNT_MINECART) {
                int slot = findCart();
                if (slot != -1) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 40, SlotActionType.SWAP, mc.player);
                    actionTimer.reset();
                    closeTimer.reset();
                    lastActionTick = mc.player.age;
                    return;
                }
            }

            if (hotbar.getValue()) {
                int targetHotbar = cartSlot.getValueInt() - 1;
                ItemStack hotbarStack = inventory.getStack(targetHotbar);
                if (hotbarStack.isEmpty() || (forceCart.getValue() && hotbarStack.getItem() != Items.TNT_MINECART)) {
                    int slot = findCart();
                    if (slot != -1) {
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, targetHotbar, SlotActionType.SWAP, mc.player);
                        actionTimer.reset();
                        closeTimer.reset();
                        lastActionTick = mc.player.age;
                        return;
                    }
                }
            }

            actionTimer.reset();
        }

        if (autoOpen.getValue() && shouldCloseScreen() && mc.player.age > lastActionTick && closeTimer.hasReached(stayOpenMs)) {
            mc.player.closeHandledScreen();
            mc.setScreen(null);
            closeTimer.reset();
        }
    }

    private boolean hasActiveMovementInput() {
        if (mc.player == null || mc.player.input == null || mc.player.input.playerInput == null) return false;
        var pi = mc.player.input.playerInput;
        return pi.forward() || pi.backward() || pi.left() || pi.right() || pi.jump() || pi.sneak();
    }

    private int findCart() {
        PlayerInventory inventory = mc.player.getInventory();
        List<Integer> slots = new ArrayList<>();

        for (int i = 9; i < 36; i++) {
            if (inventory.getStack(i).getItem() == Items.TNT_MINECART) {
                if (mode.isMode(Mode.Blatant))
                    return i;

                slots.add(i);
            }
        }

        if (slots.isEmpty())
            return -1;

        return slots.get(new Random().nextInt(slots.size()));
    }

    public boolean shouldCloseScreen() {
        return hasOffhand() && hasHotbar() && mc.currentScreen instanceof FakeInvScreen;
    }

    public boolean shouldOpenScreen() {
        return (!hasOffhand() || !hasHotbar()) &&
                !(mc.currentScreen instanceof FakeInvScreen) &&
                InventoryUtils.countItemExceptHotbar(item -> item == Items.TNT_MINECART) != 0;
    }

    private boolean hasOffhand() {
        return !offhand.getValue() || mc.player.getOffHandStack().getItem() == Items.TNT_MINECART;
    }

    private boolean hasHotbar() {
        return !hotbar.getValue() || mc.player.getInventory().getStack(cartSlot.getValueInt() - 1).getItem() == Items.TNT_MINECART;
    }
}
