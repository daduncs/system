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

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dlindustries.uk.daduncs.System;
import dlindustries.uk.daduncs.module.modules.render.Esp;
import dlindustries.uk.daduncs.module.modules.render.PopFx;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void onRenderStart(CallbackInfo ci) {
		Esp esp = esp();
		if (esp != null) esp.beginFrame();

		PopFx pops = pops();
		if (pops != null) pops.beginFrame();
	}

	@Inject(method = "renderBlockEntities", at = @At("TAIL"))
	private void onSubmitted(MatrixStack matrices, WorldRenderState state, OrderedRenderCommandQueueImpl queue, CallbackInfo ci) {
		Esp esp = esp();
		if (esp != null) esp.submit(state);

		PopFx pops = pops();
		if (pops != null) pops.submit(state);
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void onRenderEnd(CallbackInfo ci) {
		Esp esp = esp();
		if (esp != null) esp.composite();

		PopFx pops = pops();
		if (pops != null) pops.composite();
	}

	@ModifyExpressionValue(method = "fillEntityRenderStates", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/entity/EntityRenderManager;shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z"))
	private boolean forceFrustum(boolean visible) {
		if (visible) return true;

		Esp esp = esp();
		return esp != null && esp.forcesEntities();
	}

	@ModifyExpressionValue(method = "fillEntityRenderStates", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/WorldRenderer;isRenderingReady(Lnet/minecraft/util/math/BlockPos;)Z"))
	private boolean forceExtract(boolean visible) {
		if (visible) return true;

		Esp esp = esp();
		return esp != null && esp.forcesEntities();
	}

	private static Esp esp() {
		if (System.INSTANCE == null || System.INSTANCE.getModuleManager() == null) return null;

		Esp esp = System.INSTANCE.getModuleManager().getModule(Esp.class);
		return esp != null && esp.isEnabled() ? esp : null;
	}

	private static PopFx pops() {
		if (System.INSTANCE == null || System.INSTANCE.getModuleManager() == null) return null;

		PopFx pops = System.INSTANCE.getModuleManager().getModule(PopFx.class);
		return pops != null && pops.isEnabled() ? pops : null;
	}
}
