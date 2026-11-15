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
import dlindustries.uk.daduncs.imixin.IKeyBinding;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.utils.EncryptedString;

public final class NoJumpDelay extends Module implements TickListener {
	public NoJumpDelay() {
		super(EncryptedString.of("No Jump Delay"),
				EncryptedString.of("Lets you jump faster, removing the delay"),
				-1,
				Category.MISC);
		setTag(EncryptedString.of("BANNABLE"));
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.currentScreen != null)
			return;

		if (!mc.player.isOnGround()
				|| mc.player.isSubmergedInWater()
				|| mc.player.isInLava()
				|| mc.player.hasVehicle()
				|| mc.player.getAbilities().flying)
			return;

		if (!((IKeyBinding) mc.options.jumpKey).isActuallyPressed())
			return;

		mc.options.jumpKey.setPressed(false);
		mc.options.jumpKey.setPressed(true);
	}
}
