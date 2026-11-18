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
import dlindustries.uk.daduncs.event.EventManager;
import dlindustries.uk.daduncs.event.events.*;
import dlindustries.uk.daduncs.module.modules.misc.FastPlace;
import dlindustries.uk.daduncs.module.modules.crystal.UtilitySwapper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.client.world.ClientWorld;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import dlindustries.uk.daduncs.managers.InputContext;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
	@Shadow
	@Nullable
	public ClientWorld world;

	@Shadow
	@Final
	private Window window;

	@Unique
	private net.minecraft.client.network.ClientPlayerEntity system$previousPlayer;

	@Unique
	private UtilitySwapper system$utilitySwapper() {
		return System.INSTANCE == null || System.INSTANCE.getModuleManager() == null ? null
				: System.INSTANCE.getModuleManager().getModule(UtilitySwapper.class);
	}

	@WrapOperation(method = "handleInputEvents", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/option/KeyBinding;isPressed()Z"))
	private boolean readHeldInput(KeyBinding key, Operation<Boolean> original) {
		if (original.call(key)) return true;
		if (key != ((MinecraftClient) (Object) this).options.useKey) return false;
		var swapper = system$utilitySwapper();
		return swapper != null && swapper.holdsShieldUse();
	}

	@WrapOperation(method = "handleInputEvents", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/MinecraftClient;doItemUse()V"))
	private void useEquippedItem(MinecraftClient client, Operation<Void> original) {
		InputContext.vanillaUse(() -> original.call(client));
	}

	@Inject(method = "onWindowFocusChanged", at = @At("HEAD"))
	private void updateInputFocus(boolean focused, CallbackInfo ci) {
		if (!focused) dlindustries.uk.daduncs.utils.KeyUtils.clearScanCodes();
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void onTick(CallbackInfo ci) {
		dlindustries.uk.daduncs.module.modules.misc.AutoReconnect.onClientTick();
		MinecraftClient client = (MinecraftClient) (Object) this;
		var player = world == null ? null : client.player;
		if (player != system$previousPlayer) {
			system$previousPlayer = player;
			dlindustries.uk.daduncs.managers.SlotManager.clear();
			dlindustries.uk.daduncs.utils.rotation.SilentAim.onPlayerChanged();
			dlindustries.uk.daduncs.utils.rotation.SilentRot.stop();
			if (System.INSTANCE != null && System.INSTANCE.getModuleManager() != null) {
				System.INSTANCE.getModuleManager().onPlayerChanged();
			}
		}

		if (world != null) {
			dlindustries.uk.daduncs.managers.Recorder tm = dlindustries.uk.daduncs.managers.Recorder.getInstance();
			if (tm != null) {
				tm.recordTickStart();
			}

			boolean gameplay = System.INSTANCE != null && System.INSTANCE.getModuleManager() != null
					&& System.INSTANCE.getModuleManager().hasGameplayModulesEnabled();
			dlindustries.uk.daduncs.managers.AntibanbyDaduncs.onTickStart(gameplay);
			if (gameplay && client.currentScreen == null && client.player != null && !client.player.isSpectator()) {
				AntibanbyDaduncs.reserveInventoryKeys(
						((KeyBindingAccessor) client.options.swapHandsKey).getTimesPressed() > 0,
						((KeyBindingAccessor) client.options.dropKey).getTimesPressed() > 0);
			}

			TickListener.TickEvent event = new TickListener.TickEvent();

			EventManager.fire(event);
		} else {
			dlindustries.uk.daduncs.managers.AntibanbyDaduncs.reset();
		}
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void onTickReturn(CallbackInfo ci) {
		if (world == null)
			return;

		dlindustries.uk.daduncs.managers.AntibanbyDaduncs.onTickEnd();

		dlindustries.uk.daduncs.managers.Recorder tm = dlindustries.uk.daduncs.managers.Recorder.getInstance();
		if (tm != null) {
			tm.recordTickEnd();
		}

		FastPlace fastPlace = System.INSTANCE.getModuleManager().getModule(FastPlace.class);
		if (fastPlace != null)
			fastPlace.apply();
	}

	@com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "handleInputEvents",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V"))
	private void beforeInputScreen(MinecraftClient client, net.minecraft.client.gui.screen.Screen screen,
			com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
		if (screen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen && System.INSTANCE != null
				&& System.INSTANCE.getModuleManager() != null) {
			var dhand = System.INSTANCE.getModuleManager().getModule(dlindustries.uk.daduncs.module.modules.crystal.DHand.class);
			if (dhand != null && dhand.deferInventoryScreen(screen)) return;
		}
		original.call(client, screen);
	}

	@Inject(method = "onResolutionChanged", at = @At("HEAD"))
	private void onResolutionChanged(CallbackInfo ci) {
		EventManager.fire(new ResolutionListener.ResolutionEvent(this.window));
	}

	@Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
	private void onItemUse(CallbackInfo ci) {
		if (dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isInventoryOpenReserved()
				|| AntibanbyDaduncs.hasPendingInventoryKey()
				|| dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isSlotPending()
				|| dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isInventoryActionSent()
				|| dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isDiggingSent()
				|| dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isReleasingSent()) {
			ci.cancel();
			return;
		}

		MinecraftClient client = (MinecraftClient) (Object) this;
		if (client.crosshairTarget != null
				&& client.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.ENTITY
				&& !dlindustries.uk.daduncs.managers.AntibanbyDaduncs.canInteractEntityNow(net.minecraft.util.Hand.MAIN_HAND)
				&& !dlindustries.uk.daduncs.managers.AntibanbyDaduncs.canUseItemNow(net.minecraft.util.Hand.MAIN_HAND)
				&& !dlindustries.uk.daduncs.managers.AntibanbyDaduncs.canUseItemNow(net.minecraft.util.Hand.OFF_HAND)) {
			ci.cancel();
			return;
		}

		ItemUseListener.ItemUseEvent event = new ItemUseListener.ItemUseEvent();

		EventManager.fire(event);
		if (event.isCancelled()) ci.cancel();
	}

	@com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "handleInputEvents",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;wasPressed()Z"))
	private boolean preserveQueuedInventoryKey(net.minecraft.client.option.KeyBinding key,
			com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original) {
		MinecraftClient client = (MinecraftClient) (Object) this;
		if (client.player != null && !client.player.isSpectator()) {
			if (key == client.options.swapHandsKey && !AntibanbyDaduncs.canSwapHandsNow()) return false;
			if (key == client.options.dropKey && !AntibanbyDaduncs.canDropNow()) return false;
		}
		return original.call(key);
	}

	@Redirect(method = "handleInputEvents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;doAttack()Z"))
	private boolean handleQueuedAttack(MinecraftClient client) {
		return InputContext.vanillaAttack(() -> ((MinecraftClientAccessor) client).invokeDoAttack());
	}

	@Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
	private void onAttack(CallbackInfoReturnable<Boolean> cir) {
		MinecraftClient client = (MinecraftClient) (Object) this;
		boolean holdingSpear = client.player != null
				&& client.player.getMainHandStack().contains(net.minecraft.component.DataComponentTypes.PIERCING_WEAPON);
		net.minecraft.util.hit.HitResult.Type targetType = client.crosshairTarget != null
				? client.crosshairTarget.getType()
				: net.minecraft.util.hit.HitResult.Type.MISS;

		boolean allowed;
		if (holdingSpear || targetType == net.minecraft.util.hit.HitResult.Type.ENTITY) {
			allowed = dlindustries.uk.daduncs.managers.AntibanbyDaduncs.canAttackPacketNow();
		} else if (targetType == net.minecraft.util.hit.HitResult.Type.BLOCK) {
			allowed = dlindustries.uk.daduncs.managers.AntibanbyDaduncs.canStartStopDigNow();
		} else {
			allowed = dlindustries.uk.daduncs.managers.AntibanbyDaduncs.canSwingAirNow();
		}

		if (!allowed) {
			cir.setReturnValue(false);
			return;
		}

		AttackListener.AttackEvent event = new AttackListener.AttackEvent();

		EventManager.fire(event);
		if (event.isCancelled()) cir.setReturnValue(false);
	}

	@Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
	private void onBlockBreaking(boolean breaking, CallbackInfo ci) {
		if (!breaking) {
			return;
		}
		if (!dlindustries.uk.daduncs.managers.AntibanbyDaduncs.canStartStopDigNow()) {
			ci.cancel();
			return;
		}

		BlockBreakingListener.BlockBreakingEvent event = new BlockBreakingListener.BlockBreakingEvent();

		EventManager.fire(event);
		if (event.isCancelled()) ci.cancel();
	}

	@Inject(method = "stop", at = @At("HEAD"))
	private void onClose(CallbackInfo ci) {
		System.INSTANCE.getProfileManager().saveProfile();
	}
}
