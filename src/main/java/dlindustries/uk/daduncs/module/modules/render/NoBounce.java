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

package dlindustries.uk.daduncs.module.modules.render;

import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.utils.EncryptedString;

public final class NoBounce extends Module {
	public static boolean active() {
		var client = dlindustries.uk.daduncs.System.INSTANCE;
		if (client == null || client.getModuleManager() == null) return false;
		var module = client.getModuleManager().getModule(NoBounce.class);
		return module != null && module.isEnabled();
	}

	public NoBounce() {
		super(EncryptedString.of("No Bounce"),
				EncryptedString.of("Removes the crystal bounce"),
				-1,
				Category.RENDER);
	}
}
