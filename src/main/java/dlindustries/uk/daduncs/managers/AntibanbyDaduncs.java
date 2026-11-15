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
	private static final AtomicBoolean swappingSentInTick = new AtomicBoolean(false);
	private static final AtomicBoolean pendingAttackSwing = new AtomicBoolean(false);
	private static final AtomicBoolean pendingDigSwing = new AtomicBoolean(false);
	private static final AtomicBoolean swingSentInTick = new AtomicBoolean(false);
	private static final AtomicBoolean suppressedActionInTick = new AtomicBoolean(false);
	private static volatile net.minecraft.util.hit.BlockHitResult lastPlaceMainHit = null;
	private static volatile net.minecraft.util.hit.BlockHitResult lastPlaceOffHit = null;
	private static final AtomicInteger pendingSlot = new AtomicInteger(-1);
	private static final AtomicInteger lastSentSlot = new AtomicInteger(-1);
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
		return actionSentInTick.get();
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

	public static boolean canSwapSlotNow() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return inTick.get()
				&& !actionSentInTick.get()
				&& !movementSentInTick.get()
				&& !tickEnded.get()
				&& mc.currentScreen == null;
	}

	public static boolean canAttackNow() {
		return inTick.get()
				&& !movementSentInTick.get()
				&& !tickEnded.get()
				&& !attackSentInTick.get()
				&& !blockActionSentInTick.get()
				&& !entityInteractSentInTick.get()
				&& !inventoryActionSentInTick.get()
				&& !releasingSentInTick.get()
				&& !diggingSentInTick.get()
				&& !useItemMainSentInTick.get()
				&& !useItemOffSentInTick.get()
				&& !isSlotPending();
	}

	public static boolean canBlockActionNow() {
		return inTick.get()
				&& !movementSentInTick.get()
				&& !tickEnded.get()
				&& !attackSentInTick.get()
				&& !entityInteractSentInTick.get()
				&& !inventoryActionSentInTick.get()
				&& !releasingSentInTick.get()
				&& !diggingSentInTick.get()
				&& !useItemMainSentInTick.get()
				&& !useItemOffSentInTick.get()
				&& !isSlotPending();
	}

	public static boolean canUseItemNow(Hand hand) {
		if (!inTick.get() || movementSentInTick.get() || tickEnded.get()
				|| inventoryActionSentInTick.get() || releasingSentInTick.get()
				|| diggingSentInTick.get() || isSlotPending()) {
			return false;
		}
		if (attackSentInTick.get() && !entityInteractSentInTick.get()) {
			return false;
		}
		return hand == Hand.MAIN_HAND ? !useItemMainSentInTick.get() : !useItemOffSentInTick.get();
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

	public static void onTickStart() {
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
		swappingSentInTick.set(false);
		pendingAttackSwing.set(false);
		pendingDigSwing.set(false);
		swingSentInTick.set(false);
		suppressedActionInTick.set(false);
	}

	public static void reset() {
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

		if (packet instanceof PlayerInteractEntityC2SPacket entityPacket) {
			final boolean[] isAttack = {false};
			entityPacket.handle(new PlayerInteractEntityC2SPacket.Handler() {
				@Override
				public void interact(Hand hand) {}

				@Override
				public void interactAt(Hand hand, net.minecraft.util.math.Vec3d pos) {}

				@Override
				public void attack() {
					isAttack[0] = true;
				}
			});

			if (isAttack[0]) {
				if (attackSentInTick.get()
						|| blockActionSentInTick.get()
						|| entityInteractSentInTick.get()
						|| inventoryActionSentInTick.get()
						|| releasingSentInTick.get()
						|| diggingSentInTick.get()
						|| useItemMainSentInTick.get()
						|| useItemOffSentInTick.get()) {
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
				if (blockActionSentInTick.get()
						|| inventoryActionSentInTick.get()
						|| releasingSentInTick.get()
						|| diggingSentInTick.get()
						|| useItemMainSentInTick.get()
						|| useItemOffSentInTick.get()) {
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
			if (attackSentInTick.get()
					|| entityInteractSentInTick.get()
					|| inventoryActionSentInTick.get()
					|| releasingSentInTick.get()
					|| diggingSentInTick.get()
					|| useItemMainSentInTick.get()
					|| useItemOffSentInTick.get()) {
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
			net.minecraft.util.hit.BlockHitResult hitPrev = isMain ? lastPlaceMainHit : lastPlaceOffHit;
			// Grim MultiPlace only flags a 2nd place in a tick if pos, face or cursor differ.
			// An identical repeat (vanilla double-click in one tick / airplace) is allowed.
			boolean identicalRepeat = hitPrev != null && hitNow != null
					&& hitPrev.getBlockPos().equals(hitNow.getBlockPos())
					&& hitPrev.getSide() == hitNow.getSide()
					&& hitPrev.getPos().equals(hitNow.getPos());
			if (!identicalRepeat && ((isMain && blockPlaceMainSentInTick.get()) || (!isMain && blockPlaceOffSentInTick.get()))) {
				suppressedActionInTick.set(true);
				Recorder tm = Recorder.getInstance();
				if (tm != null) {
					tm.recordModuleEvent("Antiban", "SUPPRESSED_DUPLICATE_BLOCK_PLACE",
							"Blocked duplicate same-hand block place in same tick", null);
				}
				return true;
			}

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
			if ((attackSentInTick.get() && !entityInteractSentInTick.get())
					|| inventoryActionSentInTick.get()
					|| releasingSentInTick.get()
					|| diggingSentInTick.get()
					|| (isMain && useItemMainSentInTick.get())
					|| (!isMain && useItemOffSentInTick.get())) {
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
			if (isMain) {
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
				if (attackSentInTick.get() || blockPlaceMainSentInTick.get() || blockPlaceOffSentInTick.get()
						|| digStartStopSentInTick.get() || entityInteractSentInTick.get()
						|| inventoryActionSentInTick.get() || releasingSentInTick.get()
						|| useItemMainSentInTick.get() || useItemOffSentInTick.get()) {
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
				if (attackSentInTick.get() || blockActionSentInTick.get() || entityInteractSentInTick.get()
						|| inventoryActionSentInTick.get() || releasingSentInTick.get() || diggingSentInTick.get()
						|| useItemMainSentInTick.get() || useItemOffSentInTick.get()
						|| isSlotPending() || !isHoldingPiercingWeapon()) {
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
				actionSentInTick.set(true);
				droppingSentInTick.set(true);
			} else if (action == PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
				if (droppingSentInTick.get() || inventoryActionSentInTick.get()
						|| attackSentInTick.get() || releasingSentInTick.get()
						|| entityInteractSentInTick.get() || blockPlaceMainSentInTick.get()
						|| blockPlaceOffSentInTick.get() || useItemMainSentInTick.get()
						|| useItemOffSentInTick.get() || diggingSentInTick.get()
						|| isSlotPending()) {
					suppressedActionInTick.set(true);
					return true;
				}
				suppressedActionInTick.set(false);
				actionSentInTick.set(true);
				swappingSentInTick.set(true);
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
					|| pendingDigSwing.get();
			if (!required && (suppressedActionInTick.get() || swingSentInTick.get())) {
				Recorder tm = Recorder.getInstance();
				if (tm != null) {
					tm.recordModuleEvent("Antiban", "SUPPRESSED_ORPHAN_SWING",
							suppressedActionInTick.get() ? "Blocked orphan swing after suppressed action" : "Blocked duplicate swing in same tick", null);
				}
				return true;
			}
			if (swingPacket.getHand() == Hand.MAIN_HAND) {
				pendingAttackSwing.set(false);
			}
			pendingDigSwing.set(false);
			swingSentInTick.set(true);
			return false;
		}

		if (packet instanceof ClickSlotC2SPacket || packet instanceof CloseHandledScreenC2SPacket) {
			if (inTick.get() && movementSentInTick.get()) {
				Recorder tm = Recorder.getInstance();
				if (tm != null) {
					tm.recordModuleEvent("Antiban", "SUPPRESSED_MIDTICK_INVENTORY_ACTION",
							"Blocked " + packet.getClass().getSimpleName() + " between PlayerMove and ClientTickEnd (PacketOrderO)", null);
				}
				return true;
			}
			if (inTick.get() && (attackSentInTick.get() || blockActionSentInTick.get()
					|| entityInteractSentInTick.get() || diggingSentInTick.get() || droppingSentInTick.get())) {
				Recorder tm = Recorder.getInstance();
				if (tm != null) {
					tm.recordModuleEvent("Antiban", "SUPPRESSED_CONFLICTING_INVENTORY_ACTION",
							"Blocked " + packet.getClass().getSimpleName() + " in same tick as combat/block action", null);
				}
				return true;
			}
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
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.currentScreen != null) {
			return;
		}
		int pending = pendingSlot.getAndSet(-1);
		if (pending != -1) {
			if (lastSentSlot.get() == pending) {
				return;
			}
			if (mc.player != null && mc.getNetworkHandler() != null) {
				mc.player.getInventory().setSelectedSlot(pending);
				mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(pending));
				lastSentSlot.set(pending);
				Recorder tm = Recorder.getInstance();
				if (tm != null) {
					tm.recordModuleEvent("Antiban", "FLUSHED_PENDING_SLOT",
							"Flushed deferred slot change to " + pending + " at tick start", null);
				}
			}
		}
	}
}
