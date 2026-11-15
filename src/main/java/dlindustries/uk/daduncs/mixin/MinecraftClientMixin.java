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
import dlindustries.uk.daduncs.utils.MouseSimulation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.client.world.ClientWorld;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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

	@Inject(method = "tick", at = @At("HEAD"))
	private void onTick(CallbackInfo ci) {
		dlindustries.uk.daduncs.module.modules.misc.AutoReconnect.onClientTick();

		if (world != null) {
			dlindustries.uk.daduncs.managers.Recorder tm = dlindustries.uk.daduncs.managers.Recorder.getInstance();
			if (tm != null) {
				tm.recordTickStart();
			}

			dlindustries.uk.daduncs.managers.AntibanbyDaduncs.onTickStart();

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

	@Inject(method = "onResolutionChanged", at = @At("HEAD"))
	private void onResolutionChanged(CallbackInfo ci) {
		EventManager.fire(new ResolutionListener.ResolutionEvent(this.window));
	}

	@Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
	private void onItemUse(CallbackInfo ci) {
		if (dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isAttackSent()
				|| dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isDiggingSent()
				|| dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isReleasingSent()) {
			ci.cancel();
			return;
		}

		ItemUseListener.ItemUseEvent event = new ItemUseListener.ItemUseEvent();

		EventManager.fire(event);
		if (event.isCancelled()) ci.cancel();

		if (MouseSimulation.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
			MouseSimulation.mouseButtons.put(GLFW.GLFW_MOUSE_BUTTON_RIGHT, false);
			ci.cancel();
		}
	}

	@Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
	private void onAttack(CallbackInfoReturnable<Boolean> cir) {
		MinecraftClient client = (MinecraftClient) (Object) this;
		boolean entityOrSpear = (client.crosshairTarget != null && client.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.ENTITY)
				|| (client.player != null && client.player.getMainHandStack().contains(net.minecraft.component.DataComponentTypes.PIERCING_WEAPON));
		if (dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isAttackSent()
				|| dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isBlockPlaceSent()
				|| dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isDigStartStopSent()
				|| dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isReleasingSent()
				|| dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isUseItemSent()
				|| (entityOrSpear && dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isDiggingSent())) {
			cir.setReturnValue(false);
			return;
		}

		AttackListener.AttackEvent event = new AttackListener.AttackEvent();

		EventManager.fire(event);
		if (event.isCancelled()) {
			cir.setReturnValue(false);
			return;
		}

		if (MouseSimulation.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_1)) {
			MouseSimulation.mouseButtons.put(GLFW.GLFW_MOUSE_BUTTON_1, false);
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
	private void onBlockBreaking(boolean breaking, CallbackInfo ci) {
		if (!breaking) {
			return;
		}
		if (dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isAttackSent()
				|| dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isEntityInteractSent()
				|| dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isBlockPlaceSent()
				|| dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isUseItemSent()
				|| dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isReleasingSent()
				|| dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isDigStartStopSent()) {
			ci.cancel();
			return;
		}

		BlockBreakingListener.BlockBreakingEvent event = new BlockBreakingListener.BlockBreakingEvent();

		EventManager.fire(event);
		if (event.isCancelled()) ci.cancel();

		if (MouseSimulation.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_1)) {
			MouseSimulation.mouseButtons.put(GLFW.GLFW_MOUSE_BUTTON_1, false);
			ci.cancel();
		}
	}

	@Inject(method = "stop", at = @At("HEAD"))
	private void onClose(CallbackInfo ci) {
		System.INSTANCE.getProfileManager().saveProfile();
	}
}
