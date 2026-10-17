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

package dlindustries.uk.daduncs.module.modules.crystal;

import dlindustries.uk.daduncs.System;
import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.mode;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.*;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class 	AutoDoubleHand extends Module implements TickListener {
	public enum AnchorMode {
		ALWAYS("Always"),
		CRITICAL("Critical");

		private final String name;

		AnchorMode(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	private final bool stopOnCrystal = new bool(EncryptedString.of("Stop On Crystal"), true)
			.setDescription(EncryptedString.of("Stops while Auto Crystal is running"));
	private final bool checkShield = new bool(EncryptedString.of("Check Shield"), true)
			.setDescription(EncryptedString.of("Checks if you're blocking with a shield"));

	private final bool onPop = new bool(EncryptedString.of("On Pop"), false)
			.setDescription(EncryptedString.of("Switches to a totem if you pop"));
	private final bool onHealth = new bool(EncryptedString.of("On Health"), false)
			.setDescription(EncryptedString.of("Switches to totem if low on health"));
	private final bool predict = new bool(EncryptedString.of("Predict Damage"), true);

	private final number health = new number(EncryptedString.of("Health"), 1, 20, 2, 1)
			.setDescription(EncryptedString.of("Health to trigger at"));
	private final bool onGround = new bool(EncryptedString.of("On Ground"), true)
			.setDescription(EncryptedString.of("Whether crystal damage is checked on ground or not"));
	private final bool checkPlayers = new bool(EncryptedString.of("Check Players"), true)
			.setDescription(EncryptedString.of("Checks for nearby players"));

	private final number distance = new number(EncryptedString.of("Distance"), 1, 10, 5, 0.1)
			.setDescription(EncryptedString.of("Player distance"));
	private final bool predictCrystals = new bool(EncryptedString.of("Predict Crystals"), false);
	private final bool checkAim = new bool(EncryptedString.of("Check Aim"), true)
			.setDescription(EncryptedString.of("Checks if the opponent is aiming at obsidian"));
	private final bool checkItems = new bool(EncryptedString.of("Check Items"), true)
			.setDescription(EncryptedString.of("Checks if the opponent is holding crystals"));

	private final number activatesAbove = new number(EncryptedString.of("Activates Above"), 0, 4, 0.2, 0.1)
			.setDescription(EncryptedString.of("Height to trigger at"));

	private final bool reduceStrictness = new bool(EncryptedString.of("Ignore Flying"), true)
			.setDescription(EncryptedString.of("Pauses module while flying and for 0.5s after you stop flying"));
	private final bool includeAnchor = new bool(EncryptedString.of("Include Anchor"), true)
			.setDescription(EncryptedString.of("Detect fatal charged anchors"));
	private final mode<AnchorMode> anchorMode = new mode<>(
			EncryptedString.of("Anchor Mode"),
			AnchorMode.CRITICAL,
			AnchorMode.class
	).setDescription(EncryptedString.of("When to double hand for anchors"));

	private boolean belowHealth;
	private boolean offhandHasNoTotem;
	private boolean wasFlying;
	private int postFlyingDelayTicks;

	public AutoDoubleHand() {
		super(EncryptedString.of("Auto Double Hand"),
				EncryptedString.of("Automatically switches to your totem when you're about to pop"),
				-1,
				Category.CRYSTAL);
		addSettings(stopOnCrystal, checkShield, onPop, onHealth, predict, health, onGround, checkPlayers, distance,
				predictCrystals, checkAim, checkItems, activatesAbove, reduceStrictness, includeAnchor, anchorMode);
		belowHealth = false;
		offhandHasNoTotem = false;
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		wasFlying = false;
		postFlyingDelayTicks = 0;
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		super.onDisable();
	}

	@SuppressWarnings("all")
	@Override
	public void onTick() {
		if (mc.player == null)
			return;

		if (AntibanbyDaduncs.isSlotPending() || AntibanbyDaduncs.isActionSent())
			return;

		if(System.INSTANCE.getModuleManager().getModule(AutoCrystal.class).crystalling && stopOnCrystal.getValue())
			return;

		if (reduceStrictness.getValue()) {
			boolean isFlying = mc.player.getAbilities().flying || mc.player.isGliding();
			if (isFlying) {
				wasFlying = true;
				postFlyingDelayTicks = 10;
				return;
			}
			if (wasFlying) {
				if (postFlyingDelayTicks > 0) {
					postFlyingDelayTicks--;
					return;
				}
				wasFlying = false;
			}
		}

		double squaredDistance = distance.getValue() * distance.getValue();
		PlayerInventory inventory = mc.player.getInventory();

		if (checkShield.getValue() && mc.player.isBlocking())
			return;

		if (mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING && onPop.getValue() && !offhandHasNoTotem) {
			offhandHasNoTotem = true;
			InventoryUtils.selectItemFromHotbar(Items.TOTEM_OF_UNDYING);
		}

		if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING)
			offhandHasNoTotem = false;

		if (mc.player.getHealth() <= health.getValue() && onHealth.getValue() && !belowHealth) {
			belowHealth = true;
			InventoryUtils.selectItemFromHotbar(Items.TOTEM_OF_UNDYING);
		}

		if (mc.player.getHealth() > health.getValue())
			belowHealth = false;

		if(!predict.getValue())
			return;

		if (includeAnchor.getValue()) {
			List<BlockPos> chargedAnchors = BlockUtils.getAllInBoxStream(
							mc.player.getBlockPos().add(-6, -6, -6),
							mc.player.getBlockPos().add(6, 6, 6))
					.filter(pos -> {
						var state = mc.world.getBlockState(pos);
						return state.getBlock() == Blocks.RESPAWN_ANCHOR && state.get(RespawnAnchorBlock.CHARGES) > 0;
					})
					.toList();

			for (BlockPos anchorPos : chargedAnchors) {
				Vec3d anchorVec = Vec3d.ofCenter(anchorPos);
				float damage = DamageUtils.anchorDamage(mc.player, anchorVec);
				if (damage >= mc.player.getHealth() + mc.player.getAbsorptionAmount()) {
					switch (anchorMode.getMode()) {
						case ALWAYS:
							InventoryUtils.selectItemFromHotbar(Items.TOTEM_OF_UNDYING);
							return;
						case CRITICAL:
							if (mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
								InventoryUtils.selectItemFromHotbar(Items.TOTEM_OF_UNDYING);
								return;
							}
							break;
					}
				}
			}
		}

		if (mc.player.getHealth() > 19)
			return;

		if (!onGround.getValue() && mc.player.isOnGround())
			return;

		if (checkPlayers.getValue() && mc.world.getPlayers().parallelStream().filter(e -> e != mc.player).noneMatch(p -> mc.player.squaredDistanceTo(p) <= squaredDistance))
			return;

		double above = activatesAbove.getValue();
		for (int floor = (int) Math.floor(above), i = 1; i <= floor; i++) {
			if (!mc.world.getBlockState(mc.player.getBlockPos().add(0, -i, 0)).isAir())
				return;
		}

		Vec3d playerPos = mc.player.getEntityPos();
		BlockPos playerBlockPos = new BlockPos((int) playerPos.x, (int) playerPos.y - (int) above, (int) playerPos.z);
		if (!mc.world.getBlockState(new BlockPos(playerBlockPos)).isAir())
			return;

		List<EndCrystalEntity> crystals = nearbyCrystals();
		ArrayList<Vec3d> pos = new ArrayList<>();
		crystals.forEach(e -> pos.add(e.getEntityPos()));

		if (predictCrystals.getValue()) {
			Stream<BlockPos> s = BlockUtils.getAllInBoxStream(mc.player.getBlockPos().add(-6, -8, -6), mc.player.getBlockPos().add(6, 2, 6))
					.filter(e -> mc.world.getBlockState(e).getBlock() == Blocks.OBSIDIAN || mc.world.getBlockState(e).getBlock() == Blocks.BEDROCK)
					.filter(CrystalUtils::canPlaceCrystalClient);

			if (checkAim.getValue()) {
				if (checkItems.getValue())
					s = s.filter(this::isAimingWithCrystal);
				else s = s.filter(this::isAimingAt);
			}
			s.forEachOrdered(e -> pos.add(Vec3d.ofBottomCenter(e).add(0, 1, 0)));
		}

		for (Vec3d crys : pos) {
			if (Math.abs(mc.player.getY() - crys.y) > 4.0) continue;

			double damage = DamageUtils.crystalDamage(mc.player, crys);

			if (damage >= mc.player.getHealth() + mc.player.getAbsorptionAmount()) {
				InventoryUtils.selectItemFromHotbar(Items.TOTEM_OF_UNDYING);
				break;
			}
		}
	}

	private List<EndCrystalEntity> nearbyCrystals() {
		Vec3d pos = mc.player.getEntityPos();
		return mc.world.getEntitiesByClass(EndCrystalEntity.class, new Box(pos.add(-6.0, -6.0, -6.0), pos.add(6.0, 6.0, 6.0)), e -> true);
	}

	private boolean isAimingAt(final BlockPos block) {
		final Vec3d[] eyesPos = new Vec3d[1];
		final BlockHitResult[] hitResult = new BlockHitResult[1];

		return mc.world.getPlayers().parallelStream().filter(e -> e != mc.player).anyMatch(e -> {
			eyesPos[0] = RotationUtils.getEyesPos(e);
			hitResult[0] = mc.world.raycast(new RaycastContext(eyesPos[0], eyesPos[0].add(RotationUtils.getPlayerLookVec(e).multiply(4.5)), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, e));
			return hitResult[0] != null && hitResult[0].getBlockPos().equals(block);
		});
	}

	private boolean isAimingWithCrystal(final BlockPos block) {
		final Vec3d[] eyesPos = new Vec3d[1];
		final BlockHitResult[] hitResult = new BlockHitResult[1];

		return mc.world.getPlayers().parallelStream().filter(e -> e != mc.player).filter(e -> e.isHolding(Items.END_CRYSTAL)).anyMatch(e -> {
			eyesPos[0] = RotationUtils.getEyesPos(e);
			hitResult[0] = mc.world.raycast(new RaycastContext(eyesPos[0], eyesPos[0].add(RotationUtils.getPlayerLookVec(e).multiply(4.5)), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, e));

			return hitResult[0] != null && hitResult[0].getBlockPos().equals(block);
		});
	}
}