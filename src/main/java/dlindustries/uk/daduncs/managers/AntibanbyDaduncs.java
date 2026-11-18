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

import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.util.Hand;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class AntibanbyDaduncs {
	private static boolean gameplayInput;
	private static final AtomicBoolean actionSentInTick = new AtomicBoolean(false);
	private static final AtomicBoolean attackSentInTick = new AtomicBoolean(false);
	private static final AtomicBoolean blockActionSentInTick = new AtomicBoolean(false);
	private static final AtomicBoolean blockPlaceMainSentInTick = new AtomicBoolean(false);
	private static final AtomicBoolean blockPlaceOffSentInTick = new AtomicBoolean(false);
	private static final AtomicBoolean entityInteractSentInTick = new AtomicBoolean(false);
	private static final AtomicBoolean inventoryActionSentInTick = new AtomicBoolean(false);
	private static final AtomicBoolean releasingSentInTick = new AtomicBoolean(false);
	private static final AtomicBoolean diggingSentInTick = new AtomicBoolean(false);
	private static final AtomicBoolean digStartStopSentInTick = new AtomicBoolean(false);
	private static final AtomicBoolean useItemMainSentInTick = new AtomicBoolean(false);
	private static final AtomicBoolean useItemOffSentInTick = new AtomicBoolean(false);
	private static final AtomicBoolean droppingSentInTick = new AtomicBoolean(false);
	// Offhand swaps can precede vanilla actions; do not treat them as attacks.
	private static final AtomicBoolean pendingAttackSwing = new AtomicBoolean(false);
	private static boolean dropInputPending;
	private static boolean swapHandsInputPending;
	private static boolean inventoryOpenReserved;

	/** DHand reserves the rest of this tick after its priority listener runs. */
	public static void reserveInventoryOpen() { inventoryOpenReserved = true; }
	public static boolean isInventoryOpenReserved() { return inventoryOpenReserved; }
	private static final AtomicBoolean pendingDropSwing = new AtomicBoolean(false);
	private static final AtomicBoolean pendingDigSwing = new AtomicBoolean(false);
	private static final AtomicBoolean swingSentInTick = new AtomicBoolean(false);
	private static final AtomicBoolean suppressedActionInTick = new AtomicBoolean(false);
	private static volatile net.minecraft.util.hit.BlockHitResult lastPlaceMainHit = null;
	private static volatile net.minecraft.util.hit.BlockHitResult lastPlaceOffHit = null;
	private static final AtomicInteger pendingSlot = new AtomicInteger(-1);
	private static final AtomicInteger lastSentSlot = new AtomicInteger(-1);
	private static boolean mainItemFallsThrough;
	private static Hand pendingUseSwing;

	private static volatile float lastSentYaw = 0.0f;
	private static volatile float lastSentPitch = 0.0f;
	private static volatile boolean hasSentRotation = false;

	private static final AtomicBoolean movementSentInTick = new AtomicBoolean(false);
	private static final AtomicBoolean tickEnded = new AtomicBoolean(false);
	private static final AtomicBoolean inTick = new AtomicBoolean(false);

	private AntibanbyDaduncs() {}

	public static boolean isAttackSent() {
		return attackSentInTick.get();
	}

	public static boolean isMovementSent() {
		return movementSentInTick.get();
	}

	public static boolean isInTick() {
		return inTick.get();
	}

	public static boolean isTickEnded() {
		return tickEnded.get();
	}

	public static boolean isSlotPending() {
		return pendingSlot.get() != -1;
	}

	private static boolean isHoldingPiercingWeapon() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) {
			return false;
		}
		if (mc.player.getMainHandStack().contains(DataComponentTypes.PIERCING_WEAPON)) {
			return true;
		}
		int serverSlot = lastSentSlot.get();
		if (serverSlot >= 0 && serverSlot < 9) {
			return mc.player.getInventory().getStack(serverSlot).contains(DataComponentTypes.PIERCING_WEAPON);
		}
		return false;
	}

	public static int getPendingSlot() {
		return pendingSlot.get();
	}

	public static boolean isBlockActionSent() {
		return blockActionSentInTick.get();
	}

	public static boolean isActionSent() {
		return inventoryOpenReserved || actionSentInTick.get();
	}

	public static boolean isEntityInteractSent() {
		return entityInteractSentInTick.get();
	}

	public static boolean isReleasingSent() {
		return releasingSentInTick.get();
	}

	public static boolean isDiggingSent() {
		return diggingSentInTick.get();
	}

	public static boolean isDigStartStopSent() {
		return digStartStopSentInTick.get();
	}

	public static boolean isBlockPlaceSent() {
		return blockPlaceMainSentInTick.get() || blockPlaceOffSentInTick.get();
	}

	public static boolean isUseItemSent() {
		return useItemMainSentInTick.get() || useItemOffSentInTick.get();
	}

	public static boolean isInventoryActionSent() {
		return inventoryActionSentInTick.get();
	}

	public static boolean hasPendingInventoryKey() {
		return dropInputPending || swapHandsInputPending;
	}

	public static void reserveInventoryKeys(boolean swapHands, boolean drop) {
		swapHandsInputPending = swapHands;
		dropInputPending = drop;
	}

	private static boolean beforeMovement() {
		return inTick.get() && !movementSentInTick.get() && !tickEnded.get();
	}

	private static boolean canStartWorldAction() {
		return beforeMovement() && !inventoryOpenReserved && !hasPendingInventoryKey() && !isSlotPending();
	}

	public static boolean canDropNow() {
		if (!gameplayInput) return true;
		return beforeMovement() && !isSlotPending() && !attackSentInTick.get() && !blockActionSentInTick.get()
				&& !entityInteractSentInTick.get() && !inventoryActionSentInTick.get()
				&& !releasingSentInTick.get() && !diggingSentInTick.get()
				&& !useItemMainSentInTick.get() && !useItemOffSentInTick.get();
	}

	/** Minecraft reads swap-hands before drop. Both may run in the same tick. */
	public static boolean canSwapHandsNow() {
		return !gameplayInput || (canDropNow() && !droppingSentInTick.get());
	}

	public static void finishDrop(boolean droppedItem) {
		if (!droppedItem) pendingDropSwing.set(false);
	}

	public static boolean canSwapSlotNow() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return canStartWorldAction() && !actionSentInTick.get() && mc.currentScreen == null;
	}

	public static boolean canAttackNow() {
		return canAttack(false);
	}

	public static boolean canAttackPacketNow() {
		return !gameplayInput || canAttack(InputContext.isVanillaAttack());
	}

	public static boolean canSwingAirNow() {
		if (!gameplayInput) return true;
		return canStartWorldAction()
				&& (InputContext.isVanillaAttack() || !attackSentInTick.get())
				&& !blockPlaceMainSentInTick.get()
				&& !blockPlaceOffSentInTick.get()
				&& !digStartStopSentInTick.get()
				&& !entityInteractSentInTick.get()
				&& !inventoryActionSentInTick.get()
				&& !releasingSentInTick.get()
				&& !diggingSentInTick.get()
				&& !useItemMainSentInTick.get()
				&& !useItemOffSentInTick.get()
				&& (InputContext.isVanillaAttack() || !swingSentInTick.get());
	}

	private static boolean canAttack(boolean repeatedInput) {
		return canStartWorldAction()
				&& (repeatedInput || !attackSentInTick.get())
				&& !blockActionSentInTick.get()
				&& !entityInteractSentInTick.get()
				&& !inventoryActionSentInTick.get()
				&& !releasingSentInTick.get()
				&& !diggingSentInTick.get()
				&& !useItemMainSentInTick.get()
				&& !useItemOffSentInTick.get();
	}

	public static boolean canBlockActionNow() {
		return canBlockActionNow(Hand.MAIN_HAND);
	}

	private static boolean canBlockActionNow(Hand hand) {
		return canStartWorldAction()
				&& !entityInteractSentInTick.get() && !inventoryActionSentInTick.get()
				&& !releasingSentInTick.get() && !diggingSentInTick.get()
				&& canFollowItemUse(hand);
	}

	private static boolean canFollowItemUse(Hand hand) {
		return !usedHand(Hand.OFF_HAND) && (!usedHand(Hand.MAIN_HAND)
				|| (hand == Hand.OFF_HAND && mainItemFallsThrough));
	}

	private static boolean usedHand(Hand hand) {
		return InputContext.usedHand(hand, hand == Hand.MAIN_HAND ? useItemMainSentInTick.get() : useItemOffSentInTick.get());
	}

	public static boolean shouldSwing(net.minecraft.util.ActionResult result) {
		return result instanceof net.minecraft.util.ActionResult.Success success
				&& success.swingSource() == net.minecraft.util.ActionResult.SwingSource.CLIENT;
	}

	public static void onInteractionResult(Hand hand, net.minecraft.util.ActionResult result, boolean itemUse) {
		InputContext.recordResult(result);
		if (itemUse && hand == Hand.MAIN_HAND) mainItemFallsThrough = !(result instanceof net.minecraft.util.ActionResult.Success);
		if (shouldSwing(result)) pendingUseSwing = hand;
	}

	public static boolean canUseItemNow(Hand hand) {
		if (!gameplayInput) return true;
		if (!canStartWorldAction()
				|| inventoryActionSentInTick.get() || releasingSentInTick.get()
				|| diggingSentInTick.get()) {
			return false;
		}
		return canFollowItemUse(hand);
	}

	// These checks also run before vanilla changes predicted inventory/world state.
	public static boolean canInventoryActionNow() {
		if (!gameplayInput) return true;
		return !inTick.get() || (!hasPendingInventoryKey() && !movementSentInTick.get()
				&& !attackSentInTick.get() && !blockActionSentInTick.get()
				&& !entityInteractSentInTick.get() && !diggingSentInTick.get()
				&& !droppingSentInTick.get());
	}

	public static boolean canStartStopDigNow() {
		if (!gameplayInput) return true;
		return canStartWorldAction() && !attackSentInTick.get() && !blockPlaceMainSentInTick.get()
				&& !blockPlaceOffSentInTick.get() && !digStartStopSentInTick.get()
				&& !entityInteractSentInTick.get() && !inventoryActionSentInTick.get()
				&& !releasingSentInTick.get() && !useItemMainSentInTick.get() && !useItemOffSentInTick.get();
	}

	public static boolean canInteractEntityNow() {
		return canInteractEntityNow(Hand.MAIN_HAND);
	}

	public static boolean canInteractEntityNow(Hand hand) {
		if (!gameplayInput) return true;
		return canStartWorldAction() && !blockActionSentInTick.get()
				&& !inventoryActionSentInTick.get() && !releasingSentInTick.get()
				&& !diggingSentInTick.get() && canFollowItemUse(hand);
	}

	public static boolean canInteractBlockNow(Hand hand, net.minecraft.util.hit.BlockHitResult hit) {
		if (!gameplayInput) return true;
		if (!canBlockActionNow(hand)) return false;
		if (hit == null || hit.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) return false;
		var previous = hand == Hand.MAIN_HAND ? lastPlaceMainHit : lastPlaceOffHit;
		return previous == null || (previous.getBlockPos().equals(hit.getBlockPos())
				&& previous.isInsideBlock() == hit.isInsideBlock() && previous.isAgainstWorldBorder() == hit.isAgainstWorldBorder()
				&& previous.getSide() == hit.getSide() && previous.getPos().equals(hit.getPos()));
	}

	public static float getLastSentYaw() {
		if (hasSentRotation) return lastSentYaw;
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null ? mc.player.getYaw() : 0.0f;
	}

	public static float getLastSentPitch() {
		if (hasSentRotation) return lastSentPitch;
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null ? mc.player.getPitch() : 0.0f;
	}

	public static boolean hasSentRotation() {
		return hasSentRotation;
	}

	public static void onTickStart(boolean gameplay) {
		gameplayInput = gameplay;
		inTick.set(true);
		movementSentInTick.set(false);
		tickEnded.set(false);
		resetTickActions();
		flushPendingSlot();
	}

	public static void onTickEnd() {
		inTick.set(false);
		movementSentInTick.set(true);
		tickEnded.set(true);
		resetTickActions();
	}

	private static void resetTickActions() {
		inventoryOpenReserved = false;
		reserveInventoryKeys(false, false);
		pendingDropSwing.set(false);
		mainItemFallsThrough = false;
		pendingUseSwing = null;
		actionSentInTick.set(false);
		attackSentInTick.set(false);
		blockActionSentInTick.set(false);
		blockPlaceMainSentInTick.set(false);
		blockPlaceOffSentInTick.set(false);
		lastPlaceMainHit = null;
		lastPlaceOffHit = null;
		entityInteractSentInTick.set(false);
		inventoryActionSentInTick.set(false);
		releasingSentInTick.set(false);
		diggingSentInTick.set(false);
		digStartStopSentInTick.set(false);
		useItemMainSentInTick.set(false);
		useItemOffSentInTick.set(false);
		droppingSentInTick.set(false);
		pendingAttackSwing.set(false);
		pendingDigSwing.set(false);
		swingSentInTick.set(false);
		suppressedActionInTick.set(false);
	}

	public static void reset() {
		gameplayInput = false;
		inTick.set(false);
		movementSentInTick.set(false);
		tickEnded.set(false);
		resetTickActions();
		pendingSlot.set(-1);
		lastSentSlot.set(-1);
		lastSentYaw = 0.0f;
		lastSentPitch = 0.0f;
		hasSentRotation = false;
	}

	public static void onPacketReceive(Packet<?> packet) {
		if (packet instanceof UpdateSelectedSlotS2CPacket slotPacket) {
			lastSentSlot.set(slotPacket.slot());
			pendingSlot.set(-1);
		} else if (packet instanceof PlayerPositionLookS2CPacket posPacket) {
			var relatives = posPacket.relatives();
			float newYaw = relatives.contains(PositionFlag.Y_ROT) ? lastSentYaw + posPacket.change().yaw() : posPacket.change().yaw();
			float newPitch = relatives.contains(PositionFlag.X_ROT) ? lastSentPitch + posPacket.change().pitch() : posPacket.change().pitch();
			if (Float.isFinite(newYaw)) {
				lastSentYaw = newYaw;
			}
			if (Float.isFinite(newPitch)) {
				lastSentPitch = Math.max(-90.0f, Math.min(90.0f, newPitch));
			}
			hasSentRotation = true;
		} else if (packet instanceof GameJoinS2CPacket) {
			reset();
		} else if (packet instanceof PlayerRespawnS2CPacket) {
			pendingSlot.set(-1);
			lastSentSlot.set(-1);
			resetTickActions();
			hasSentRotation = false;
		}
	}

	public static boolean isActionPacket(Packet<?> packet) {
		return packet instanceof PlayerInteractEntityC2SPacket ||
				packet instanceof PlayerInteractBlockC2SPacket ||
				packet instanceof PlayerInteractItemC2SPacket ||
				packet instanceof PlayerActionC2SPacket;
	}

	private static boolean isPostMovementSuppressedPacket(Packet<?> packet) {
		if (!isActionPacket(packet)) {
			return false;
		}
		if (packet instanceof PlayerActionC2SPacket actionPacket) {
			PlayerActionC2SPacket.Action action = actionPacket.getAction();
			// Never drop client-initiated stop/abort packets or the server stays desynced (still using/digging).
			if (action == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM ||
					action == PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK) {
				return false;
			}
		}
		return true;
	}

	public static boolean filterPacket(Packet<?> packet) {
		// Minecraft replies to a server-requested animation even between input ticks.
		if (packet instanceof HandSwingC2SPacket swing && InputContext.consumeServerSwing(swing.getHand())) return false;
		if (packet instanceof ClientTickEndC2SPacket) {
			inTick.set(false);
			movementSentInTick.set(true);
			tickEnded.set(true);
			resetTickActions();
			return false;
		}

		if (packet instanceof PlayerMoveC2SPacket movePacket) {
			movementSentInTick.set(true);
			if (movePacket.changesLook()) {
				lastSentYaw = movePacket.getYaw(lastSentYaw);
				lastSentPitch = movePacket.getPitch(lastSentPitch);
				hasSentRotation = true;
			}
			return false;
		}

		if (!gameplayInput) {
			if (packet instanceof UpdateSelectedSlotC2SPacket slot) lastSentSlot.set(slot.getSelectedSlot());
			return false;
		}

		if (isPostMovementSuppressedPacket(packet)) {
			if (movementSentInTick.get() || tickEnded.get() || !inTick.get()) {
				suppressedActionInTick.set(true);
				Recorder tm = Recorder.getInstance();
				if (tm != null) {
					tm.recordModuleEvent("Antiban", "SUPPRESSED_POST_MOVEMENT_ACTION",
							"Blocked post-movement action: " + packet.getClass().getSimpleName(), null);
				}
				return true;
			}
		}

		if (isSlotPending() && (packet instanceof PlayerInteractEntityC2SPacket
				|| packet instanceof PlayerInteractBlockC2SPacket
				|| packet instanceof PlayerInteractItemC2SPacket)) {
			suppressedActionInTick.set(true);
			return true;
		}

		if (packet instanceof PlayerInteractEntityC2SPacket entityPacket) {
			final boolean[] isAttack = {false};
			final Hand[] interactionHand = {Hand.MAIN_HAND};
			entityPacket.handle(new PlayerInteractEntityC2SPacket.Handler() {
				@Override
				public void interact(Hand hand) { interactionHand[0] = hand; }

				@Override
				public void interactAt(Hand hand, net.minecraft.util.math.Vec3d pos) { interactionHand[0] = hand; }

				@Override
				public void attack() {
					isAttack[0] = true;
				}
			});

			if (isAttack[0]) {
				if (!canAttackPacketNow()) {
					suppressedActionInTick.set(true);
					Recorder tm = Recorder.getInstance();
					if (tm != null) {
						tm.recordModuleEvent("Antiban", "SUPPRESSED_CONFLICTING_ATTACK",
								"Blocked conflicting or duplicate attack in same tick (PacketOrder/MultiAttack)", null);
					}
					return true;
				}
				suppressedActionInTick.set(false);
				actionSentInTick.set(true);
				attackSentInTick.set(true);
				pendingAttackSwing.set(true);
			} else {
				if (!canInteractEntityNow(interactionHand[0])) {
					suppressedActionInTick.set(true);
					Recorder tm = Recorder.getInstance();
					if (tm != null) {
						tm.recordModuleEvent("Antiban", "SUPPRESSED_CONFLICTING_ENTITY_ACTION",
								"Blocked entity interact after block/inventory/use/release/dig (PacketOrder/MultiActions)", null);
					}
					return true;
				}
				suppressedActionInTick.set(false);
				actionSentInTick.set(true);
				entityInteractSentInTick.set(true);
			}
			return false;
		}

		if (packet instanceof PlayerInteractBlockC2SPacket blockPacket) {
			if (!canInteractBlockNow(blockPacket.getHand(), blockPacket.getBlockHitResult())) {
				suppressedActionInTick.set(true);
				Recorder tm = Recorder.getInstance();
				if (tm != null) {
					tm.recordModuleEvent("Antiban", "SUPPRESSED_CONFLICTING_BLOCK_PLACE",
							"Blocked block place after attack/interact/use/release/dig/inventory (MultiActions/PacketOrder)", null);
				}
				return true;
			}

			boolean isMain = blockPacket.getHand() == Hand.MAIN_HAND;
			net.minecraft.util.hit.BlockHitResult hitNow = blockPacket.getBlockHitResult();

			suppressedActionInTick.set(false);
			actionSentInTick.set(true);
			blockActionSentInTick.set(true);
			if (isMain) {
				blockPlaceMainSentInTick.set(true);
				lastPlaceMainHit = hitNow;
			} else {
				blockPlaceOffSentInTick.set(true);
				lastPlaceOffHit = hitNow;
			}
			return false;
		}

		if (packet instanceof PlayerInteractItemC2SPacket itemPacket) {
			boolean isMain = itemPacket.getHand() == Hand.MAIN_HAND;
			if (!canUseItemNow(itemPacket.getHand())) {
				suppressedActionInTick.set(true);
				Recorder tm = Recorder.getInstance();
				if (tm != null) {
					tm.recordModuleEvent("Antiban", "SUPPRESSED_USE_ITEM_AFTER_ATTACK",
							"Blocked conflicting or duplicate item use in same tick (PacketOrder)", null);
				}
				return true;
			}

			suppressedActionInTick.set(false);
			actionSentInTick.set(true);
			InputContext.recordHandUse(itemPacket.getHand());
			if (isMain) {
				mainItemFallsThrough = false;
				useItemMainSentInTick.set(true);
			} else {
				useItemOffSentInTick.set(true);
			}
			return false;
		}

		if (packet instanceof PlayerActionC2SPacket actionPacket) {
			PlayerActionC2SPacket.Action action = actionPacket.getAction();
			if (action == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK ||
					action == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) {
				if (!canStartStopDigNow()) {
					suppressedActionInTick.set(true);
					Recorder tm = Recorder.getInstance();
					if (tm != null) {
						tm.recordModuleEvent("Antiban", "SUPPRESSED_CONFLICTING_BLOCK_DIG",
								"Blocked invalid/conflicting dig (" + action.name() + ")", null);
					}
					return true;
				}
				suppressedActionInTick.set(false);
				actionSentInTick.set(true);
				blockActionSentInTick.set(true);
				diggingSentInTick.set(true);
				digStartStopSentInTick.set(true);
				pendingDigSwing.set(true);
			} else if (action == PlayerActionC2SPacket.Action.STAB) {
				if (!canAttackPacketNow() || !isHoldingPiercingWeapon()) {
					suppressedActionInTick.set(true);
					Recorder tm = Recorder.getInstance();
					if (tm != null) {
						tm.recordModuleEvent("Antiban", "SUPPRESSED_CONFLICTING_STAB",
								"Blocked spear stab after conflicting action (MultiActions/PacketOrder)", null);
					}
					return true;
				}
				suppressedActionInTick.set(false);
				actionSentInTick.set(true);
				attackSentInTick.set(true);
				pendingAttackSwing.set(true);
			} else if (action == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM) {
				actionSentInTick.set(true);
				releasingSentInTick.set(true);
			} else if (action == PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK) {
				// Never set digStartStopSentInTick or blockActionSentInTick here:
				// vanilla attackBlock() sends ABORT_DESTROY_BLOCK(oldPos, newFace) immediately
				// followed by START_DESTROY_BLOCK(newPos, newFace), which PositionBreakB requires.
				actionSentInTick.set(true);
				diggingSentInTick.set(true);
			} else if (action == PlayerActionC2SPacket.Action.DROP_ITEM
					|| action == PlayerActionC2SPacket.Action.DROP_ALL_ITEMS) {
				if (!canDropNow()) {
					suppressedActionInTick.set(true);
					return true;
				}
				dropInputPending = false;
				suppressedActionInTick.set(false);
				pendingDropSwing.set(true);
				actionSentInTick.set(true);
				droppingSentInTick.set(true);
			} else if (action == PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
				if (!canSwapHandsNow()) {
					suppressedActionInTick.set(true);
					return true;
				}
				swapHandsInputPending = false;
				suppressedActionInTick.set(false);
				actionSentInTick.set(true);
			} else {
				actionSentInTick.set(true);
			}
			return false;
		}

		if (packet instanceof HandSwingC2SPacket swingPacket) {
			if (movementSentInTick.get() || tickEnded.get() || !inTick.get()) {
				Recorder tm = Recorder.getInstance();
				if (tm != null) {
					tm.recordModuleEvent("Antiban", "SUPPRESSED_POST_MOVEMENT_SWING",
							"Blocked post-movement hand swing", null);
				}
				return true;
			}
			boolean required = (pendingAttackSwing.get() && swingPacket.getHand() == Hand.MAIN_HAND)
					|| (pendingDropSwing.get() && swingPacket.getHand() == Hand.MAIN_HAND)
					|| (pendingDigSwing.get() && swingPacket.getHand() == Hand.MAIN_HAND)
					|| pendingUseSwing == swingPacket.getHand();
			if (!required && (suppressedActionInTick.get() || (swingSentInTick.get() && !InputContext.isVanillaAttack()))) {
				Recorder tm = Recorder.getInstance();
				if (tm != null) {
					tm.recordModuleEvent("Antiban", "SUPPRESSED_ORPHAN_SWING",
							suppressedActionInTick.get() ? "Blocked orphan swing after suppressed action" : "Blocked duplicate swing in same tick", null);
				}
				return true;
			}
			if (swingPacket.getHand() == Hand.MAIN_HAND) {
				pendingAttackSwing.set(false);
				pendingDropSwing.set(false);
			}
			pendingDigSwing.set(false);
			if (pendingUseSwing == swingPacket.getHand()) pendingUseSwing = null;
			swingSentInTick.set(true);
			return false;
		}

		// Closing a screen is a lifecycle operation. Dropping it strands the server
		// in the old handler after the client has already closed it.
		if (packet instanceof CloseHandledScreenC2SPacket) return false;

		if (packet instanceof ClickSlotC2SPacket) {
			if (!canInventoryActionNow()) return true;
			if (inTick.get()) {
				actionSentInTick.set(true);
				inventoryActionSentInTick.set(true);
			}
			return false;
		}

		if (packet instanceof UpdateSelectedSlotC2SPacket slotPacket) {
			int slot = slotPacket.getSelectedSlot();
			if (lastSentSlot.get() == slot) {
				pendingSlot.set(-1);
				Recorder tm = Recorder.getInstance();
				if (tm != null) {
					tm.recordModuleEvent("Antiban", "SUPPRESSED_DUPLICATE_SLOT",
							"Blocked duplicate slot switch to " + slot, null);
				}
				return true;
			}

			boolean screenOpen = MinecraftClient.getInstance().currentScreen != null;
			if (screenOpen || actionSentInTick.get() || movementSentInTick.get() || tickEnded.get() || !inTick.get()) {
				pendingSlot.set(slot);
				Recorder tm = Recorder.getInstance();
				if (tm != null) {
					tm.recordModuleEvent("Antiban", "SUPPRESSED_POST_ACTION_SLOT",
							"Deferred slot change to " + slot + " (post-action/post-movement/screen)", null);
				}
				return true;
			}

			pendingSlot.set(-1);
			lastSentSlot.set(slot);
		}

		return false;
	}

	public static void flushPendingSlot() {
		if (!isSlotPending()) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.currentScreen != null || mc.player == null || mc.interactionManager == null) {
			return;
		}
		if (pendingSlot.getAndSet(-1) != -1) {
			// syncSelectedSlot cached the rejected send. Retry with the player's current selection.
			var manager = (dlindustries.uk.daduncs.mixin.ClientPlayerInteractionManagerAccessor) mc.interactionManager;
			manager.setLastSelectedSlot(lastSentSlot.get());
			manager.syncSlot();
		}
	}
}
