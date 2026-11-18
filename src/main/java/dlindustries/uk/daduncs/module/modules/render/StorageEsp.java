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

import dlindustries.uk.daduncs.event.events.GameRenderListener;
import dlindustries.uk.daduncs.gui.Theme;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.RenderUtils;
import dlindustries.uk.daduncs.utils.WorldUtils;
import net.minecraft.block.entity.*;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.*;

public final class StorageEsp extends Module implements GameRenderListener {
	private final number alpha = new number(EncryptedString.of("Alpha"), 1, 255, 125, 1);
	private final bool tracers = new bool(EncryptedString.of("Tracers"), false)
			.setDescription(EncryptedString.of("Draws a line from your player to the storage block"));

	public StorageEsp() {
		super(EncryptedString.of("Storage ESP"),
				EncryptedString.of("Renders storage blocks through walls"),
				-1,
				Category.RENDER);
		addSettings(alpha, tracers);
	}

	@Override
	public void onEnable() {
		eventManager.add(GameRenderListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(GameRenderListener.class, this);
		super.onDisable();
	}

	@Override
	public void onGameRender(GameRenderEvent event) {
		renderStorages(event);
	}

	private Color getColor(BlockEntity blockEntity, int a) {
		Color base = baseColor(blockEntity);
		if (base.getAlpha() == 0) return base;

		int tinted = Theme.lerp(0xFF000000 | base.getRGB(), Theme.accent, 0.55f);
		return new Color((tinted >> 16) & 0xFF, (tinted >> 8) & 0xFF, tinted & 0xFF, a);
	}

	private Color baseColor(BlockEntity blockEntity) {
		int a = 255;
		if (blockEntity instanceof TrappedChestBlockEntity) {
			return new Color(200, 91, 0, a);
		} else if (blockEntity instanceof ChestBlockEntity) {
			return new Color(156, 91, 0, a);
		} else if (blockEntity instanceof EnderChestBlockEntity) {
			return new Color(117, 0, 255, a);
		} else if (blockEntity instanceof MobSpawnerBlockEntity) {
			return new Color(138, 126, 166, a);
		} else if (blockEntity instanceof ShulkerBoxBlockEntity) {
			return new Color(134, 0, 158, a);
		} else if (blockEntity instanceof FurnaceBlockEntity) {
			return new Color(125, 125, 125, a);
		} else if (blockEntity instanceof BarrelBlockEntity) {
			return new Color(255, 140, 140, a);
		} else if (blockEntity instanceof EnchantingTableBlockEntity) {
			return new Color(80, 80, 255, a);
		} else return new Color(255, 255, 255, 0);
	}

	private void renderStorages(GameRenderEvent event) {
		Camera cam = mc.gameRenderer.getCamera();
		if (cam == null) {
			return;
		}

		event.matrices.push();
		Vec3d cameraPos = cam.getCameraPos();
		event.matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cam.getPitch()));
		event.matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(cam.getYaw() + 180.0F));
		event.matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

		for (WorldChunk chunk : WorldUtils.getLoadedChunks().toList()) {
			for (BlockPos blockPos : chunk.getBlockEntityPositions()) {
				BlockEntity blockEntity = mc.world.getBlockEntity(blockPos);
				if (blockEntity == null) {
					continue;
				}

				RenderUtils.renderFilledBox(event.matrices, blockPos.getX() + 0.1F, blockPos.getY() + 0.05F, blockPos.getZ() + 0.1F, blockPos.getX() + 0.9F, blockPos.getY() + 0.85F, blockPos.getZ() + 0.9F, getColor(blockEntity, alpha.getValueInt()));

				Vec3d center = new Vec3d(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
				if (tracers.getValue() && mc.crosshairTarget != null) {
					RenderUtils.renderLine(
							event.matrices,
							getColor(blockEntity, 255),
							mc.crosshairTarget.getPos(),
							center
					);
				}
			}
		}

		event.matrices.pop();
	}

}
