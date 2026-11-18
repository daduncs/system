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

import dlindustries.uk.daduncs.mixin.MinecraftClientAccessor;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

public final class FastPlace extends Module {
	private final number delay = new number(EncryptedString.of("Delay"), 0, 4, 0, 1)
			.setDescription(EncryptedString.of("Ticks between automatic block placements (vanilla is 4)"));
	private final bool excludeAnchors = new bool(EncryptedString.of("Exclude Anchors"), true)
			.setDescription(EncryptedString.of("Keeps vanilla delay for respawn anchors and glowstone"));

	public FastPlace() {
		super(EncryptedString.of("Fast Place"),
				EncryptedString.of("Reduces the delay between automatic block placements while holding right click"),
				-1,
				Category.MISC);
		addSettings(delay, excludeAnchors);
	}

	public void apply() {
		if (!isEnabled() || mc.player == null)
			return;

		Item main = mc.player.getMainHandStack().getItem();
		Item off = mc.player.getOffHandStack().getItem();

		if (!(main instanceof BlockItem) && !(off instanceof BlockItem))
			return;

		if (excludeAnchors.getValue() && isAnchorOrGlowstone(main, off))
			return;

		MinecraftClientAccessor accessor = (MinecraftClientAccessor) mc;
		int target = delay.getValueInt();
		if (accessor.getItemUseCooldown() > target)
			accessor.setItemUseCooldown(target);
	}

	private static boolean isAnchorOrGlowstone(Item main, Item off) {
		return main == Items.RESPAWN_ANCHOR || off == Items.RESPAWN_ANCHOR
				|| main == Items.GLOWSTONE || off == Items.GLOWSTONE;
	}
}
