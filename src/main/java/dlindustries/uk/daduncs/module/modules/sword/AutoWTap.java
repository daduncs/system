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

package dlindustries.uk.daduncs.module.modules.sword;

import dlindustries.uk.daduncs.event.events.PacketSendListener;
import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.imixin.IKeyBinding;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.module.setting.range;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.MathUtils;
import dlindustries.uk.daduncs.utils.TimerUtils;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public final class AutoWTap extends Module implements PacketSendListener, TickListener {
	private final range delay = new range(EncryptedString.of("Delay"), 20, 200, 1, 50, 80);
	private final number chance = new number(EncryptedString.of("Chance"), 0, 100, 100, 1);
	private final bool inAir = new bool(EncryptedString.of("In Air"), false)
			.setDescription(EncryptedString.of("Whether it should W tap in air"));
	private final bool humanize = new bool(EncryptedString.of("Humanize"), true)
			.setDescription(EncryptedString.of("randomizes timings not that its gonna bipass shi"));

	private final TimerUtils tapTimer = new TimerUtils();
	private boolean tapScheduled = false;
	private boolean isTapping = false;
	private int tapTicksRemaining = 0;
	private int currentDelay = 50;

	public AutoWTap() {
		super(EncryptedString.of("Auto WTap"),
				EncryptedString.of("Automatically W Taps for you so the opponent takes more knockback"),
				-1,
				Category.SWORD);
		addSettings(delay, chance, inAir, humanize);
	}

	@Override
	public void onEnable() {
		eventManager.add(PacketSendListener.class, this);
		eventManager.add(TickListener.class, this);
		resetState();
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(PacketSendListener.class, this);
		eventManager.remove(TickListener.class, this);
		resetState();
		super.onDisable();
	}

	private void resetState() {
		if (mc.options != null) {
			((IKeyBinding) mc.options.forwardKey).resetPressed();
		}
		tapScheduled = false;
		isTapping = false;
		tapTicksRemaining = 0;
	}

	private boolean isHoldingForward() {
		if (mc.options == null) return false;
		return ((IKeyBinding) mc.options.forwardKey).isActuallyPressed();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.world == null) {
			resetState();
			return;
		}

		if (mc.currentScreen != null) {
			resetState();
			return;
		}

		boolean forwardDown = isHoldingForward();

		if (!forwardDown) {
			if (isTapping) {
				((IKeyBinding) mc.options.forwardKey).resetPressed();
				isTapping = false;
			}
			tapScheduled = false;
			return;
		}

		if (tapScheduled) {
			mc.options.forwardKey.setPressed(false);
			isTapping = true;
			tapScheduled = false;
			tapTicksRemaining = humanize.getValue() ? (MathUtils.randomInt(1, 10) <= 8 ? 1 : 2) : 1;
			tapTimer.reset();
			return;
		}

		if (isTapping) {
			tapTicksRemaining--;
			if (tapTicksRemaining <= 0 && tapTimer.delay(currentDelay)) {
				((IKeyBinding) mc.options.forwardKey).resetPressed();
				isTapping = false;
			}
		}
	}

	@Override
	public void onPacketSend(PacketSendEvent event) {
		if (mc.player == null || mc.currentScreen != null) return;
		if (!(event.packet instanceof PlayerInteractEntityC2SPacket packet))
			return;

		packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
			@Override
			public void interact(Hand hand) {}

			@Override
			public void interactAt(Hand hand, Vec3d pos) {}

			@Override
			public void attack() {
				if (mc.player.isUsingItem() || mc.player.hasVehicle() || mc.player.isClimbing()) return;
				if (!isHoldingForward()) return;
				if (!mc.player.isSprinting()) return;

				if (!inAir.getValue() && !mc.player.isOnGround())
					return;

				if (mc.options.jumpKey.isPressed() && !inAir.getValue())
					return;

				if (MathUtils.randomInt(1, 100) > chance.getValueInt())
					return;

				currentDelay = delay.getRandomValueInt();
				tapScheduled = true;
			}
		});
	}
}