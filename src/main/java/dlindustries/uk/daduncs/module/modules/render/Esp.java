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

import dlindustries.uk.daduncs.gui.Theme;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.colour;
import dlindustries.uk.daduncs.module.setting.mode;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.render.esp.OutlineRenderer;
import dlindustries.uk.daduncs.render.esp.TracerRenderer;
import dlindustries.uk.daduncs.utils.EncryptedString;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Items;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

public final class Esp extends Module {
	public enum Fill {
		SOLID, FROSTED, FLUID
	}

	private static final int THEME = 0xFF9B6DFF;

	private final bool custom = new bool(EncryptedString.of("Custom Color"), false);
	private final bool gradient = new bool(EncryptedString.of("Gradient"), true).setVisibility(() -> !custom.getValue());
	private final bool players = new bool(EncryptedString.of("Players"), true).setIcon(Items.PLAYER_HEAD).setDivider(true);
	private final colour playerColor = new colour(EncryptedString.of("Player Color"), THEME).setVisibility(() -> custom.getValue() && players.getValue());
	private final bool playerTracers = new bool(EncryptedString.of("Tracers"), false).setVisibility(players::getValue);
	private final bool crystals = new bool(EncryptedString.of("Crystals"), true).setIcon(Items.END_CRYSTAL).setDivider(true);
	private final colour crystalColor = new colour(EncryptedString.of("Crystal Color"), THEME).setVisibility(() -> custom.getValue() && crystals.getValue());
	private final bool crystalTracers = new bool(EncryptedString.of("Tracers"), false).setVisibility(crystals::getValue);
	private final bool items = new bool(EncryptedString.of("Items"), true).setIcon(Items.TROPICAL_FISH_BUCKET).setDivider(true);
	private final colour itemColor = new colour(EncryptedString.of("Item Color"), THEME).setVisibility(() -> custom.getValue() && items.getValue());
	private final bool itemTracers = new bool(EncryptedString.of("Tracers"), false).setVisibility(items::getValue);
	private final bool chests = new bool(EncryptedString.of("Chests"), true).setIcon(Items.CHEST).setDivider(true);
	private final colour chestColor = new colour(EncryptedString.of("Chest Color"), THEME).setVisibility(() -> custom.getValue() && chests.getValue());
	private final bool chestTracers = new bool(EncryptedString.of("Tracers"), false).setVisibility(chests::getValue);
	private final bool enderChests = new bool(EncryptedString.of("Ender Chests"), true).setIcon(Items.ENDER_CHEST).setDivider(true);
	private final colour enderChestColor = new colour(EncryptedString.of("Ender Chest Color"), THEME).setVisibility(() -> custom.getValue() && enderChests.getValue());
	private final bool enderChestTracers = new bool(EncryptedString.of("Tracers"), false).setVisibility(enderChests::getValue);
	private final bool barrels = new bool(EncryptedString.of("Barrels"), true).setIcon(Items.BARREL).setDivider(true);
	private final colour barrelColor = new colour(EncryptedString.of("Barrel Color"), THEME).setVisibility(() -> custom.getValue() && barrels.getValue());
	private final bool barrelTracers = new bool(EncryptedString.of("Tracers"), false).setVisibility(barrels::getValue);
	private final bool shulkers = new bool(EncryptedString.of("Shulkers"), true).setIcon(Items.SHULKER_BOX).setDivider(true);
	private final colour shulkerColor = new colour(EncryptedString.of("Shulker Color"), THEME).setVisibility(() -> custom.getValue() && shulkers.getValue());
	private final bool shulkerTracers = new bool(EncryptedString.of("Tracers"), false).setVisibility(shulkers::getValue);
	private final bool spawners = new bool(EncryptedString.of("Spawners"), true).setIcon(Items.SPAWNER).setDivider(true);
	private final colour spawnerColor = new colour(EncryptedString.of("Spawner Color"), THEME).setVisibility(() -> custom.getValue() && spawners.getValue());
	private final bool spawnerTracers = new bool(EncryptedString.of("Tracers"), false).setVisibility(spawners::getValue);
	private final mode<Fill> fillMode = new mode<>(EncryptedString.of("Fill Mode"), Fill.SOLID, Fill.class).setDivider(true);
	private final number fill = new number(EncryptedString.of("Fill"), 0, 1, 0.2, 0.05);
	private final number width = new number(EncryptedString.of("Width"), 1, 6, 1, 0.5);
	private final number glow = new number(EncryptedString.of("Glow"), 0, 24, 0, 1);

	private final OutlineRenderer renderer = new OutlineRenderer();
	private final TracerRenderer tracerRenderer = new TracerRenderer();

