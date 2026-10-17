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

import com.mojang.authlib.GameProfile;
import dlindustries.uk.daduncs.event.EventManager;
import dlindustries.uk.daduncs.event.events.MovementPacketListener;
import dlindustries.uk.daduncs.event.events.PlayerTickListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin extends AbstractClientPlayerEntity {

	@Shadow
	@Final
	protected MinecraftClient client;

	@Unique
	private boolean sr$save;
	@Unique
	private float sr$y, sr$p, sr$ly, sr$lp;
	@Unique
	private boolean sr$mv;
	@Unique
	private float sr$my, sr$mp, sr$mly, sr$mlp;
	@Unique
	private float sr$ry, sr$rp;
	@Unique
	private float sr$by, sr$hy, sr$lby, sr$lhy;

	public ClientPlayerEntityMixin(ClientWorld world, GameProfile profile) {
		super(world, profile);
	}

	@Inject(method = "tickMovement", at = @At("HEAD"))
	private void onTickMovementHead(CallbackInfo ci) {
		dlindustries.uk.daduncs.module.modules.misc.Fly.beforeMove();

		if (!dlindustries.uk.daduncs.utils.rotation.SilentRot.on()) {
			return;
		}
		ClientPlayerEntity p = (ClientPlayerEntity) (Object) this;
		this.sr$my = p.getYaw();
		this.sr$mp = p.getPitch();
		this.sr$mly = p.lastYaw;
		this.sr$mlp = p.lastPitch;
		this.sr$ry = p.renderYaw;
		this.sr$rp = p.renderPitch;
		this.sr$by = p.bodyYaw;
		this.sr$hy = p.headYaw;
		this.sr$lby = p.lastBodyYaw;
		this.sr$lhy = p.lastHeadYaw;
		this.sr$mv = true;

		dlindustries.uk.daduncs.utils.rotation.SilentRot.beginMove(this.sr$my);
		float wy = dlindustries.uk.daduncs.utils.rotation.SilentRot.yaw();
		float wp = dlindustries.uk.daduncs.utils.rotation.SilentRot.pitch();
		p.setYaw(wy);
		p.setPitch(wp);
		p.lastYaw = wy;
		p.lastPitch = wp;
	}

	@Inject(method = "tickMovement", at = @At("TAIL"))
	private void onTickMovementTail(CallbackInfo ci) {
		if (!this.sr$mv) {
			return;
		}
		ClientPlayerEntity p = (ClientPlayerEntity) (Object) this;
		p.setYaw(this.sr$my);
		p.setPitch(this.sr$mp);
		p.lastYaw = this.sr$mly;
		p.lastPitch = this.sr$mlp;
		p.lastRenderYaw = this.sr$ry;
		p.lastRenderPitch = this.sr$rp;
		p.renderYaw = this.sr$ry + (this.sr$my - this.sr$ry) * 0.5f;
		p.renderPitch = this.sr$rp + (this.sr$mp - this.sr$rp) * 0.5f;
		p.bodyYaw = this.sr$by;
		p.headYaw = this.sr$hy;
		p.lastBodyYaw = this.sr$lby;
		p.lastHeadYaw = this.sr$lhy;
		dlindustries.uk.daduncs.utils.rotation.SilentRot.endMove();
		this.sr$mv = false;
	}

	@Inject(method = "sendMovementPackets", at = @At("HEAD"))
	private void onSendMovementPacketsHead(CallbackInfo ci) {
		EventManager.fire(new MovementPacketListener.MovementPacketEvent());
		if (!dlindustries.uk.daduncs.utils.rotation.SilentRot.on()) {
			return;
		}
		ClientPlayerEntity p = (ClientPlayerEntity) (Object) this;
		this.sr$y = p.getYaw();
		this.sr$p = p.getPitch();
		this.sr$ly = p.lastYaw;
		this.sr$lp = p.lastPitch;
		this.sr$save = true;
		float wy = dlindustries.uk.daduncs.utils.rotation.SilentRot.yaw();
		float wp = dlindustries.uk.daduncs.utils.rotation.SilentRot.pitch();
		p.setYaw(wy);
		p.setPitch(wp);
		p.lastYaw = wy;
		p.lastPitch = wp;
	}

	@Inject(method = "sendMovementPackets", at = @At("TAIL"))
	private void onSendMovementPacketsTail(CallbackInfo ci) {
		if (this.sr$save) {
			ClientPlayerEntity p = (ClientPlayerEntity) (Object) this;
			p.setYaw(this.sr$y);
			p.setPitch(this.sr$p);
			p.lastYaw = this.sr$ly;
			p.lastPitch = this.sr$lp;
			this.sr$save = false;
		}

		if (dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isAttackSent() &&
				!dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isMovementSent()) {
			ClientPlayerEntity p = (ClientPlayerEntity) (Object) this;
			if (p.networkHandler != null) {
				p.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly(
						p.isOnGround(), p.horizontalCollision
				));
			}
		}
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void onPlayerTick(CallbackInfo ci) {
		EventManager.fire(new PlayerTickListener.PlayerTickEvent());
	}
}
