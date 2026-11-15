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

import dlindustries.uk.daduncs.event.EventManager;
import dlindustries.uk.daduncs.event.events.PacketReceiveListener;
import dlindustries.uk.daduncs.event.events.PacketSendListener;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.managers.Recorder;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public class ClientConnectionMixin {

	@Inject(method = "handlePacket", at = @At("HEAD"), cancellable = true)
	private static <T extends PacketListener> void onPacketReceive(Packet<T> packet, PacketListener listener, CallbackInfo ci) {
		AntibanbyDaduncs.onPacketReceive(packet);

		Recorder tm = Recorder.getInstance();
		if (tm != null) {
			tm.recordPacketReceive(packet);
		}

		PacketReceiveListener.PacketReceiveEvent event = new PacketReceiveListener.PacketReceiveEvent(packet);

		EventManager.fire(event);
		if (event.isCancelled()) ci.cancel();
	}

	@Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
	private void onPacketSend(Packet<?> packet, @Nullable ChannelFutureListener callback, boolean flush, CallbackInfo ci) {
		if (AntibanbyDaduncs.filterPacket(packet)) {
			ci.cancel();
			return;
		}

		Recorder tm = Recorder.getInstance();
		if (tm != null) {
			tm.recordPacket(packet);
		}

		PacketSendListener.PacketSendEvent event = new PacketSendListener.PacketSendEvent(packet);

		EventManager.fire(event);
		if (event.isCancelled()) ci.cancel();
	}
}
