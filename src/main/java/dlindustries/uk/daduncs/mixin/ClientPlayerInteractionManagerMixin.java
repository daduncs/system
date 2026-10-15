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
			return;
		}

		if (dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isAttackSent()) {
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

	@Inject(method = "interactItem", at = @At("TAIL"))
	private void onInteractItemTail(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		if (!this.sr$use) {
			return;
		}
		player.setYaw(this.sr$y);
		player.setPitch(this.sr$p);
		player.lastYaw = this.sr$ly;
		player.lastPitch = this.sr$lp;
		this.sr$use = false;
	}
}
