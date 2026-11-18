package dlindustries.uk.daduncs.managers;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;

/** Scopes input origins on the client thread. Always restored, including on exceptions. */
public final class InputContext {
    private static final ThreadLocal<Boolean> vanillaAttack = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<UseAttempt> moduleUse = new ThreadLocal<>();
    private static final ThreadLocal<EnumSet<Hand>> vanillaUseHands = new ThreadLocal<>();
    private static final ThreadLocal<Hand> serverSwing = new ThreadLocal<>();
    private InputContext() {}

    public static boolean isVanillaAttack() { return vanillaAttack.get(); }
    public static boolean isModuleUse() { return moduleUse.get() != null; }

    public static void serverSwing(Hand hand, Runnable action) {
        var previous = serverSwing.get();
        serverSwing.set(hand);
        try { action.run(); }
        finally {
            if (previous == null) serverSwing.remove();
            else serverSwing.set(previous);
        }
    }

    static boolean consumeServerSwing(Hand hand) {
        if (serverSwing.get() != hand) return false;
        serverSwing.remove();
        return true;
    }

    public static boolean vanillaAttack(BooleanSupplier action) {
        boolean previous = vanillaAttack.get();
        vanillaAttack.set(true);
        try { return action.getAsBoolean(); }
        finally { vanillaAttack.set(previous); }
    }

    public static boolean moduleUse(Runnable action) {
        return runUse(new UseAttempt(), action);
    }

    /** Each call from Minecraft's use-key loop starts a new main/offhand attempt. */
    public static void vanillaUse(Runnable action) {
        var previous = vanillaUseHands.get();
        vanillaUseHands.set(EnumSet.noneOf(Hand.class));
        try {
            action.run();
        } finally {
            if (previous == null) vanillaUseHands.remove();
            else vanillaUseHands.set(previous);
        }
    }

    static boolean usedHand(Hand hand, boolean usedInTick) {
        var hands = vanillaUseHands.get();
        return hands == null || isModuleUse() ? usedInTick : hands.contains(hand);
    }

    static void recordHandUse(Hand hand) {
        var hands = vanillaUseHands.get();
        if (hands != null && !isModuleUse()) hands.add(hand);
    }

    /** Module callbacks keep the tick-wide limits even when a native click fires an event. */
    public static void dispatchListeners(Runnable dispatch) {
        var hands = vanillaUseHands.get();
        if (hands == null) {
            dispatch.run();
            return;
        }
        vanillaUseHands.remove();
        try {
            dispatch.run();
        } finally {
            vanillaUseHands.set(hands);
        }
    }

    public static boolean moduleItemUse(Hand hand, RegistryKey<Item> item, Runnable action) {
        UseAttempt attempt = new UseAttempt();
        attempt.expectedHand = hand;
        attempt.expectedItem = item;
        attempt.itemOnly = true;
        return runUse(attempt, action);
    }

    private static boolean runUse(UseAttempt attempt, Runnable action) {
        // An event handler must not recursively trigger another use attempt.
        if (isModuleUse()) return false;
        moduleUse.set(attempt);
        try {
            action.run();
            return attempt.accepted;
        } finally { moduleUse.remove(); }
    }

    public static void recordResult(ActionResult result) {
        UseAttempt attempt = moduleUse.get();
        if (attempt != null && !attempt.itemOnly && result.isAccepted()) attempt.accepted = true;
    }

    public static void recordItemResult(Hand hand, RegistryKey<Item> item, ActionResult result) {
        UseAttempt attempt = moduleUse.get();
        if (attempt != null && attempt.itemOnly && hand == attempt.expectedHand
                && item == attempt.expectedItem && result.isAccepted()) attempt.accepted = true;
    }

    private static final class UseAttempt {
        boolean accepted;
        boolean itemOnly;
        Hand expectedHand;
        RegistryKey<Item> expectedItem;
    }
}
