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
import dlindustries.uk.daduncs.event.events.ButtonListener;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bind;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.InventoryUtils;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import dlindustries.uk.daduncs.utils.KeyUtils;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

import java.util.function.Predicate;

public final class UtilitySwapper extends Module implements TickListener, ButtonListener {

    public static boolean isSwapping = false;

    private final number slotSwitchDelay = new number(EncryptedString.of("Slot Switch Delay"), 0, 20, 0, 1)
            .setDescription(EncryptedString.of("Delay in ticks before switching hotbar slots"));

    private final number itemPickupDelay = new number(EncryptedString.of("Item Pickup Delay"), 0, 20, 0, 1)
            .setDescription(EncryptedString.of("Delay in ticks before moving items in the inventory"));

    private final number utilitySlot = new number(EncryptedString.of("Utility Slot"), 1, 9, 8, 1)
            .setDescription(EncryptedString.of("The hotbar slot (1-9) where items will be swapped to"));

    private final bind shieldActivation = new bind(EncryptedString.of("Shield Activation"), GLFW.GLFW_KEY_UNKNOWN, false)
            .setDescription(EncryptedString.of("Hold to equip a shield and hold Use. Blocks and main-hand items keep their normal priority."))
            .setTag(EncryptedString.of("READ DESC"))
            .setShowDescription(true);


    private final bind axeSwap = new bind(EncryptedString.of("Axe Swap"), GLFW.GLFW_KEY_UNKNOWN, false)
            .setDescription(EncryptedString.of("Press/Hold to ensure an axe is in the utility slot"));