	public Esp() {
		super(EncryptedString.of("Esp"),
				EncryptedString.of("Highlights stuff ( players , blocks of some kinds  ) threw walls"),
				-1,
				Category.RENDER);
		addSettings(custom, gradient,
				players, playerColor, playerTracers,
				crystals, crystalColor, crystalTracers,
				items, itemColor, itemTracers,
				chests, chestColor, chestTracers,
				enderChests, enderChestColor, enderChestTracers,
				barrels, barrelColor, barrelTracers,
				shulkers, shulkerColor, shulkerTracers,
				spawners, spawnerColor, spawnerTracers,
				fillMode, fill, width, glow);
	}

	public void beginFrame() {
		renderer.beginFrame();
	}

	public void submit(WorldRenderState world) {
		if (mc.player == null || mc.world == null) return;

		for (EntityRenderState entity : world.entityRenderStates) {
			int color = entityColor(entity);
			if (color == 0) continue;

			renderer.addEntity(entity, world, color);
			if (traced(entity)) {
				tracerRenderer.add(new Vec3d(entity.x, entity.y + entity.height / 2, entity.z), world, color);
			}
		}

		if (chests.getValue() || enderChests.getValue() || barrels.getValue() || shulkers.getValue() || spawners.getValue()) {
			submitBlocks(world);
		}

		renderer.drawShapes();
		tracerRenderer.draw();
	}

	public void composite() {
		renderer.composite(width.getValueFloat(), glow.getValueFloat(), fill.getValueFloat(), fillMode.getModeIndex(),
				gradient.getValue() && !custom.getValue(), Theme.accent, Theme.flowEnd());
	}

	public boolean forcesEntities() {
		return players.getValue() || crystals.getValue() || items.getValue();
	}

	private void submitBlocks(WorldRenderState world) {
		int chunks = mc.options.getClampedViewDistance();
		ChunkPos center = mc.player.getChunkPos();

		for (int x = center.x - chunks; x <= center.x + chunks; x++) {
			for (int z = center.z - chunks; z <= center.z + chunks; z++) {
				WorldChunk chunk = mc.world.getChunkManager().getChunk(x, z, ChunkStatus.FULL, false);
				if (chunk == null) continue;

				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					int color = blockColor(blockEntity);
					if (color == 0) continue;

					if (blockEntity instanceof BarrelBlockEntity || blockEntity instanceof MobSpawnerBlockEntity) {
						renderer.addBlock(blockEntity, world, color);
					} else {
						renderer.addBlockEntity(blockEntity, world, color);
					}

					if (traced(blockEntity)) {
						tracerRenderer.add(blockEntity.getPos().toCenterPos(), world, color);
					}
				}
			}
		}
	}

	private int entityColor(EntityRenderState entity) {
		if (entity.entityType == EntityType.PLAYER && players.getValue()) {
			if (entity instanceof PlayerEntityRenderState player && player.id == mc.player.getId()) return 0;

			return paint(playerColor);
		}
		if (entity.entityType == EntityType.END_CRYSTAL && crystals.getValue()) {
			return paint(crystalColor);
		}
		if (entity.entityType == EntityType.ITEM && items.getValue()) {
			return paint(itemColor);
		}
		return 0;
	}

	private boolean traced(EntityRenderState entity) {
		if (entity.entityType == EntityType.PLAYER) return playerTracers.getValue();
		if (entity.entityType == EntityType.END_CRYSTAL) return crystalTracers.getValue();

		return itemTracers.getValue();
	}

	private boolean traced(BlockEntity blockEntity) {
		return switch (blockEntity) {
			case MobSpawnerBlockEntity spawner -> spawnerTracers.getValue();
			case ChestBlockEntity chest -> chestTracers.getValue();
			case EnderChestBlockEntity enderChest -> enderChestTracers.getValue();
			case BarrelBlockEntity barrel -> barrelTracers.getValue();
			case ShulkerBoxBlockEntity shulker -> shulkerTracers.getValue();
			default -> false;
		};
	}

	private int paint(colour picked) {
		return custom.getValue() ? picked.getValue() : Theme.accent;
	}

	private int blockColor(BlockEntity blockEntity) {
		return switch (blockEntity) {
			case MobSpawnerBlockEntity spawner -> spawners.getValue() ? paint(spawnerColor) : 0;
			case ChestBlockEntity chest -> chests.getValue() ? paint(chestColor) : 0;
			case EnderChestBlockEntity enderChest -> enderChests.getValue() ? paint(enderChestColor) : 0;
			case BarrelBlockEntity barrel -> barrels.getValue() ? paint(barrelColor) : 0;
			case ShulkerBoxBlockEntity shulker -> shulkers.getValue() ? paint(shulkerColor) : 0;
			default -> 0;
		};
	}
}
