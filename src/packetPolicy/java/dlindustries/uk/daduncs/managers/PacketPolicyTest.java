package dlindustries.uk.daduncs.managers;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.util.Hand;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.util.ActionResult;

/** Runs without a game window. Verifies the admission rules before prediction. */
public final class PacketPolicyTest {
    private static int checks;
    private static void flag(String name, boolean value) throws Exception {
        var field = AntibanbyDaduncs.class.getDeclaredField(name);
        field.setAccessible(true);
        ((AtomicBoolean) field.get(null)).set(value);
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void fresh() throws Exception {
        AntibanbyDaduncs.reset();
        AntibanbyDaduncs.onTickStart(true);
    }
    private static PlayerInteractEntityC2SPacket attackPacket() throws Exception {
        var type = PlayerInteractEntityC2SPacket.class.getDeclaredField("ATTACK");
        type.setAccessible(true);
        var constructor = PlayerInteractEntityC2SPacket.class.getDeclaredConstructor(int.class, boolean.class, type.getType());
        constructor.setAccessible(true);
        return constructor.newInstance(1, false, type.get(null));
    }
    public static void main(String[] args) throws Exception {
        fresh();
        check(AntibanbyDaduncs.canAttackNow(), "fresh attack");
        check(AntibanbyDaduncs.canInventoryActionNow(), "fresh inventory");
        for (String conflict : new String[]{"attackSentInTick", "blockActionSentInTick",
                "entityInteractSentInTick", "diggingSentInTick", "droppingSentInTick", "movementSentInTick"}) {
            fresh(); flag(conflict, true);
            check(!AntibanbyDaduncs.canInventoryActionNow(), "inventory conflict: " + conflict);
            check(!AntibanbyDaduncs.filterPacket(new CloseHandledScreenC2SPacket(1)), "close must survive: " + conflict);
        }
        fresh();
        var slot = AntibanbyDaduncs.class.getDeclaredField("pendingSlot");
        slot.setAccessible(true);
        ((AtomicInteger) slot.get(null)).set(3);
        check(!AntibanbyDaduncs.canAttackNow(), "pending slot attack");
        check(!AntibanbyDaduncs.canUseItemNow(Hand.MAIN_HAND), "pending slot use");
        check(!AntibanbyDaduncs.canInteractEntityNow(), "pending slot interact");
        check(!AntibanbyDaduncs.canBlockActionNow(), "pending slot block");
        check(AntibanbyDaduncs.canInventoryActionNow(), "explicit inventory slots do not wait for hotbar sync");
        check(!AntibanbyDaduncs.canStartStopDigNow(), "pending slot dig");
        fresh(); flag("diggingSentInTick", true);
        check(AntibanbyDaduncs.canStartStopDigNow(), "abort then start remains allowed");
        flag("digStartStopSentInTick", true);
        check(!AntibanbyDaduncs.canStartStopDigNow(), "duplicate start/stop blocked");
        fresh(); flag("blockActionSentInTick", true); flag("blockPlaceMainSentInTick", true);
        check(AntibanbyDaduncs.canUseItemNow(Hand.MAIN_HAND), "vanilla block-to-item fallback");
        fresh(); flag("entityInteractSentInTick", true);
        check(AntibanbyDaduncs.canInteractEntityNow(), "interact-at to interact fallback");
        check(AntibanbyDaduncs.canUseItemNow(Hand.MAIN_HAND), "entity-to-item fallback");
        fresh();
        var hit = new net.minecraft.util.hit.BlockHitResult(new net.minecraft.util.math.Vec3d(0.5, 1, 0.5),
                net.minecraft.util.math.Direction.UP, net.minecraft.util.math.BlockPos.ORIGIN, false);
        check(AntibanbyDaduncs.canInteractBlockNow(Hand.MAIN_HAND, hit), "first block interaction");
        var miss = net.minecraft.util.hit.BlockHitResult.createMissed(hit.getPos(), hit.getSide(), hit.getBlockPos());
        check(!AntibanbyDaduncs.canInteractBlockNow(Hand.MAIN_HAND, miss), "a missed target is not a block interaction");
        check(AntibanbyDaduncs.filterPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, miss, 1)), "miss also rejected at send");
        flag("blockActionSentInTick", true); flag("blockPlaceMainSentInTick", true);
        var lastHit = AntibanbyDaduncs.class.getDeclaredField("lastPlaceMainHit");
        lastHit.setAccessible(true); lastHit.set(null, hit);
        check(AntibanbyDaduncs.canInteractBlockNow(Hand.MAIN_HAND, hit), "identical repeat");
        check(!AntibanbyDaduncs.canInteractBlockNow(Hand.MAIN_HAND, hit.againstWorldBorder()), "border flag changed");
        var inside = new net.minecraft.util.hit.BlockHitResult(hit.getPos(), hit.getSide(), hit.getBlockPos(), true);
        check(AntibanbyDaduncs.filterPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, inside, 2)), "inside flag changed");
        var otherHit = new net.minecraft.util.hit.BlockHitResult(new net.minecraft.util.math.Vec3d(1.5, 1, 0.5),
                net.minecraft.util.math.Direction.UP, new net.minecraft.util.math.BlockPos(1, 0, 0), false);
        check(!AntibanbyDaduncs.canInteractBlockNow(Hand.MAIN_HAND, otherHit), "different repeat blocked before prediction");
        check(AntibanbyDaduncs.canInteractBlockNow(Hand.OFF_HAND, hit), "offhand fallback");
        AntibanbyDaduncs.reset();
        check(AntibanbyDaduncs.canInventoryActionNow(), "GUI click between ticks");
        // Exercise real packet transitions, including the result callbacks made by the mixin.
        fresh();
        check(!AntibanbyDaduncs.filterPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, 1)), "main block packet");
        check(!AntibanbyDaduncs.filterPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 2, 0, 0)), "main item fallback packet");
        check(!AntibanbyDaduncs.canInteractBlockNow(Hand.OFF_HAND, hit), "wait for main item result");
        AntibanbyDaduncs.onInteractionResult(Hand.MAIN_HAND, ActionResult.PASS, true);
        check(AntibanbyDaduncs.canInteractBlockNow(Hand.OFF_HAND, hit), "offhand block after main PASS");
        check(!AntibanbyDaduncs.filterPacket(new PlayerInteractBlockC2SPacket(Hand.OFF_HAND, hit, 3)), "offhand block packet after main PASS");
        check(!AntibanbyDaduncs.canInteractBlockNow(Hand.MAIN_HAND, hit), "main hand cannot restart after item fallback");

        fresh();
        check(!AntibanbyDaduncs.filterPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 1, 0, 0)), "main item before entity fallback");
        check(!AntibanbyDaduncs.canUseItemNow(Hand.OFF_HAND), "offhand item waits for main result");
        AntibanbyDaduncs.onInteractionResult(Hand.MAIN_HAND, ActionResult.PASS, true);
        check(AntibanbyDaduncs.canUseItemNow(Hand.OFF_HAND), "offhand item after main PASS");
        check(AntibanbyDaduncs.canInteractEntityNow(Hand.OFF_HAND), "offhand entity after main PASS");
        check(!AntibanbyDaduncs.canInteractEntityNow(Hand.MAIN_HAND), "main entity cannot restart after item fallback");
        AntibanbyDaduncs.onInteractionResult(Hand.MAIN_HAND, ActionResult.SUCCESS, true);
        check(!AntibanbyDaduncs.canInteractEntityNow(Hand.OFF_HAND), "accepted main use cannot fall through");
        check(AntibanbyDaduncs.filterPacket(new PlayerInteractItemC2SPacket(Hand.OFF_HAND, 2, 0, 0)),
                "offhand item cannot follow an accepted main item in the same attempt");

        fresh();
        var item = new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 1, 0, 0);
        for (int press = 0; press < 2; press++) {
            InputContext.vanillaUse(() -> {
                check(AntibanbyDaduncs.canUseItemNow(Hand.MAIN_HAND), "new use-key press starts a fresh attempt");
                InputContext.dispatchListeners(() -> {
                    check(AntibanbyDaduncs.canUseItemNow(Hand.MAIN_HAND) != AntibanbyDaduncs.isUseItemSent(),
                            "module callbacks see tick-wide hand use");
                });
                check(!AntibanbyDaduncs.filterPacket(item), "queued use-key press accepted");
                check(!AntibanbyDaduncs.canUseItemNow(Hand.MAIN_HAND), "one main item use per attempt");
                AntibanbyDaduncs.onInteractionResult(Hand.MAIN_HAND, ActionResult.PASS, true);
            });
        }
        check(!AntibanbyDaduncs.canUseItemNow(Hand.MAIN_HAND) && !AntibanbyDaduncs.canAttackNow(),
                "module actions still see both manual clicks");
        try {
            InputContext.vanillaUse(() -> { throw new IllegalStateException("test"); });
        } catch (IllegalStateException expected) {}
        check(!AntibanbyDaduncs.canUseItemNow(Hand.MAIN_HAND), "failed click restored the module limits");

        fresh(); flag("attackSentInTick", true); flag("pendingAttackSwing", true);
        check(!AntibanbyDaduncs.filterPacket(new HandSwingC2SPacket(Hand.MAIN_HAND)), "attack swing");
        check(AntibanbyDaduncs.canUseItemNow(Hand.MAIN_HAND), "use after attack");
        check(AntibanbyDaduncs.canInteractBlockNow(Hand.MAIN_HAND, hit), "block use after attack");
        check(!AntibanbyDaduncs.filterPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 1, 0, 0)), "use packet after attack");
        AntibanbyDaduncs.onInteractionResult(Hand.MAIN_HAND, ActionResult.SUCCESS, true);
        check(!AntibanbyDaduncs.filterPacket(new HandSwingC2SPacket(Hand.MAIN_HAND)), "use swing after attack swing");
        check(AntibanbyDaduncs.filterPacket(new HandSwingC2SPacket(Hand.MAIN_HAND)), "required swing credit is consumed once");
        fresh(); flag("attackSentInTick", true); flag("pendingAttackSwing", true);
        check(!AntibanbyDaduncs.filterPacket(new HandSwingC2SPacket(Hand.MAIN_HAND)), "attack swing before block use");
        check(!AntibanbyDaduncs.filterPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, 1)), "block packet after attack");
        AntibanbyDaduncs.onInteractionResult(Hand.MAIN_HAND, ActionResult.SUCCESS, false);
        check(!AntibanbyDaduncs.filterPacket(new HandSwingC2SPacket(Hand.MAIN_HAND)), "block swing after attack");
        check(!AntibanbyDaduncs.filterPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 2, 0, 0)), "item fallback packet");
        check(!AntibanbyDaduncs.canAttackNow(), "attack cannot follow item use");
        check(!AntibanbyDaduncs.canUseItemNow(Hand.MAIN_HAND), "duplicate item use remains blocked");
        check(AntibanbyDaduncs.shouldSwing(ActionResult.SUCCESS), "client success swings");
        check(!AntibanbyDaduncs.shouldSwing(ActionResult.CONSUME), "anchor consume does not swing");
        check(!AntibanbyDaduncs.shouldSwing(ActionResult.SUCCESS_SERVER), "server success does not swing");
        check(!AntibanbyDaduncs.shouldSwing(ActionResult.PASS), "pass does not swing");
        check(!AntibanbyDaduncs.shouldSwing(ActionResult.FAIL), "failure does not swing");
        AntibanbyDaduncs.onTickEnd();
        check(!AntibanbyDaduncs.canUseItemNow(Hand.MAIN_HAND), "no use after tick end");
        fresh();
        check(!AntibanbyDaduncs.filterPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, false)), "movement packet");
        check(!AntibanbyDaduncs.canUseItemNow(Hand.MAIN_HAND), "no use after movement");
        AntibanbyDaduncs.reset();
        fresh();
        check(!AntibanbyDaduncs.filterPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 1, 0, 0)), "bow item attempt");
        AntibanbyDaduncs.onInteractionResult(Hand.MAIN_HAND, ActionResult.FAIL, true);
        check(AntibanbyDaduncs.canInteractBlockNow(Hand.OFF_HAND, hit), "bow without arrows permits offhand placement");
        check(AntibanbyDaduncs.canInteractEntityNow(Hand.OFF_HAND), "failed item permits offhand entity interaction");
        check(!AntibanbyDaduncs.filterPacket(new PlayerInteractBlockC2SPacket(Hand.OFF_HAND, hit, 2)), "offhand packet after failed main item");

        fresh();
        var attack = attackPacket();
        check(!AntibanbyDaduncs.filterPacket(attack), "first attack packet");
        check(!AntibanbyDaduncs.filterPacket(new HandSwingC2SPacket(Hand.MAIN_HAND)), "first attack swing");
        check(!AntibanbyDaduncs.canAttackNow(), "modules retain one attack per tick");
        InputContext.vanillaAttack(() -> {
            check(AntibanbyDaduncs.canAttackPacketNow(), "queued manual attack admitted");
            check(!AntibanbyDaduncs.canAttackNow(), "manual scope does not relax module scheduler");
            check(!AntibanbyDaduncs.filterPacket(attack), "second manual attack packet");
            check(!AntibanbyDaduncs.filterPacket(new HandSwingC2SPacket(Hand.MAIN_HAND)), "second manual attack swing");
            return true;
        });
        check(!InputContext.isVanillaAttack(), "manual scope restored");
        check(!AntibanbyDaduncs.canAttackPacketNow(), "unscoped repeat remains blocked");
        check(AntibanbyDaduncs.filterPacket(attack), "duplicate module packet blocked");
        try {
            InputContext.vanillaAttack(() -> { throw new IllegalStateException("test"); });
        } catch (IllegalStateException expected) {}
        check(!InputContext.isVanillaAttack(), "manual scope restored after exception");
        check(!InputContext.moduleUse(() -> {}), "blocked vanilla use reports no success");
        check(InputContext.moduleUse(() -> {
            check(!InputContext.moduleUse(() -> { throw new AssertionError("recursive use"); }), "recursive use rejected");
            InputContext.recordResult(ActionResult.SUCCESS);
        }), "vanilla interaction success returned to module");
        check(!InputContext.moduleUse(() -> InputContext.recordResult(ActionResult.FAIL)), "failed vanilla use returned to module");
        try {
            InputContext.moduleUse(() -> { throw new IllegalStateException("test"); });
        } catch (IllegalStateException expected) {}
        check(!InputContext.isModuleUse(), "module scope restored after exception");
        AntibanbyDaduncs.reset();
        // Registry keys test identity without starting the game registry bootstrap.
        var pearl = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.ITEM,
                net.minecraft.util.Identifier.ofVanilla("ender_pearl"));
        var charge = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.ITEM,
                net.minecraft.util.Identifier.ofVanilla("wind_charge"));
        check(!InputContext.moduleItemUse(Hand.MAIN_HAND, pearl,
                () -> InputContext.recordResult(ActionResult.SUCCESS)), "opening a container is not a pearl throw");
        check(!InputContext.moduleItemUse(Hand.MAIN_HAND, pearl,
                () -> InputContext.recordItemResult(Hand.MAIN_HAND, pearl, ActionResult.FAIL)), "failed item use does not advance");
        check(!InputContext.moduleItemUse(Hand.MAIN_HAND, pearl,
                () -> InputContext.recordItemResult(Hand.MAIN_HAND, pearl, ActionResult.PASS)), "cooldown or PASS does not advance");
        check(!InputContext.moduleItemUse(Hand.MAIN_HAND, pearl,
                () -> InputContext.recordItemResult(Hand.OFF_HAND, pearl, ActionResult.SUCCESS)), "other hand does not complete request");
        check(!InputContext.moduleItemUse(Hand.MAIN_HAND, pearl,
                () -> InputContext.recordItemResult(Hand.MAIN_HAND, charge, ActionResult.SUCCESS)), "other item does not complete request");
        check(InputContext.moduleItemUse(Hand.MAIN_HAND, pearl, () -> {
            InputContext.recordResult(ActionResult.PASS);
            InputContext.recordItemResult(Hand.MAIN_HAND, pearl, ActionResult.SUCCESS);
        }), "block PASS then intended item success completes request");
        check(InputContext.moduleItemUse(Hand.MAIN_HAND, charge,
                () -> InputContext.recordItemResult(Hand.MAIN_HAND, charge, ActionResult.SUCCESS)), "next attempt can succeed after failures");
        try {
            InputContext.moduleItemUse(Hand.MAIN_HAND, pearl, () -> { throw new IllegalStateException("test"); });
        } catch (IllegalStateException expected) {}
        check(!InputContext.isModuleUse(), "item attempt scope restored after exception");
        fresh();
        ((AtomicInteger) slot.get(null)).set(3);
        check(AntibanbyDaduncs.canInventoryActionNow(), "hover inventory click does not wait for hotbar sync");
        flag("attackSentInTick", true);
        check(!AntibanbyDaduncs.canInventoryActionNow(), "hover inventory still rejects conflicting attack");
        AntibanbyDaduncs.reset();
        var drop = new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.DROP_ITEM,
                net.minecraft.util.math.BlockPos.ORIGIN, net.minecraft.util.math.Direction.DOWN);
        var dropAll = new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.DROP_ALL_ITEMS,
                net.minecraft.util.math.BlockPos.ORIGIN, net.minecraft.util.math.Direction.DOWN);
        var swapHands = new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                net.minecraft.util.math.BlockPos.ORIGIN, net.minecraft.util.math.Direction.DOWN);
        var dig = new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                net.minecraft.util.math.BlockPos.ORIGIN, net.minecraft.util.math.Direction.UP);
        fresh();
        AntibanbyDaduncs.reserveInventoryKeys(true, true);
        check(!AntibanbyDaduncs.canInventoryActionNow(), "inventory clicks yield to queued keys");
        check(!AntibanbyDaduncs.canAttackPacketNow() && AntibanbyDaduncs.filterPacket(attack), "attack yields before prediction and send");
        check(!AntibanbyDaduncs.canUseItemNow(Hand.OFF_HAND)
                && AntibanbyDaduncs.filterPacket(new PlayerInteractItemC2SPacket(Hand.OFF_HAND, 1, 0, 0)), "offhand use yields at both boundaries");
        check(!AntibanbyDaduncs.canStartStopDigNow() && AntibanbyDaduncs.filterPacket(dig), "dig yields at both boundaries");
        check(AntibanbyDaduncs.canSwapHandsNow() && !AntibanbyDaduncs.filterPacket(swapHands), "swap before drop");
        check(AntibanbyDaduncs.hasPendingInventoryKey(), "drop retains priority after a swap");
        check(!AntibanbyDaduncs.filterPacket(swapHands), "second native swap in the same tick");
        check(!AntibanbyDaduncs.filterPacket(drop), "drop follows queued swaps");
        check(!AntibanbyDaduncs.canSwapHandsNow() && AntibanbyDaduncs.filterPacket(swapHands), "late swap waits at both boundaries");
        check(!AntibanbyDaduncs.hasPendingInventoryKey() && AntibanbyDaduncs.canAttackNow(), "world actions resume after input");
        fresh();
        AntibanbyDaduncs.reserveInventoryKeys(true, false);
        check(!AntibanbyDaduncs.canAttackNow(), "swap alone gets priority");
        check(!AntibanbyDaduncs.filterPacket(swapHands) && AntibanbyDaduncs.canAttackNow(), "swap alone releases priority");
        check(!AntibanbyDaduncs.filterPacket(attack) && !AntibanbyDaduncs.canSwapHandsNow(), "hold an unconsumed key after attack");
        fresh();
        check(!AntibanbyDaduncs.filterPacket(swapHands), "deferred swap is eligible next tick");
        fresh();
        AntibanbyDaduncs.reserveInventoryKeys(false, true);
        check(AntibanbyDaduncs.canDropNow(), "queued drop has priority");
        check(!AntibanbyDaduncs.canAttackNow(), "module attack yields to queued drop");
        check(!AntibanbyDaduncs.canBlockActionNow(), "module block use yields to queued drop");
        check(!AntibanbyDaduncs.canUseItemNow(Hand.MAIN_HAND), "module item use yields to queued drop");
        for (var packet : new PlayerActionC2SPacket[]{drop, drop, dropAll}) {
            check(!AntibanbyDaduncs.filterPacket(packet), "queued drop packet allowed");
            AntibanbyDaduncs.finishDrop(true);
            check(!AntibanbyDaduncs.filterPacket(new HandSwingC2SPacket(Hand.MAIN_HAND)), "each drop retains its swing");
        }
        check(!AntibanbyDaduncs.hasPendingInventoryKey(), "drop releases input priority");
        check(AntibanbyDaduncs.canAttackNow(), "attack after drops remains allowed");
        check(AntibanbyDaduncs.filterPacket(new HandSwingC2SPacket(Hand.MAIN_HAND)), "drop swing credit consumed once");
        check(!AntibanbyDaduncs.filterPacket(drop), "empty-stack drop packet is allowed as in vanilla");
        AntibanbyDaduncs.finishDrop(false);
        check(AntibanbyDaduncs.filterPacket(new HandSwingC2SPacket(Hand.MAIN_HAND)), "empty drop grants no extra swing");
        for (String conflict : new String[]{"attackSentInTick", "blockActionSentInTick",
                "entityInteractSentInTick", "inventoryActionSentInTick", "useItemMainSentInTick",
                "useItemOffSentInTick", "releasingSentInTick", "diggingSentInTick", "movementSentInTick"}) {
            fresh(); flag(conflict, true);
            check(!AntibanbyDaduncs.canDropNow(), "pre-prediction drop guard: " + conflict);
            check(AntibanbyDaduncs.filterPacket(drop), "packet drop guard: " + conflict);
        }
        fresh();
        ((AtomicInteger) slot.get(null)).set(2);
        check(!AntibanbyDaduncs.canDropNow(), "drop waits for selected slot sync");
        check(AntibanbyDaduncs.canInventoryActionNow(), "InvCart and UtilitySwapper explicit clicks do not wait for hotbar sync");
        AntibanbyDaduncs.reset();
        AntibanbyDaduncs.onTickStart(true);
        AntibanbyDaduncs.onTickEnd();
        check(!AntibanbyDaduncs.canDropNow(), "drop outside module tick blocked");
        AntibanbyDaduncs.onTickStart(false);
        AntibanbyDaduncs.onTickEnd();
        check(AntibanbyDaduncs.canDropNow(), "vanilla handles its own drop input in a passive profile");
        check(AntibanbyDaduncs.canAttackPacketNow(), "vanilla handles its own attack input");
        check(AntibanbyDaduncs.canStartStopDigNow(), "vanilla handles its own mining input");
        check(!AntibanbyDaduncs.filterPacket(drop), "passive profile preserves the vanilla send");
        check(!AntibanbyDaduncs.filterPacket(new HandSwingC2SPacket(Hand.MAIN_HAND)), "passive profile preserves the swing");
        check(!AntibanbyDaduncs.filterPacket(new UpdateSelectedSlotC2SPacket(2)), "passive profile preserves hotbar sync");
        check(!AntibanbyDaduncs.isSlotPending(), "passive profile does not queue native hotbar changes");
        // Register ordinary work first, as happens when Auto XP was enabled before DHand.
        fresh();
        var events = new dlindustries.uk.daduncs.event.EventManager();
        var order = new java.util.ArrayList<String>();
        events.add(dlindustries.uk.daduncs.event.events.TickListener.class, () -> {
            order.add("module");
            check(AntibanbyDaduncs.isInventoryOpenReserved(), "priority listener reserves before ordinary modules");
            check(!AntibanbyDaduncs.canAttackNow(), "queued inventory blocks module attack");
            check(!AntibanbyDaduncs.canUseItemNow(Hand.MAIN_HAND), "queued inventory blocks continuous XP use");
            check(!AntibanbyDaduncs.canBlockActionNow(), "queued inventory blocks anchor use");
            check(!AntibanbyDaduncs.canInteractEntityNow(), "queued inventory blocks entity use");
            check(!AntibanbyDaduncs.canStartStopDigNow(), "queued inventory blocks digging");
            check(AntibanbyDaduncs.isActionSent(), "legacy module guards yield to inventory reservation");
        });
        events.add(dlindustries.uk.daduncs.event.events.TickListener.class, () -> {
            order.add("inventory");
            check(AntibanbyDaduncs.canAttackNow(), "priority listener starts before other action requests");
            AntibanbyDaduncs.reserveInventoryOpen();
        }, Integer.MAX_VALUE);
        var dispatch = dlindustries.uk.daduncs.event.EventManager.class.getDeclaredMethod("fireImpl",
                dlindustries.uk.daduncs.event.Event.class);
        dispatch.setAccessible(true);
        dispatch.invoke(events, new dlindustries.uk.daduncs.event.events.TickListener.TickEvent());
        check(order.equals(java.util.List.of("inventory", "module")), "priority is independent of enable order");
        check(AntibanbyDaduncs.canInventoryActionNow(), "inventory remains usable after priority swap");
        check(!AntibanbyDaduncs.filterPacket(new CloseHandledScreenC2SPacket(1)), "reservation does not block screen close");
        AntibanbyDaduncs.onTickEnd();
        check(!AntibanbyDaduncs.isInventoryOpenReserved(), "reservation ends with tick");
        fresh();
        check(AntibanbyDaduncs.canAttackNow(), "module work resumes on next unreserved tick");
        AntibanbyDaduncs.reserveInventoryOpen();
        AntibanbyDaduncs.reset();
        check(!AntibanbyDaduncs.isInventoryOpenReserved(), "disconnect clears reservation");

        // Verify EventManager descending priority sorting with negative and extreme priorities.
        var prioEvents = new dlindustries.uk.daduncs.event.EventManager();
        var prioOrder = new java.util.ArrayList<String>();
        prioEvents.add(dlindustries.uk.daduncs.event.events.TickListener.class, () -> prioOrder.add("min"), Integer.MIN_VALUE);
        prioEvents.add(dlindustries.uk.daduncs.event.events.TickListener.class, () -> prioOrder.add("neg"), -1);
        prioEvents.add(dlindustries.uk.daduncs.event.events.TickListener.class, () -> prioOrder.add("zero"), 0);
        prioEvents.add(dlindustries.uk.daduncs.event.events.TickListener.class, () -> prioOrder.add("max"), Integer.MAX_VALUE);
        dispatch.invoke(prioEvents, new dlindustries.uk.daduncs.event.events.TickListener.TickEvent());
        check(prioOrder.equals(java.util.List.of("max", "zero", "neg", "min")), "negative priorities must not overflow comparator");

        // Verify canSwingAirNow mirrors attack/drop/inventory/slot/swing rules.
        fresh();
        check(AntibanbyDaduncs.canSwingAirNow(), "fresh air swing allowed");
        AntibanbyDaduncs.reserveInventoryOpen();
        check(!AntibanbyDaduncs.canSwingAirNow(), "reserved inventory blocks air swing");
        fresh();
        AntibanbyDaduncs.reserveInventoryKeys(false, true);
        check(!AntibanbyDaduncs.canSwingAirNow(), "queued drop blocks air swing");
        fresh();
        ((AtomicInteger) slot.get(null)).set(1);
        check(!AntibanbyDaduncs.canSwingAirNow(), "pending slot blocks air swing");
        fresh();
        check(!AntibanbyDaduncs.filterPacket(new HandSwingC2SPacket(Hand.MAIN_HAND)), "first swing allowed");
        check(!AntibanbyDaduncs.canSwingAirNow(), "duplicate non-vanilla air swing blocked");
        InputContext.vanillaAttack(() -> {
            check(AntibanbyDaduncs.canSwingAirNow(), "queued manual attack may swing again");
            return true;
        });

        // Verify pendingDigSwing only validates MAIN_HAND swings.
        fresh();
        flag("diggingSentInTick", true);
        flag("pendingDigSwing", true);
        flag("suppressedActionInTick", true);
        check(AntibanbyDaduncs.filterPacket(new HandSwingC2SPacket(Hand.OFF_HAND)), "offhand swing does not satisfy dig swing");
        check(!AntibanbyDaduncs.filterPacket(new HandSwingC2SPacket(Hand.MAIN_HAND)), "mainhand dig swing succeeds");

        fresh();
        flag("movementSentInTick", true);
        for (Hand hand : Hand.values()) {
            var swing = new HandSwingC2SPacket(hand);
            var other = new HandSwingC2SPacket(hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND);
            InputContext.serverSwing(hand, () -> {
                check(AntibanbyDaduncs.filterPacket(other), "server animation applies only to its hand");
                check(!AntibanbyDaduncs.filterPacket(swing), "server animation reply after movement");
                check(AntibanbyDaduncs.filterPacket(swing), "one reply per server animation");
            });
            InputContext.serverSwing(hand, () -> {});
            check(AntibanbyDaduncs.filterPacket(swing), "unused animation permission ends with handler");
        }
        System.out.println("Packet policy: " + checks + " checks passed");
    }
}