    private int switchTimer = 0;
    private int pickupTimer = 0;
    private boolean pendingClose = false;
    private enum Utility { SHIELD, AXE }
    private Utility requested;
    private ClientPlayerEntity requestPlayer;
    private InventoryScreen swapScreen;
    private boolean screenOpened;
    private boolean shieldEquippedThisHold;

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
        eventManager.add(ButtonListener.class, this);
        switchTimer = 0;
        pickupTimer = 0;
        shieldEquippedThisHold = false;
        resetRequest();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        eventManager.remove(ButtonListener.class, this);
        closeOwnScreen();
        resetRequest();
        super.onDisable();
    }

    @Override
    public void onButtonPress(ButtonEvent event) {
        if (event.button == shieldActivation.getKey() && event.action == GLFW.GLFW_RELEASE
                && event.window == mc.getWindow().getHandle()) shieldEquippedThisHold = false;
        // Axe accepts taps between ticks. Shield progression requires a physical hold.
        if (event.action != GLFW.GLFW_PRESS || mc.currentScreen != null
                || mc.player == null || !mc.isWindowFocused()
                || event.window != mc.getWindow().getHandle()) return;
        if (event.button == axeSwap.getKey() && event.button != shieldActivation.getKey()) request(Utility.AXE);
    }

    private void request(Utility utility) {
        if (requested != null) return;
        requested = utility;
        requestPlayer = mc.player;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.player.isSpectator() || !mc.player.isAlive() || mc.getOverlay() != null || !mc.isWindowFocused()) {
            shieldEquippedThisHold = false;
            closeOwnScreen();
            resetRequest();
            return;
        }
        if (requestPlayer != null && requestPlayer != mc.player) {
            shieldEquippedThisHold = false;
            resetRequest();
            return;
        }

        if (switchTimer > 0) switchTimer--;
        boolean shieldPressed = isKeyPressed(shieldActivation);
        boolean axePressed = isKeyPressed(axeSwap);
        if (!shieldPressed) {
            shieldEquippedThisHold = false;
            if (requested == Utility.SHIELD) {
                closeOwnScreen();
                resetRequest();
            }
        }

        if (swapScreen != null) {
            if (mc.currentScreen != swapScreen) {
                if (!screenOpened && InventoryUtils.canOpenPlayerInventory() && !AntibanbyDaduncs.isInventoryOpenReserved()) {
                    if (!AntibanbyDaduncs.canInventoryActionNow()) return;
                    // Retry only while the original screen request is still valid.
                    mc.setScreen(swapScreen);
                    screenOpened = mc.currentScreen == swapScreen;
                    return;
                }
                resetRequest();
                return;
            }
            screenOpened = true;
            if (pendingClose) {
                closeOwnScreen();
                resetRequest();
                return;
            }
            if (pickupTimer > 0) pickupTimer--;
            invSwap(itemFinder());
            return;
        }

        if (mc.currentScreen != null) {
            resetRequest();
            return;
        }

        if (requested == null) {
            if (shieldPressed && !shieldEquippedThisHold) request(Utility.SHIELD);
            else if (axePressed) request(Utility.AXE);
        }

        if (requested == Utility.SHIELD) {
            handleShield();
        } else if (requested == Utility.AXE) {
            handleAxe();
        }
    }

    private void handleShield() {
        if (equippedShieldHand() != null) {
            shieldEquippedThisHold = true;
            resetRequest();
            return;
        }

        int shieldInHotbar = findSlotInHotbar(item -> item.getItem() == Items.SHIELD);

        if (shieldInHotbar == -1) {
            invSwap(item -> item.getItem() == Items.SHIELD);
        } else if (switchTimer <= 0 && !mc.player.isUsingItem() && AntibanbyDaduncs.canSwapSlotNow()
                && !AntibanbyDaduncs.isSlotPending() && InventoryUtils.setInvSlot(shieldInHotbar)) {
            shieldEquippedThisHold = true;
            switchTimer = slotSwitchDelay.getValueInt();
            resetRequest();
        }
    }

    private void handleAxe() {
        int axeInHotbar = findSlotInHotbar(item -> item.getItem() instanceof AxeItem);

        if (axeInHotbar != -1) {
            resetRequest();
            return;
        }

        invSwap(item -> item.getItem() instanceof AxeItem);
    }

    private void invSwap(Predicate<ItemStack> itemFinder) {
        if (mc.player == null || mc.interactionManager == null) return;

        if (swapScreen == null) {
            if (!AntibanbyDaduncs.canInventoryActionNow()) return;
            if (!InventoryUtils.canOpenPlayerInventory() || findInventorySlot(itemFinder) == -1 || AntibanbyDaduncs.isInventoryOpenReserved()) {
                resetRequest();
                return;
            }
            if (mc.player.isUsingItem()) {
                mc.interactionManager.stopUsingItem(mc.player);
                return;
            }
            isSwapping = true;
            swapScreen = new InventoryScreen(mc.player);
            // Vanilla opens the screen first. This releases movement keys so sprint
            // cannot restart each tick and prevent the inventory from ever opening.
            mc.setScreen(swapScreen);
            screenOpened = mc.currentScreen == swapScreen;
            pickupTimer = Math.max(1, itemPickupDelay.getValueInt());
            return;
        }

        if (mc.currentScreen != swapScreen
                || mc.player.currentScreenHandler != swapScreen.getScreenHandler()) return;

        if (mc.player.isUsingItem()) {
            mc.interactionManager.stopUsingItem(mc.player);
            return;
        }

        if (mc.player.isSprinting()) {
            mc.player.setSprinting(false);
            return;
        }

        if (mc.player.isSneaking() || hasMovementInput()) {
            return;
        }

        if (!AntibanbyDaduncs.canInventoryActionNow() || !mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            return;
        }

        if (pickupTimer > 0) return;

        int sourceSlot = findInventorySlot(itemFinder);

        if (sourceSlot != -1) {
            int targetHotbarIndex = utilitySlot.getValueInt() - 1;

            mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    sourceSlot,
                    targetHotbarIndex,
                    SlotActionType.SWAP,
                    mc.player
            );

            // clickSlot can be cancelled before prediction. Do not finish a rejected swap.
            if (itemFinder.test(mc.player.getInventory().getStack(targetHotbarIndex))) {
                pendingClose = true;
                switchTimer = slotSwitchDelay.getValueInt();
            }
        } else {
            closeOwnScreen();
            resetRequest();
        }
    }

    private Predicate<ItemStack> itemFinder() {
        return requested == Utility.SHIELD ? item -> item.getItem() == Items.SHIELD
                : item -> item.getItem() instanceof AxeItem;
    }

    private int findInventorySlot(Predicate<ItemStack> predicate) {
        for (int i = 9; i < 36; i++) {
            if (predicate.test(mc.player.getInventory().getStack(i))) return i;
        }
        return -1;
    }

    private boolean hasMovementInput() {
        var input = mc.player.input.playerInput;
        return input.forward() || input.backward() || input.left() || input.right()
                || input.jump() || input.sneak() || input.sprint();
    }

    private Hand equippedShieldHand() {
        if (mc.player.getMainHandStack().getItem() == Items.SHIELD) return Hand.MAIN_HAND;
        if (mc.player.getOffHandStack().getItem() == Items.SHIELD) return Hand.OFF_HAND;
        return null;
    }

    private boolean isShieldActivationHeld() {
        return isEnabled() && mc.player != null && mc.player.isAlive() && !mc.player.isSpectator()
                && mc.currentScreen == null && mc.getOverlay() == null && mc.isWindowFocused()
                && isKeyPressed(shieldActivation);
    }

    public boolean holdsShieldUse() {
        return isShieldActivationHeld() && equippedShieldHand() != null;
    }

    private void closeOwnScreen() {
        if (swapScreen != null && mc.currentScreen == swapScreen
                && mc.player != null && mc.player == requestPlayer) {
            mc.player.closeHandledScreen();
        }
    }

    private void resetRequest() {
        requested = null;
        requestPlayer = null;
        swapScreen = null;
        screenOpened = false;
        pendingClose = false;
        pickupTimer = 0;
        isSwapping = false;
    }

    private int findSlotInHotbar(Predicate<ItemStack> predicate) {
        for (int i = 0; i < 9; i++) {
            if (predicate.test(mc.player.getInventory().getStack(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isKeyPressed(bind setting) {
        return KeyUtils.isKeyPressed(setting.getKey());
    }
}
