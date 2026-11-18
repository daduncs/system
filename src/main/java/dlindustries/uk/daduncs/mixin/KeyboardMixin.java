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

import dlindustries.uk.daduncs.event.EventManager;
import dlindustries.uk.daduncs.event.events.ButtonListener;
import dlindustries.uk.daduncs.managers.Recorder;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {
	@Shadow
	@Final
	private MinecraftClient client;

	@Inject(method = "onKey", at = @At("HEAD"))
	private void onPress(long window, int action, KeyInput input, CallbackInfo ci) {
		if (window != client.getWindow().getHandle()) return;
		dlindustries.uk.daduncs.utils.KeyUtils.onScanCode(input.scancode(), action);
		Recorder tm = Recorder.getInstance();
		if (tm != null) {
			tm.recordKey(input.key(), input.scancode(), action, input.modifiers());
		}
		EventManager.fire(new ButtonListener.ButtonEvent(input.key(), window, action));
	}
}
