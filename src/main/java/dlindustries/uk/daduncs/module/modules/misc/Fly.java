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
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec2f;

public final class Fly extends Module implements TickListener {

	private final number speed = new number(EncryptedString.of("Speed"), 0.1, 3, 0.6, 0.1);
	private final bool through = new bool(EncryptedString.of("Through Blocks"), true);

	private static Fly instance;

	public Fly() {
		super(EncryptedString.of("Fly"), EncryptedString.of("Lets you fly"), -1, Category.MISC);
		setTag(EncryptedString.of("BANNABLE"));

		addSettings(speed, through);
		instance = this;
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);

		if (mc.player != null) {
			boolean creativeOrSpectator = mc.player.isCreative() || mc.player.isSpectator();

			mc.player.getAbilities().flying = creativeOrSpectator && mc.player.getAbilities().flying;
			mc.player.getAbilities().allowFlying = creativeOrSpectator;
			mc.player.getAbilities().setFlySpeed(0.05f);
			mc.player.noClip = false;
			mc.player.setNoGravity(false);
			ghost(false);
		}

		super.onDisable();
	}

	public static void beforeMove() {
		if (instance != null && instance.isEnabled() && instance.through.getValue())
			instance.ghosting();
	}

	private void ghosting() {
		if (mc.player == null)
			return;

		mc.player.noClip = true;
		ghost(true);
	}

	private void ghost(boolean state) {
		if (!mc.isInSingleplayer() || mc.getServer() == null) return;

		ServerPlayerEntity player = mc.getServer().getPlayerManager().getPlayer(mc.player.getUuid());
		if (player != null)
			player.noClip = state;
	}

	@Override
	public void onTick() {
		if (mc.player == null)
			return;

		mc.player.getAbilities().allowFlying = true;
		mc.player.getAbilities().flying = true;

		Vec2f move = mc.player.input.getMovementInput();
		double yaw = Math.toRadians(mc.player.getYaw());
		double fast = speed.getValue();
		double x = 0;
		double z = 0;

		if (move.x != 0 || move.y != 0) {
			x = move.y * -Math.sin(yaw) + move.x * Math.cos(yaw);
			z = move.y * Math.cos(yaw) + move.x * Math.sin(yaw);

			double length = Math.sqrt(x * x + z * z);
			x = x / length * fast;
			z = z / length * fast;
		}

		double y = 0;
		if (mc.player.input.playerInput.jump())
			y = fast;
		else if (mc.player.input.playerInput.sneak())
			y = -fast;

		mc.player.setVelocity(x, y, z);

		mc.player.noClip = through.getValue();
		mc.player.setNoGravity(through.getValue());

		if (through.getValue()) {
			mc.player.fallDistance = 0;
			ghost(true);
		}
	}
}
