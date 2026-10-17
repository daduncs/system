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

package dlindustries.uk.daduncs.render.esp;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.MappableRingBuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Util;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.OptionalInt;

public final class OutlineRenderer {

	private static final int STYLE_SIZE = new Std140SizeCalculator().putVec4().putVec4().putVec4().putVec4().get();
	private static final int MASK_SIZE = new Std140SizeCalculator().putVec4().get();
	private final MinecraftClient mc = MinecraftClient.getInstance();
	private final SilhouetteQueue queue = new SilhouetteQueue();
	private final SilhouetteBuffers.Outlines outlines = new SilhouetteBuffers.Outlines();
	private final SilhouetteBuffers.Discard discard = new SilhouetteBuffers.Discard();
	private final MatrixStack matrices = new MatrixStack();

	private RenderDispatcher dispatcher;
	private SimpleFramebuffer silhouettes;
	private SimpleFramebuffer seeds;
	private SimpleFramebuffer seedsSwap;
	private MappableRingBuffer style;
	private MappableRingBuffer mask;
	private boolean drawn;

	public void beginFrame() {
		EspPipelines.init();
		drawn = false;

		Framebuffer main = mc.getFramebuffer();
		if (silhouettes == null) {
			silhouettes = new SimpleFramebuffer("System outline shapes", main.textureWidth, main.textureHeight, false);
			seeds = new SimpleFramebuffer("System outline seeds", main.textureWidth, main.textureHeight, false);
			seedsSwap = new SimpleFramebuffer("System outline seeds swap", main.textureWidth, main.textureHeight, false);
			style = new MappableRingBuffer(() -> "System outline style", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, STYLE_SIZE);
		} else if (silhouettes.textureWidth != main.textureWidth || silhouettes.textureHeight != main.textureHeight) {
			silhouettes.resize(main.textureWidth, main.textureHeight);
			seeds.resize(main.textureWidth, main.textureHeight);
			seedsSwap.resize(main.textureWidth, main.textureHeight);
		}

		RenderSystem.getDevice().createCommandEncoder().clearColorTexture(silhouettes.getColorAttachment(), 0);
	}

	public void addEntity(EntityRenderState entity, WorldRenderState world, int color) {
		Vec3d camera = world.cameraRenderState.pos;
		queue.setColor(color);
		mc.getEntityRenderDispatcher().render(entity, world.cameraRenderState,
				entity.x - camera.x, entity.y - camera.y, entity.z - camera.z, matrices, queue);
	}

