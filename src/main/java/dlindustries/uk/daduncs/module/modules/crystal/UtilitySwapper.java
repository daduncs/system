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
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bind;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.InventoryUtils;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

import java.util.function.Predicate;

public final class UtilitySwapper extends Module implements TickListener {

    public static boolean isSwapping = false;

    private final number slotSwitchDelay = new number(EncryptedString.of("Slot Switch Delay"), 0, 20, 0, 1)
            .setDescription(EncryptedString.of("Delay in ticks before switching hotbar slots"));

    private final number itemPickupDelay = new number(EncryptedString.of("Item Pickup Delay"), 0, 20, 0, 1)
            .setDescription(EncryptedString.of("Delay in ticks before moving items in the inventory"));

    private final number utilitySlot = new number(EncryptedString.of("Utility Slot"), 1, 9, 8, 1)
            .setDescription(EncryptedString.of("The hotbar slot (1-9) where items will be swapped to"));

    private final bind shieldActivation = new bind(EncryptedString.of("Shield Activation"), GLFW.GLFW_KEY_UNKNOWN, false)
            .setDescription(EncryptedString.of("You can ONLY use the shield with shieldactivation bind. NOT right click hold."))
            .setTag(EncryptedString.of("READ DESC"))
            .setShowDescription(true);


    private final bind axeSwap = new bind(EncryptedString.of("Axe Swap"), GLFW.GLFW_KEY_UNKNOWN, false)
            .setDescription(EncryptedString.of("Press/Hold to ensure an axe is in the utility slot"));

    private int switchTimer = 0;
    private int pickupTimer = 0;
    private boolean pendingClose = false;

    public UtilitySwapper() {
        super(EncryptedString.of("Utility Swapper"),
                EncryptedString.of("Hotkeys shields, axes, and utilities straight into your hand"),
                -1,
                Category.CRYSTAL);
        addSettings(slotSwitchDelay, itemPickupDelay, utilitySlot, shieldActivation, axeSwap);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        switchTimer = 0;
        pickupTimer = 0;
        pendingClose = false;
        isSwapping = false;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        if (mc.options != null) {
            mc.options.useKey.setPressed(false);
        }
        pendingClose = false;
        isSwapping = false;
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (pendingClose) {
            pendingClose = false;
            if (mc.currentScreen instanceof InventoryScreen) {
                mc.player.closeHandledScreen();
                mc.setScreen(null);
            }
            isSwapping = false;
            return;
        }

        if (!isScreenSafe()) {
            isSwapping = false;
            return;
        }

        if (switchTimer > 0) switchTimer--;
        if (pickupTimer > 0) pickupTimer--;

        boolean shieldPressed = isKeyPressed(shieldActivation);
        boolean axePressed = isKeyPressed(axeSwap);

        if (shieldPressed) {
            handleShield();
        } else if (axePressed) {
            if (mc.player.isUsingItem() && mc.player.getActiveItem().getItem() instanceof ShieldItem) {
                mc.options.useKey.setPressed(false);
                mc.interactionManager.stopUsingItem(mc.player);
                return;
            }
            handleAxe();
        } else {
            if (mc.player.getMainHandStack().getItem() instanceof ShieldItem || mc.player.getOffHandStack().getItem() instanceof ShieldItem) {
                mc.options.useKey.setPressed(false);
            }
            isSwapping = false;
        }
    }

    private void handleShield() {
        if (mc.player.getOffHandStack().getItem() == Items.SHIELD) {
            if (mc.currentScreen == null) {
                mc.options.useKey.setPressed(true);
            }
            return;
        }

        int shieldInHotbar = findSlotInHotbar(item -> item.getItem() == Items.SHIELD);

        if (shieldInHotbar != -1) {
            if (mc.player.getInventory().getSelectedSlot() != shieldInHotbar) {
                if (mc.currentScreen == null && switchTimer <= 0
                        && !mc.player.isUsingItem()
                        && AntibanbyDaduncs.canSwapSlotNow()
                        && !AntibanbyDaduncs.isSlotPending()) {
                    InventoryUtils.setInvSlot(shieldInHotbar);
                    switchTimer = slotSwitchDelay.getValueInt();
                }
            } else {
                if (mc.currentScreen == null) {
                    mc.options.useKey.setPressed(true);
                }
            }
        } else {
            invSwap(item -> item.getItem() == Items.SHIELD);
        }
    }

    private void handleAxe() {
        int axeInHotbar = findSlotInHotbar(item -> item.getItem() instanceof AxeItem);

        if (axeInHotbar != -1) {
            isSwapping = false;
            return;
        }

        invSwap(item -> item.getItem() instanceof AxeItem);
    }

    private void invSwap(Predicate<ItemStack> itemFinder) {
        if (mc.player == null || mc.interactionManager == null) return;

        if (!(mc.currentScreen instanceof InventoryScreen)) {
            if (!isSwapping) {
                if (mc.player.isUsingItem()) {
                    mc.options.useKey.setPressed(false);
                    mc.interactionManager.stopUsingItem(mc.player);
                    return;
                }
                if (mc.player.isSprinting()) {
                    mc.player.setSprinting(false);
                    return;
                }
                if (mc.player.isSneaking() || AntibanbyDaduncs.isActionSent()) {
                    return;
                }
                isSwapping = true;
                mc.setScreen(new InventoryScreen(mc.player));
                pickupTimer = Math.max(1, itemPickupDelay.getValueInt());
            }
            return;
        }

        if (mc.player.isSprinting()) {
            mc.player.setSprinting(false);
            return;
        }

        if (mc.player.isSneaking() || mc.player.isUsingItem()) {
            return;
        }

        if (AntibanbyDaduncs.isActionSent() || AntibanbyDaduncs.isSlotPending()) {
            return;
        }

        if (pickupTimer > 0) return;

        int sourceSlot = -1;
        for (int i = 9; i < 36; i++) {
            if (itemFinder.test(mc.player.getInventory().getStack(i))) {
                sourceSlot = i;
                break;
            }
        }

        if (sourceSlot != -1) {
            int targetHotbarIndex = utilitySlot.getValueInt() - 1;

            mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    sourceSlot,
                    targetHotbarIndex,
                    SlotActionType.SWAP,
                    mc.player
            );

            pendingClose = true;
            switchTimer = slotSwitchDelay.getValueInt();
        } else {
            mc.player.closeHandledScreen();
            mc.setScreen(null);
            isSwapping = false;
        }
    }

    private int findSlotInHotbar(Predicate<ItemStack> predicate) {
        for (int i = 0; i < 9; i++) {
            if (predicate.test(mc.player.getInventory().getStack(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isScreenSafe() {
        if (mc.currentScreen == null) return true;
        if (mc.currentScreen instanceof InventoryScreen && isSwapping) return true;
        if (mc.currentScreen instanceof ChatScreen ||
                mc.currentScreen instanceof AnvilScreen ||
                mc.currentScreen instanceof SignEditScreen ||
                mc.currentScreen instanceof CreativeInventoryScreen) {
            return false;
        }
        return false;
    }

    private boolean isKeyPressed(bind setting) {
        int key = setting.getKey();
        if (key == -1 || key == GLFW.GLFW_KEY_UNKNOWN) return false;
        if (key < 8) {
            return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(mc.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
    }
}