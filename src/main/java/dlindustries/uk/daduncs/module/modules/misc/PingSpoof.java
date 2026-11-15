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

package dlindustries.uk.daduncs.module.modules.misc;

import dlindustries.uk.daduncs.event.events.PacketReceiveListener;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.range;
import dlindustries.uk.daduncs.utils.EncryptedString;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class PingSpoof extends Module implements PacketReceiveListener {
	private final range ping = new range(EncryptedString.of("Ping"), 0, 1000, 1, 0, 600)
			.setDescription(EncryptedString.of("The ping you want to achieve"));

	private ScheduledExecutorService executor;
	private int delay;
	public PingSpoof() {
		super(EncryptedString.of("Ping Spoof"),
				EncryptedString.of("Holds back packets making the server think your internet connection is bad."), -1, Category.MISC);
		setTag(EncryptedString.of("BANNABLE"));
		addSettings(ping);
	}

	@Override
	public void onEnable() {
		eventManager.add(PacketReceiveListener.class, this);
		if (executor == null || executor.isShutdown()) {
			executor = Executors.newSingleThreadScheduledExecutor(r -> {
				Thread t = new Thread(r, "PingSpoof-Worker");
				t.setDaemon(true);
				return t;
			});
		}

		delay = ping.getRandomValueInt();
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(PacketReceiveListener.class, this);
		if (executor != null) {
			executor.shutdownNow();
			executor = null;
		}
		super.onDisable();
	}

	@Override
	public void onPacketReceive(PacketReceiveEvent event) {
		if (event.packet instanceof KeepAliveS2CPacket packet) {
			ScheduledExecutorService exec = this.executor;
			if (exec == null || exec.isShutdown()) return;

			long holdMs = Math.max(0, delay);
			delay = ping.getRandomValueInt();
			exec.schedule(() -> {
				if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null && mc.getNetworkHandler().getConnection().isOpen()) {
					mc.getNetworkHandler().getConnection().send(new KeepAliveC2SPacket(packet.getId()));
				}
			}, holdMs, TimeUnit.MILLISECONDS);

			event.cancel();
		}
	}
}
