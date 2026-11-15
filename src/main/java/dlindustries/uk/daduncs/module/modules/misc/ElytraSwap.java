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

package dlindustries.uk.daduncs.module.modules.misc;

import dlindustries.uk.daduncs.event.events.AttackListener;
import dlindustries.uk.daduncs.event.events.ItemUseListener;
import dlindustries.uk.daduncs.event.events.PacketSendListener;
import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.setting.bind;
import dlindustries.uk.daduncs.module.setting.mode;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.InventoryUtils;
import dlindustries.uk.daduncs.utils.KeyUtils;
import dlindustries.uk.daduncs.utils.TimerUtils;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

public final class ElytraSwap extends Module implements TickListener, AttackListener, ItemUseListener, PacketSendListener {

    public enum TriggerMode {
        ATTACK("Attack"),
        KEYPRESS("Keypress");

        private final String name;

        TriggerMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum SwapMode {
        NONE("None"),
        CONTEXT("Context(CPVP)"),
        SET_SLOT("Set Slot");

        private final String name;

        SwapMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final mode<TriggerMode> triggerMode = new mode<>(
            EncryptedString.of("Trigger Mode"),
            TriggerMode.ATTACK,
            TriggerMode.class
    ).setDescription(EncryptedString.of("Trigger Elytra Swap on attack or via activate key"));

    private final bind activateKey = new bind(
            EncryptedString.of("Activate Key"),
            -1,
            false
    ).setDescription(EncryptedString.of("Key to trigger Elytra Swap in Keypress mode"));

    private final mode<SwapMode> swapMode = new mode<>(
            EncryptedString.of("Slot Swap After"),
            SwapMode.NONE,
            SwapMode.class
    ).setDescription(EncryptedString.of("What slot to swap to after chestplate is reequipped"));

    private final number setSlot = new number(
            EncryptedString.of("Set Slot"),
            1, 9, 1, 1
    ).setDescription(EncryptedString.of("The exact hotbar slot (1-9) to swap to if Slot Swap After is Set Slot"));

    private final number switchDelay = new number(EncryptedString.of("Switch Delay"), 0, 20, 1, 1);

    private enum EquipState {
        IDLE,
        EQUIPPING,
        USE_CHESTPLATE,
        RESTORING
    }

    private EquipState equipState = EquipState.IDLE;
    private final TimerUtils delayTimer = new TimerUtils();
    private long currentStepDelayMs = 0;
    private int targetRestoreSlot = -1;
    private boolean isElytraEquipped = false;
    private long groundTime = 0;
    private int previousSlot = -1;
    private boolean wasSpearOnAttack = false;
    private final TimerUtils swapDebounceTimer = new TimerUtils();
    private boolean keyWasDown = false;
    private boolean actionSentThisTick = false;

    public ElytraSwap() {
        super(
                EncryptedString.of("Elytra Swap"),
                EncryptedString.of("Auto chestplate swap when left clicking while flying with Elytra"),
                -1,
                Category.MISC
        );
        addSettings(triggerMode, activateKey, swapMode, setSlot, switchDelay);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        eventManager.add(AttackListener.class, this);
        eventManager.add(ItemUseListener.class, this);
        eventManager.add(PacketSendListener.class, this);
        resetState();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        eventManager.remove(AttackListener.class, this);
        eventManager.remove(ItemUseListener.class, this);
        eventManager.remove(PacketSendListener.class, this);
        resetState();
        super.onDisable();
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (mc.player == null) return;
        Packet<?> packet = event.packet;

        if (packet instanceof ClientTickEndC2SPacket) {
            actionSentThisTick = false;
        } else if (isActionPacket(packet)) {
            actionSentThisTick = true;
        }
    }

    private boolean isActionPacket(Packet<?> packet) {
        return packet instanceof PlayerInteractEntityC2SPacket ||
                packet instanceof PlayerInteractBlockC2SPacket ||
                packet instanceof PlayerInteractItemC2SPacket ||
                packet instanceof PlayerActionC2SPacket;
    }

    private long randomDelay() {
        long baseMs = switchDelay.getValueLong() * 50L;
        if (ClickGUI.randomisedTiming.getValue()) {
            long variance = (long) ClickGUI.VARIANCE;
            long offset = (long) (Math.random() * variance);
            baseMs += offset;
        }
        return baseMs;
    }

    @Override
    public void onTick() {
        if (mc.currentScreen != null || mc.player == null || mc.world == null) return;

        ItemStack chestItem = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        isElytraEquipped = chestItem.isOf(Items.ELYTRA);

        if (equipState != EquipState.IDLE) {
            if (delayTimer.hasReached(currentStepDelayMs)) {
                processEquipState();
            }
            return;
        }

        if (mc.player.isOnGround() && !mc.player.isGliding() && isElytraEquipped) {
            if (groundTime == 0) {
                groundTime = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - groundTime >= 1000) {
                if (findChestplateSlot() != -1) {
                    startEquipProcess(false);
                }
                groundTime = 0;
            }
        } else {
            groundTime = 0;
        }

        if (triggerMode.isMode(TriggerMode.KEYPRESS)) {
            boolean keyDown = KeyUtils.isKeyPressed(activateKey.getKey());
            if (keyDown && !keyWasDown) {
                if (isElytraEquipped && (mc.player.isGliding() || !mc.player.isOnGround())) {
                    startEquipProcess(false);
                }
            }
            keyWasDown = keyDown;
        }
    }

    @Override
    public void onItemUse(ItemUseEvent event) {
        if (equipState != EquipState.IDLE) {
            event.cancel();
            return;
        }


        if (mc.player != null) {
            ItemStack main = mc.player.getMainHandStack();
            if (isChestplate(main)) {
                if (!swapDebounceTimer.hasReached(150)) {
                    event.cancel();
                }
            } else if (main.isOf(Items.ELYTRA)) {
                swapDebounceTimer.reset();
            }
        }
    }

    public boolean isSwapping() {
        return equipState != EquipState.IDLE;
    }

    @Override
    public void onAttack(AttackEvent event) {
        if (!triggerMode.isMode(TriggerMode.ATTACK)) return;
        if (mc.currentScreen != null || mc.player == null) return;
        if (equipState != EquipState.IDLE) return;
        if (!swapDebounceTimer.hasReached(150)) return;
        if (findChestplateSlot() == -1) return;

        if (isElytraEquipped && (mc.player.isGliding() || !mc.player.isOnGround())) {
            startEquipProcess(true);
        }
    }

    private void startEquipProcess(boolean fromAttack) {
        if (equipState == EquipState.IDLE) {
            if (!swapDebounceTimer.hasReached(150)) return;
            if (isChestplateEquipped() || !isElytraEquipped) {
                resetState();
                return;
            }

            int chestSlot = findChestplateSlot();
            if (chestSlot == -1) {
                resetState();
                return;
            }

            previousSlot = mc.player.getInventory().getSelectedSlot();
            wasSpearOnAttack = isSpear(mc.player.getMainHandStack());
            equipState = EquipState.EQUIPPING;
            currentStepDelayMs = Math.max(50L, randomDelay());
            delayTimer.reset();
        }
    }

    private void processEquipState() {
        if (mc.currentScreen != null || mc.player == null) {
            resetState();
            return;
        }

        PlayerEntity player = mc.player;

        if (equipState == EquipState.EQUIPPING) {
            if (actionSentThisTick || player.isUsingItem() || !AntibanbyDaduncs.canSwapSlotNow() || AntibanbyDaduncs.isSlotPending()) {
                return;
            }

            int chestSlot = findChestplateSlot();
            if (chestSlot == -1) {
                resetState();
                return;
            }

            InventoryUtils.setInvSlot(chestSlot);
            equipState = EquipState.USE_CHESTPLATE;
            currentStepDelayMs = Math.max(50L, randomDelay());
            delayTimer.reset();
        } else if (equipState == EquipState.USE_CHESTPLATE) {
            if (actionSentThisTick || player.isUsingItem() || !AntibanbyDaduncs.canUseItemNow(Hand.MAIN_HAND) || AntibanbyDaduncs.isSlotPending()) {
                return;
            }

            if (isChestplate(player.getMainHandStack())) {
                if (mc.interactionManager.interactItem(player, Hand.MAIN_HAND).isAccepted()) {
                    player.swingHand(Hand.MAIN_HAND);
                }
            }

            int restoreSlot = getRestoreSlot();
            if (restoreSlot != -1) {
                targetRestoreSlot = restoreSlot;
                equipState = EquipState.RESTORING;
                currentStepDelayMs = 50L;
                delayTimer.reset();
                return;
            }

            finishEquip();
        } else if (equipState == EquipState.RESTORING) {
            if (actionSentThisTick || player.isUsingItem() || !AntibanbyDaduncs.canSwapSlotNow() || AntibanbyDaduncs.isSlotPending()) {
                return;
            }

            if (targetRestoreSlot != -1) {
                InventoryUtils.setInvSlot(targetRestoreSlot);
            }

            finishEquip();
        } else {
            resetState();
        }
    }

    private void finishEquip() {
        long window = mc.getWindow().getHandle();
        boolean physicallyHoldingRightClick = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (!physicallyHoldingRightClick) {
            mc.options.useKey.setPressed(false);
        }
        resetState();
        swapDebounceTimer.reset();
    }

    private int getRestoreSlot() {
        if (mc.player == null) return -1;

        if (swapMode.isMode(SwapMode.NONE)) return -1;
        if (swapMode.isMode(SwapMode.SET_SLOT)) return setSlot.getValueInt() - 1;

        int maceSlot = InventoryUtils.getMaceSlot();
        int axeSlot = InventoryUtils.getAxeSlot();
        int spearSlot = getSpearSlot();
        int swordSlot = InventoryUtils.getSwordSlot();

        boolean hasMace = (maceSlot != -1);
        boolean hasAxe = (axeSlot != -1);
        boolean hasSpear = (spearSlot != -1);
        boolean hasSword = (swordSlot != -1);

        PlayerEntity closest = null;
        double minDsq = Double.MAX_VALUE;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            double dsq = mc.player.squaredDistanceTo(p);
            if (dsq < minDsq) {
                minDsq = dsq;
                closest = p;
            }
        }
        boolean targetHasShield = false;
        if (closest != null) {
            targetHasShield = closest.getOffHandStack().isOf(Items.SHIELD) || closest.getMainHandStack().isOf(Items.SHIELD);
        }

        if (hasAxe && hasMace && hasSpear && !hasSword) {
            return targetHasShield ? axeSlot : spearSlot;
        }

        if (hasAxe && hasMace && !hasSpear) {
            return axeSlot;
        }

        if (!hasMace) {
            if (hasSword && hasSpear) {
                return spearSlot;
            }
            if (!hasSpear && hasSword) {
                return swordSlot;
            }
        }

        if (hasSpear && (wasSpearOnAttack || (previousSlot >= 0 && previousSlot < 9 && isSpear(mc.player.getInventory().getStack(previousSlot))))) {
            return (previousSlot >= 0 && previousSlot < 9 && isSpear(mc.player.getInventory().getStack(previousSlot))) ? previousSlot : spearSlot;
        }

        if (hasMace && hasAxe && hasSpear) {
            return axeSlot;
        }

        if (hasMace) {
            if (hasAxe) {
                return axeSlot;
            }
            return maceSlot;
        }

        if (hasSpear) {
            return spearSlot;
        }

        if (hasSword) {
            return swordSlot;
        }

        return -1;
    }

    private boolean isChestplateEquipped() {
        if (mc.player == null) return false;
        ItemStack stack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        return isChestplate(stack);
    }

    private int findChestplateSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isChestplate(stack)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isChestplate(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem().getTranslationKey().toLowerCase().contains("chestplate");
    }

    private boolean isSpear(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem().getTranslationKey().toLowerCase().contains("spear") || stack.isOf(Items.TRIDENT);
    }

    private int getSpearSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isSpear(stack)) {
                return i;
            }
        }
        return -1;
    }

    private void resetState() {
        equipState = EquipState.IDLE;
        previousSlot = -1;
        targetRestoreSlot = -1;
        wasSpearOnAttack = false;
        currentStepDelayMs = 0;
        delayTimer.reset();
        keyWasDown = false;
        actionSentThisTick = false;
    }
}