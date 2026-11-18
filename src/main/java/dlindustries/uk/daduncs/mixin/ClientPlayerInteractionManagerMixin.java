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

package dlindustries.uk.daduncs.mixin;

import dlindustries.uk.daduncs.System;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import net.minecraft.entity.Entity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.type.PiercingWeaponComponent;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dlindustries.uk.daduncs.module.modules.misc.NoBreakDelay;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
	@Shadow
	private int blockBreakingCooldown;

	@Unique
	private net.minecraft.item.Item sr$usedItem;
	@Unique
	private boolean sr$use;
	@Unique
	private float sr$y, sr$p, sr$ly, sr$lp;

	@Redirect(method = "updateBlockBreakingProgress",
			at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;blockBreakingCooldown:I", opcode = Opcodes.GETFIELD, ordinal = 0))
	public int updateBlockBreakingProgress(ClientPlayerInteractionManager clientPlayerInteractionManager) {
		int cooldown = this.blockBreakingCooldown;
		return System.INSTANCE.getModuleManager().getModule(NoBreakDelay.class).isEnabled() ? 0 : cooldown;
	}

	@Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
	private void onInteractItemHead(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		if (player == null) {
			sr$usedItem = null;
			return;
		}

		sr$usedItem = player.getStackInHand(hand).getItem();
		if (!AntibanbyDaduncs.canUseItemNow(hand)) {
			cir.setReturnValue(ActionResult.PASS);
			return;
		}

		if (this.sr$use) {
			return;
		}

		if (!dlindustries.uk.daduncs.utils.rotation.SilentRot.on()) {
			return;
		}

		float wy = dlindustries.uk.daduncs.utils.rotation.SilentRot.yaw();
		float wp = dlindustries.uk.daduncs.utils.rotation.SilentRot.pitch();

		this.sr$y = player.getYaw();
		this.sr$p = player.getPitch();
		this.sr$ly = player.lastYaw;
		this.sr$lp = player.lastPitch;
		this.sr$use = true;

		player.setYaw(wy);
		player.setPitch(wp);
		player.lastYaw = wy;
		player.lastPitch = wp;
	}

	@Inject(method = {"attackBlock", "updateBlockBreakingProgress"}, at = @At("HEAD"), cancellable = true)
	private void beforeDig(net.minecraft.util.math.BlockPos pos, net.minecraft.util.math.Direction direction,
			CallbackInfoReturnable<Boolean> cir) {
		if (!AntibanbyDaduncs.canStartStopDigNow()) cir.setReturnValue(false);
	}

	@Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
	private void beforeSlotClick(int syncId, int slot, int button, SlotActionType type,
			PlayerEntity player, CallbackInfo ci) {
		if (!AntibanbyDaduncs.canInventoryActionNow()) ci.cancel();
	}

	@Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
	private void beforeEntityAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
		if (net.minecraft.client.MinecraftClient.getInstance().currentScreen != null) {
			ci.cancel();
			return;
		}
		if (!AntibanbyDaduncs.canAttackPacketNow()) ci.cancel();
	}

	@Inject(method = "attackWithPiercingWeapon", at = @At("HEAD"), cancellable = true)
	private void beforeSpearAttack(PiercingWeaponComponent piercing, CallbackInfo ci) {
		if (net.minecraft.client.MinecraftClient.getInstance().currentScreen != null) {
			ci.cancel();
			return;
		}
		if (!AntibanbyDaduncs.canAttackPacketNow()) ci.cancel();
	}

	@Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
	private void beforeBlockInteraction(ClientPlayerEntity player, Hand hand, BlockHitResult hit,
			CallbackInfoReturnable<ActionResult> cir) {
		// Vanilla doItemUse refuses block use while a previous mining action is active.
		// Check before slot synchronization or predicted world changes.
		if (((ClientPlayerInteractionManager) (Object) this).isBreakingBlock()) {
			cir.setReturnValue(ActionResult.FAIL);
			return;
		}
		if (!AntibanbyDaduncs.canInteractBlockNow(hand, hit)) cir.setReturnValue(ActionResult.FAIL);
	}

	@Inject(method = "interactEntity", at = @At("HEAD"), cancellable = true)
	private void beforeEntityInteraction(PlayerEntity player, Entity target, Hand hand,
			CallbackInfoReturnable<ActionResult> cir) {
		if (!AntibanbyDaduncs.canInteractEntityNow(hand)) cir.setReturnValue(ActionResult.FAIL);
	}

	@Inject(method = "interactEntityAtLocation", at = @At("HEAD"), cancellable = true)
	private void beforeEntityInteractionAt(PlayerEntity player, Entity target, EntityHitResult hit, Hand hand,
			CallbackInfoReturnable<ActionResult> cir) {
		if (!AntibanbyDaduncs.canInteractEntityNow(hand)) cir.setReturnValue(ActionResult.FAIL);
	}

	@Inject(method = "interactBlock", at = @At("RETURN"))
	private void afterBlockInteraction(ClientPlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
		AntibanbyDaduncs.onInteractionResult(hand, cir.getReturnValue(), false);
	}

	@Inject(method = "interactEntity", at = @At("RETURN"))
	private void afterEntityInteraction(PlayerEntity player, Entity target, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		AntibanbyDaduncs.onInteractionResult(hand, cir.getReturnValue(), false);
	}

	@Inject(method = "interactEntityAtLocation", at = @At("RETURN"))
	private void afterEntityInteractionAt(PlayerEntity player, Entity target, EntityHitResult hit, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		AntibanbyDaduncs.onInteractionResult(hand, cir.getReturnValue(), false);
	}

	@Inject(method = "interactItem", at = @At("RETURN"))
	private void onInteractItemTail(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		AntibanbyDaduncs.onInteractionResult(hand, cir.getReturnValue(), true);
		if (sr$usedItem != null) {
			dlindustries.uk.daduncs.managers.InputContext.recordItemResult(hand, sr$usedItem.getRegistryEntry().registryKey(), cir.getReturnValue());
			sr$usedItem = null;
		}
		if (!this.sr$use) {
			return;
		}
		if (player != null) {
			player.setYaw(this.sr$y);
			player.setPitch(this.sr$p);
			player.lastYaw = this.sr$ly;
			player.lastPitch = this.sr$lp;
		}
		this.sr$use = false;
	}
}
