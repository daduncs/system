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

import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.setting.bind;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.InventoryUtils;
import dlindustries.uk.daduncs.utils.KeyUtils;
import dlindustries.uk.daduncs.utils.TimerUtils;

import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public final class FireworkMacro extends Module implements TickListener {

    private final bind activateKey = new bind(EncryptedString.of("Activate Key"), 32, false);
    private final number switchDelay = new number(EncryptedString.of("Switch Delay"), 0, 20, 1, 1);
    private final number fireworkSlot = new number(EncryptedString.of("Firework Slot"), 1, 9, 3, 1)
            .setDescription(EncryptedString.of("Slot 1-9 for fireworks"));
    private final bool switchBack = new bool(
            EncryptedString.of("Switch Back"),
            true
    ).setDescription(EncryptedString.of("Switch back to original hotbar slot after using firework"));

    private enum SequenceState {
        IDLE,
        SWITCHING,
        USING,
        SWITCHING_BACK
    }

    private SequenceState state = SequenceState.IDLE;
    private final TimerUtils delayTimer = new TimerUtils();
    private long currentStepDelayMs = 0;
    private boolean wasKeyPressed;
    private int originalSlot = -1;

    public FireworkMacro() {
        super(
                EncryptedString.of("Firework Macro"),
                EncryptedString.of("Press a key while flying to use fireworks with randomized timing"),
                -1,
                Category.MISC
        );
        addSettings(activateKey, switchDelay, fireworkSlot, switchBack);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        reset();
        super.onDisable();
    }

    private long randomDelay() {
        long baseMs = switchDelay.getValueLong() * 50L;
        if (ClickGUI.randomisedTiming.getValue()) {
            long variance = (long) ClickGUI.VARIANCE;
            long offset = (long) (Math.random() * variance);
            baseMs += offset;
        } else {
            long randomOffset = (long) (Math.random() * 50);
            baseMs += randomOffset;
        }
        return baseMs;
    }

    @Override
    public void onTick() {
        if (mc.currentScreen != null || mc.player == null) return;

        handleKeyDetection();

        if (state != SequenceState.IDLE && !mc.player.isGliding()) {
            reset();
            return;
        }

        if (state != SequenceState.IDLE && delayTimer.hasReached(currentStepDelayMs)) {
            processSequence();
        }
    }

    private void handleKeyDetection() {
        boolean keyPressed = KeyUtils.isKeyPressed(activateKey.getKey());

        if (keyPressed && !wasKeyPressed) {
            if (mc.player != null && mc.player.isGliding() && state == SequenceState.IDLE) {
                startSequence();
            }
        }
        wasKeyPressed = keyPressed;
    }

    private void startSequence() {
        if (mc.player != null) {
            originalSlot = mc.player.getInventory().getSelectedSlot();
        }
        state = SequenceState.SWITCHING;
        currentStepDelayMs = randomDelay();
        delayTimer.reset();
    }

    private void processSequence() {
        if (mc.player == null) {
            reset();
            return;
        }

        switch (state) {
            case SWITCHING:
                if (mc.player.isUsingItem() || !AntibanbyDaduncs.canSwapSlotNow() || AntibanbyDaduncs.isSlotPending()) {
                    return;
                }
                InventoryUtils.setInvSlot(fireworkSlot.getValueInt() - 1);
                state = SequenceState.USING;
                currentStepDelayMs = Math.max(50L, randomDelay());
                delayTimer.reset();
                break;

            case USING:
                if (mc.player.isUsingItem() || !AntibanbyDaduncs.canUseItemNow(Hand.MAIN_HAND) || AntibanbyDaduncs.isSlotPending()) {
                    return;
                }
                if (mc.player.getMainHandStack().isOf(Items.FIREWORK_ROCKET)) {
                    if (mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND).isAccepted()) {
                        mc.player.swingHand(Hand.MAIN_HAND);
                    }
                }
                if (switchBack.getValue()) {
                    state = SequenceState.SWITCHING_BACK;
                    currentStepDelayMs = Math.max(50L, randomDelay());
                    delayTimer.reset();
                } else {
                    reset();
                }
                break;

            case SWITCHING_BACK:
                if (mc.player.isUsingItem() || !AntibanbyDaduncs.canSwapSlotNow() || AntibanbyDaduncs.isSlotPending()) {
                    return;
                }
                if (switchBack.getValue() && originalSlot != -1) {
                    InventoryUtils.setInvSlot(originalSlot);
                }
                reset();
                break;

            case IDLE:
                break;
        }
    }

    private void reset() {
        state = SequenceState.IDLE;
        originalSlot = -1;
        currentStepDelayMs = 0;
        delayTimer.reset();
    }
}