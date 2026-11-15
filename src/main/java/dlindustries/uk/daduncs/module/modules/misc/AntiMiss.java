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

import dlindustries.uk.daduncs.event.events.AttackListener;
import dlindustries.uk.daduncs.event.events.BlockBreakingListener;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.WorldUtils;
import net.minecraft.util.hit.HitResult;

public final class AntiMiss extends Module implements AttackListener, BlockBreakingListener {
	private final bool onlyWeapon = new bool(EncryptedString.of("Only weapon"), true);
	private final bool air = new bool(EncryptedString.of("Air"), true)
			.setDescription(EncryptedString.of("Whether to stop hits directed to the air"));
	private final bool blocks = new bool(EncryptedString.of("Blocks"), false)
			.setDescription(EncryptedString.of("Whether to stop hits directed to blocks"));

	public AntiMiss() {
		super(EncryptedString.of("Anti Miss"),
				EncryptedString.of("Doesn't let you miss your sword/axe hits"),
				-1,
				Category.MISC);
		addSettings(onlyWeapon, air, blocks);
	}

	@Override
	public void onEnable() {
		eventManager.add(AttackListener.class, this);
		eventManager.add(BlockBreakingListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(AttackListener.class, this);
		eventManager.remove(BlockBreakingListener.class, this);
		super.onDisable();
	}

	@Override
	public void onAttack(AttackEvent event) {
		if (mc.player == null || mc.crosshairTarget == null)
			return;
		if (onlyWeapon.getValue() && !WorldUtils.isWeapon(mc.player.getMainHandStack()))
			return;

		switch (mc.crosshairTarget.getType()) {
			case MISS -> {
				if (air.getValue()) event.cancel();
			}
			case BLOCK -> {
				if (blocks.getValue()) event.cancel();
			}
		}
	}

	@Override
	public void onBlockBreaking(BlockBreakingEvent event) {
		if (mc.player == null || mc.crosshairTarget == null)
			return;
		if (onlyWeapon.getValue() && !WorldUtils.isWeapon(mc.player.getMainHandStack()))
			return;

		if (mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
			if (blocks.getValue()) event.cancel();
		}
	}
}
