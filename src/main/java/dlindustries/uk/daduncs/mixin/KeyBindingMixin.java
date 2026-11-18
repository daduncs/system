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

import dlindustries.uk.daduncs.imixin.IKeyBinding;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.StickyKeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import static dlindustries.uk.daduncs.System.mc;

@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin implements IKeyBinding {

	@Shadow
	private InputUtil.Key boundKey;

	@Shadow
	private boolean pressed;

	@Override
	public boolean isActuallyPressed() {
		int code = boundKey.getCode();
		if (code == -1 || mc == null || mc.getWindow() == null || !mc.isWindowFocused()) {
			return false;
		}
		if (boundKey.getCategory() == InputUtil.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), code) == GLFW.GLFW_PRESS;
		}
		if (boundKey.getCategory() == InputUtil.Type.SCANCODE) {
			return dlindustries.uk.daduncs.utils.KeyUtils.isScanCodePressed(code);
		}
		return InputUtil.isKeyPressed(mc.getWindow(), code);
	}

	@Override
	public void resetPressed() {
		if ((Object) this instanceof StickyKeyBinding) {
			if (isActuallyPressed()) {
				this.pressed = true;
			}
			return;
		}
		setPressed(isActuallyPressed());
	}

	@Override
	public void setRawPressed(boolean pressed) {
		this.pressed = pressed;
	}

	@Shadow
	public abstract void setPressed(boolean pressed);
}
