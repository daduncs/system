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

package dlindustries.uk.daduncs.managers;

import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.imixin.IKeyBinding;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.utils.InventoryUtils;
import net.minecraft.client.option.KeyBinding;

import java.util.ArrayDeque;
import java.util.Deque;

import static dlindustries.uk.daduncs.System.mc;

public final class SlotManager implements TickListener {

    public static final SlotManager INSTANCE = new SlotManager();

    private static final Deque<SwapState> stack = new ArrayDeque<>();
    private static KeyBinding pressedHotbarKey;

    private SlotManager() {}

    public static boolean swapTo(Module owner, int targetSlot, boolean inputSimulation) {
        return swapTo(owner, targetSlot, inputSimulation, 0, true, null);
    }

    public static boolean swapTo(Module owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks) {
        return swapTo(owner, targetSlot, inputSimulation, restoreDelayTicks, true, null);
    }

    public static boolean swapTo(Module owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks, Runnable onEnd) {
        return swapTo(owner, targetSlot, inputSimulation, restoreDelayTicks, true, onEnd);
    }

    public static boolean swapToIfNeeded(Module owner, int targetSlot, boolean inputSimulation) {
        return swapToIfNeeded(owner, targetSlot, inputSimulation, 0, true, null);
    }

    public static boolean swapToIfNeeded(Module owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks) {
        return swapToIfNeeded(owner, targetSlot, inputSimulation, restoreDelayTicks, true, null);
    }

    public static boolean swapToIfNeeded(Module owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks, Runnable onEnd) {
        return swapToIfNeeded(owner, targetSlot, inputSimulation, restoreDelayTicks, true, onEnd);
    }

    public static boolean swapToIfNeeded(Module owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks, boolean restoreOnEnd, Runnable onEnd) {
        if (owner == null || mc.player == null) return false;
        if (targetSlot < 0 || targetSlot > 8) return false;

        int selectedSlot = getSelectedSlot();
        if (selectedSlot == targetSlot && !isActive(owner)) {
            return true;
        }

        return swapTo(owner, targetSlot, inputSimulation, restoreDelayTicks, restoreOnEnd, onEnd);
    }

    public static boolean swapTo(Module owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks, boolean restoreOnEnd, Runnable onEnd) {
        if (owner == null || mc.player == null) return false;
        if (targetSlot < 0 || targetSlot > 8) return false;

        SwapState top = stack.peek();
        if (top != null && top.owner == owner) {
            top.targetSlot = targetSlot;
            top.inputSimulation = inputSimulation;
            top.restoreOnEnd = restoreOnEnd;
            top.restoreTick = restoreDelayTicks < 0 ? Long.MAX_VALUE : mc.player.age + restoreDelayTicks;
            top.onEnd = onEnd;
            setInvSlot(targetSlot, inputSimulation);
            return true;
        }

        if (isActive(owner)) return false;

        int previousSlot = getSelectedSlot();
        if (previousSlot == targetSlot) {
            return true;
        }

        SwapState rootState = stack.peekLast();
        int rootSlot = rootState != null ? rootState.rootSlot : previousSlot;
        boolean rootInputSimulation = rootState != null ? rootState.rootInputSimulation : inputSimulation;

        long restoreTick = restoreDelayTicks < 0 ? Long.MAX_VALUE : mc.player.age + restoreDelayTicks;

        stack.push(new SwapState(owner, rootSlot, rootInputSimulation, targetSlot, inputSimulation, restoreOnEnd, restoreTick, onEnd));
        setInvSlot(targetSlot, inputSimulation);
        return true;
    }

    public static boolean isActive(Module owner) {
        if (owner == null) return false;
        for (SwapState state : stack) {
            if (state.owner == owner) return true;
        }
        return false;
    }

    public static boolean isActive(Class<? extends Module> ownerClass) {
        if (ownerClass == null) return false;
        for (SwapState state : stack) {
            if (ownerClass.isInstance(state.owner)) return true;
        }
        return false;
    }

    public static boolean isOwnerActive(Module owner) {
        return isActive(owner);
    }

    public static boolean isOwnerActive(Class<? extends Module> ownerClass) {
        return isActive(ownerClass);
    }

