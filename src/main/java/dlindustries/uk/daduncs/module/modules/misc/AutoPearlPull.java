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

import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bind;
import dlindustries.uk.daduncs.module.setting.mode;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.KeyUtils;
import dlindustries.uk.daduncs.utils.WorldUtils;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public final class AutoPearlPull extends Module implements TickListener {

	public enum Mode {
		Puller, Activator
	}

	private final mode<Mode> mode = new mode<>(EncryptedString.of("Mode"), Mode.Puller, Mode.class);
	private final bind activate = new bind(EncryptedString.of("Activate Key"), -1, false);
	private final number port = new number(EncryptedString.of("Port"), 10000, 60000, 25599, 1);

	private DatagramSocket socket;
	private Thread listener;
	private volatile boolean pull;
	private boolean wasDown;

	public AutoPearlPull() {
		super(EncryptedString.of("AutoPearlPull"), EncryptedString.of("Helps you pull your pearl stasis from a different account from a keybind."), -1, Category.MISC);
		addSettings(mode, activate, port);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		pull = false;
		wasDown = false;

		if (mode.isMode(Mode.Puller))
			listen();

		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);

		if (socket != null) {
			socket.close();
			socket = null;
		}

		listener = null;
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.world == null) return;

		if (mode.isMode(Mode.Activator)) {
			boolean down = activate.getKey() != -1 && KeyUtils.isKeyPressed(activate.getKey());

			if (down && !wasDown)
				send();
			wasDown = down;
			return;
		}

		if (!pull)
			return;

		if (mc.currentScreen != null || mc.player.isUsingItem() || !AntibanbyDaduncs.canBlockActionNow() || AntibanbyDaduncs.isSlotPending())
			return;

		if (mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			if (WorldUtils.placeBlock(hit, true)) {
				pull = false;
			}
		} else {
			pull = false;
		}
	}

	private void listen() {
		try {
			socket = new DatagramSocket(port.getValueInt());
		} catch (Exception ignored) {
			return;
		}

		listener = new Thread(() -> {
			byte[] buffer = new byte[16];

			while (socket != null && !socket.isClosed()) {
				try {
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
					socket.receive(packet);
					pull = true;
				} catch (Exception ignored) {
					return;
				}
			}
		}, "pearl");

		listener.setDaemon(true);
		listener.start();
	}

	private void send() {
		try (DatagramSocket out = new DatagramSocket()) {
			out.setBroadcast(true);

			byte[] data = {1};
			out.send(new DatagramPacket(data, data.length, InetAddress.getByName("255.255.255.255"), port.getValueInt()));
		} catch (Exception ignored) {
		}

	}
}