	public void addItem(ItemRenderState item, WorldRenderState world, Vec3d spot, float yaw, float scale, int color) {
		Vec3d camera = world.cameraRenderState.pos;
		queue.setColor(color);
		matrices.push();
		matrices.translate(spot.x - camera.x, spot.y - camera.y, spot.z - camera.z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
		matrices.scale(scale, scale, scale);
		item.render(matrices, queue, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, 0);
		matrices.pop();
	}

	public void addBlock(BlockEntity blockEntity, WorldRenderState world, int color) {
		queue.setColor(color);
		translateTo(blockEntity, world);
		queue.submitBlock(matrices, blockEntity.getCachedState(), LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, color);
		matrices.pop();
	}

	public void addBlockEntity(BlockEntity blockEntity, WorldRenderState world, int color) {
		BlockEntityRenderManager manager = mc.getBlockEntityRenderDispatcher();
		BlockEntityRenderState state = extract(manager, blockEntity, world.cameraRenderState.pos);
		if (state == null) return;

		queue.setColor(color);
		translateTo(blockEntity, world);
		manager.render(state, matrices, queue, world.cameraRenderState);
		matrices.pop();
	}

	public void drawShapes() {
		if (silhouettes == null || queue.isEmpty()) {
			queue.clear();
			return;
		}

		if (dispatcher == null) {
			dispatcher = new RenderDispatcher(queue, mc.getBlockRenderManager(), discard, mc.getAtlasManager(), outlines, discard, mc.textRenderer);
		}

		GpuTextureView previous = RenderSystem.outputColorTextureOverride;
		RenderSystem.outputColorTextureOverride = silhouettes.getColorAttachmentView();
		dispatcher.render();
		outlines.draw();
		RenderSystem.outputColorTextureOverride = previous;

		queue.onNextFrame();
		drawn = true;
	}

	public void addDepth(int color) {
		if (silhouettes == null) return;

		if (mask == null) {
			mask = new MappableRingBuffer(() -> "System outline mask", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, MASK_SIZE);
		}

		Framebuffer main = mc.getFramebuffer();
		if (main.getDepthAttachmentView() == null) return;

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (GpuBuffer.MappedView view = encoder.mapBuffer(mask.getBlocking(), false, true)) {
			Std140Builder.intoBuffer(view.data()).putVec4(ColorHelper.getRedFloat(color), ColorHelper.getGreenFloat(color), ColorHelper.getBlueFloat(color), 1);
		}

		try (RenderPass pass = encoder.createRenderPass(() -> "System outline depth mask", silhouettes.getColorAttachmentView(), OptionalInt.empty())) {
			pass.setPipeline(EspPipelines.HAND_MASK);
			pass.bindTexture("DepthSampler", main.getDepthAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
			pass.setUniform("MaskColor", mask.getBlocking());
			pass.draw(0, 3);
		}
		mask.rotate();
		drawn = true;
	}

	public void composite(float width, float glow, float fill, int look) {
		composite(width, glow, fill, look, false, 0, 0);
	}

	public void composite(float width, float glow, float fill, int look, boolean gradient, int from, int to) {
		if (!drawn) return;

		GpuSampler nearest = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
		GpuSampler linear = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

		try (RenderPass pass = encoder.createRenderPass(() -> "System outline seed", seeds.getColorAttachmentView(), OptionalInt.empty())) {
			pass.setPipeline(EspPipelines.SEED);
			pass.bindTexture("SilhouetteSampler", silhouettes.getColorAttachmentView(), nearest);
			pass.draw(0, 3);
		}

		SimpleFramebuffer read = seeds;
		SimpleFramebuffer write = seedsSwap;
		for (int step : EspPipelines.JUMP_STEPS) {
			jump(encoder, EspPipelines.jump(step), read, write, nearest);
			SimpleFramebuffer swap = read;
			read = write;
			write = swap;
		}

		GpuTextureView blur = look == 1 ? FrameBlur.blur() : null;
		if (blur == null) {
			blur = silhouettes.getColorAttachmentView();
			if (look == 1) look = 0;
		}

		try (GpuBuffer.MappedView view = encoder.mapBuffer(style.getBlocking(), false, true)) {
			float time = (Util.getMeasuringTimeMs() % 100_000L) / 1000.0f;
			Std140Builder.intoBuffer(view.data())
					.putVec4(width, glow, fill, time)
					.putVec4(look, gradient ? 1 : 0, 0, 0)
					.putVec4(ColorHelper.getRedFloat(from), ColorHelper.getGreenFloat(from), ColorHelper.getBlueFloat(from), 1)
					.putVec4(ColorHelper.getRedFloat(to), ColorHelper.getGreenFloat(to), ColorHelper.getBlueFloat(to), 1);
		}

		Framebuffer main = mc.getFramebuffer();
		try (RenderPass pass = encoder.createRenderPass(() -> "System outline composite", main.getColorAttachmentView(), OptionalInt.empty())) {
			pass.setPipeline(EspPipelines.COMPOSITE);
			pass.bindTexture("SilhouetteSampler", silhouettes.getColorAttachmentView(), nearest);
			pass.bindTexture("SeedSampler", read.getColorAttachmentView(), nearest);
			pass.bindTexture("BlurSampler", blur, linear);
			pass.setUniform("EspStyle", style.getBlocking());
			pass.draw(0, 3);
		}
		style.rotate();
	}

	private void translateTo(BlockEntity blockEntity, WorldRenderState world) {
		Vec3d camera = world.cameraRenderState.pos;
		matrices.push();
		matrices.translate(blockEntity.getPos().getX() - camera.x, blockEntity.getPos().getY() - camera.y, blockEntity.getPos().getZ() - camera.z);
	}

	private <E extends BlockEntity, S extends BlockEntityRenderState> S extract(BlockEntityRenderManager manager, E blockEntity, Vec3d camera) {
		BlockEntityRenderer<E, S> renderer = manager.get(blockEntity);
		if (renderer == null || !blockEntity.hasWorld() || !blockEntity.getType().supports(blockEntity.getCachedState())) {
			return null;
		}

		S state = renderer.createRenderState();
		renderer.updateRenderState(blockEntity, state, mc.getRenderTickCounter().getTickProgress(false), camera, null);
		return state;
	}

	private static void jump(CommandEncoder encoder, RenderPipeline pipeline, SimpleFramebuffer input, SimpleFramebuffer output, GpuSampler sampler) {
		try (RenderPass pass = encoder.createRenderPass(() -> "System outline jump", output.getColorAttachmentView(), OptionalInt.empty())) {
			pass.setPipeline(pipeline);
			pass.bindTexture("SeedSampler", input.getColorAttachmentView(), sampler);
			pass.draw(0, 3);
		}
	}
}