    public static boolean hasActiveSwaps() {
        return !stack.isEmpty();
    }

    public static int getSelectedSlot() {
        if (mc.player == null) return -1;
        SwapState state = stack.peek();
        return state != null ? state.targetSlot : mc.player.getInventory().getSelectedSlot();
    }

    public static void cancel(Module owner) {
        cancel(owner, true);
    }

    public static void cancel(Module owner, boolean restore) {
        if (owner == null || stack.isEmpty()) return;
        if (!isActive(owner)) return;

        if (restore) {
            resetSwaps(stack.peekLast());
            return;
        }

        while (!stack.isEmpty()) {
            SwapState state = stack.pop();
            endState(state, false, true);
            if (state.owner == owner) {
                break;
            }
        }
    }

    public static void clear() {
        releasePressedHotbarKey();
        stack.clear();
    }

    @Override
    public void onTick() {
        releasePressedHotbarKey();
        if (mc.player == null) {
            clear();
            return;
        }

        if (mc.currentScreen != null) {
            return;
        }

        while (!stack.isEmpty()) {
            SwapState state = stack.peek();
            if (mc.player.age < state.restoreTick) break;

            if (mc.player.isUsingItem() || !AntibanbyDaduncs.canSwapSlotNow() || AntibanbyDaduncs.isSlotPending()) {
                break;
            }

            stack.pop();
            if (state.restoreOnEnd) {
                resetSwaps(state);
                runEnd(state);
                return;
            }

            endState(state, false, false);
        }
    }

    private static void resetSwaps(SwapState fallbackRoot) {
        SwapState root = stack.peekLast();
        if (root == null) root = fallbackRoot;

        if (root != null && mc.player != null && mc.currentScreen == null) {
            setInvSlot(root.rootSlot, root.rootInputSimulation);
        }

        while (!stack.isEmpty()) {
            SwapState state = stack.pop();
            runEnd(state);
        }
    }

    private static void endState(SwapState state, boolean restore, boolean forceRestore) {
        if (state == null) return;

        if (restore && mc.player != null && mc.currentScreen == null && (forceRestore || state.restoreOnEnd)) {
            setInvSlot(state.rootSlot, state.rootInputSimulation);
        }

        runEnd(state);
    }

    private static void runEnd(SwapState state) {
        if (state.onEnd == null) return;
        try {
            state.onEnd.run();
        } catch (Throwable ignored) {
        }
    }

    private static void releasePressedHotbarKey() {
        if (pressedHotbarKey != null) {
            ((IKeyBinding) pressedHotbarKey).resetPressed();
            pressedHotbarKey = null;
        }
    }

    public static void setInvSlot(int slot, boolean inputSimulation) {
        if (slot < 0 || slot > 8 || mc.player == null || mc.currentScreen != null) return;

        if (mc.player.getInventory().getSelectedSlot() == slot) return;

        releasePressedHotbarKey();
        if (inputSimulation) {
            if (mc.options != null && mc.options.hotbarKeys != null && slot < mc.options.hotbarKeys.length) {
                KeyBinding key = mc.options.hotbarKeys[slot];
                if (key != null) {
                    key.setPressed(true);
                    pressedHotbarKey = key;
                }
            }
        }

        InventoryUtils.setInvSlot(slot);
    }

    private static final class SwapState {
        private final Module owner;
        private final int rootSlot;
        private final boolean rootInputSimulation;
        private int targetSlot;
        private boolean inputSimulation;
        private boolean restoreOnEnd;
        private long restoreTick;
        private Runnable onEnd;

        private SwapState(Module owner, int rootSlot, boolean rootInputSimulation, int targetSlot, boolean inputSimulation, boolean restoreOnEnd, long restoreTick, Runnable onEnd) {
            this.owner = owner;
            this.rootSlot = rootSlot;
            this.rootInputSimulation = rootInputSimulation;
            this.targetSlot = targetSlot;
            this.inputSimulation = inputSimulation;
            this.restoreOnEnd = restoreOnEnd;
            this.restoreTick = restoreTick;
            this.onEnd = onEnd;
        }
    }
}
