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
import dlindustries.uk.daduncs.event.events.MouseMoveListener;
import dlindustries.uk.daduncs.event.events.MouseUpdateListener;
import dlindustries.uk.daduncs.gui.ConfigDrop;
import dlindustries.uk.daduncs.managers.Recorder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.List;

@Mixin(Mouse.class)
public abstract class MouseMixin {
	@Shadow @Final private MinecraftClient client;
	@Shadow public abstract double getX();
	@Shadow public abstract double getY();

	@Unique private double system$lastMouseX;
	@Unique private double system$lastMouseY;
	@Unique private boolean system$initialized;

	@Inject(method = "onFilesDropped", at = @At("HEAD"), cancellable = true)
	private void onFilesDroppedHook(long window, List<Path> paths, int invalidFilesCount, CallbackInfo ci) {
		if (ConfigDrop.onDrop(paths)) {
			ci.cancel();
		}
	}

	@Inject(method = "onMouseButton", at = @At("HEAD"))
	private void onMouseButtonHook(long window, MouseInput input, int action, CallbackInfo ci) {
		if (window != client.getWindow().getHandle()) return;
		Recorder tm = Recorder.getInstance();
		if (tm != null) {
			tm.recordClick(input.button(), action, input.modifiers(), getX(), getY(), false);
		}
		EventManager.fire(new ButtonListener.ButtonEvent(input.button(), window, action));
	}

	@Inject(method = "onMouseScroll", at = @At("HEAD"))
	private void onMouseScrollHook(long window, double horizontal, double vertical, CallbackInfo ci) {
		Recorder tm = Recorder.getInstance();
		if (tm != null) {
			tm.recordScroll(horizontal, vertical);
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void onMouseUpdate(CallbackInfo ci) {
		EventManager.fire(new MouseUpdateListener.MouseUpdateEvent());
		long window = client.getWindow().getHandle();
		double x = getX();
		double y = getY();

		if (!system$initialized) {
			system$initialized = true;
			system$lastMouseX = x;
			system$lastMouseY = y;

			return;
		}

		if (x != system$lastMouseX || y != system$lastMouseY) {
			system$lastMouseX = x;
			system$lastMouseY = y;
			EventManager.fire(new MouseMoveListener.MouseMoveEvent(window, x, y));
		}

	}
}
