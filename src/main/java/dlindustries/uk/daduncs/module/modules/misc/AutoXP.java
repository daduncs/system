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

import dlindustries.uk.daduncs.event.events.ItemUseListener;
import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.MathUtils;
import dlindustries.uk.daduncs.utils.MouseSimulation;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

public final class AutoXP extends Module implements TickListener, ItemUseListener {
	private final number delay = new number(EncryptedString.of("Delay"), 0, 20, 0, 1);
	private final number chance = new number(EncryptedString.of("Chance"), 0, 100, 100, 1)
			.setDescription(EncryptedString.of("Randomization"));
	private final bool clickSimulation = new bool(EncryptedString.of("Click Simulation"), false)
			.setDescription(EncryptedString.of("Records module clicks as simulated input"));
	int clock;

	public AutoXP() {
		super(EncryptedString.of("Auto XP"),
				EncryptedString.of("Automatically throws XP bottles for you"),
				-1,
				Category.MISC);
		addSettings(delay, chance, clickSimulation);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		eventManager.add(ItemUseListener.class, this);

		clock = 0;
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		eventManager.remove(ItemUseListener.class, this);
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.currentScreen != null)
			return;

		boolean dontThrow = clock != 0;

		int randomInt = MathUtils.randomInt(1, 100);

		if (mc.player.getMainHandStack().getItem() != Items.EXPERIENCE_BOTTLE)
			return;

		if (GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS)
			return;

		if (dontThrow)
			clock--;

		if (!dontThrow && randomInt <= chance.getValueInt() && !mc.player.isUsingItem() && AntibanbyDaduncs.canUseItemNow(Hand.MAIN_HAND) && !AntibanbyDaduncs.isSlotPending()) {
			if (dlindustries.uk.daduncs.utils.WorldUtils.useItem(mc.player, Hand.MAIN_HAND)) {
				if (clickSimulation.getValue())
					MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			}

			clock = delay.getValueInt();
		}
	}

	@Override
	public void onItemUse(ItemUseEvent event) {
		if (mc.player == null)
			return;
		if (!dlindustries.uk.daduncs.managers.InputContext.isModuleUse()
				&& mc.player.getMainHandStack().getItem() == Items.EXPERIENCE_BOTTLE) {
			event.cancel();
		}
	}

}
