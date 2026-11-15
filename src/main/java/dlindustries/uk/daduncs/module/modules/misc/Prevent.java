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

import dlindustries.uk.daduncs.event.events.AttackListener;
import dlindustries.uk.daduncs.event.events.BlockBreakingListener;
import dlindustries.uk.daduncs.event.events.ItemUseListener;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.utils.BlockUtils;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.WorldUtils;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;


public final class Prevent extends Module implements ItemUseListener, AttackListener, BlockBreakingListener {
	private final bool doubleGlowstone = new bool(EncryptedString.of("Double Glowstone"), false)
			.setDescription(EncryptedString.of("Makes it so you can't charge the anchor again if it's already charged"));
	private final bool glowstoneMisplace = new bool(EncryptedString.of("Glowstone Misplace"), false)
			.setDescription(EncryptedString.of("Makes it so you can only right-click with glowstone only when aiming at an anchor"));
	private final bool antiExplode = new bool(EncryptedString.of("Anti Anchor Explode"), false)
			.setDescription(EncryptedString.of("Prevents you from interacting (exploding) a charged anchor"));
	private final bool anchorOnAnchor = new bool(EncryptedString.of("Anchor on Anchor"), false)
			.setDescription(EncryptedString.of("Makes it so you can't place an anchor on/next to another anchor unless charged"));
	private final bool glowstoneOnGlowstone = new bool(EncryptedString.of("Glowstone on Glowstone"), false)
			.setDescription(EncryptedString.of("Makes it so you can't place glowstone on/next to another glowstone"));
	private final bool obiPunch = new bool(EncryptedString.of("Obi Punch"), false)
			.setDescription(EncryptedString.of("Makes it so you can crystal faster by not letting you left click/start breaking the obsidian"));
	private final bool echestClick = new bool(EncryptedString.of("E-chest click"), false)
			.setDescription(EncryptedString.of("Makes it so you can't click on e-chests with PvP items"));

	public Prevent() {
		super(EncryptedString.of("Prevent"),
				EncryptedString.of("Prevents ghost hits, misplacing glowstone, and bad clicks"),
				-1,
				Category.MISC);
		addSettings(doubleGlowstone, glowstoneMisplace, antiExplode, anchorOnAnchor, glowstoneOnGlowstone, obiPunch, echestClick);
	}

	@Override
	public void onEnable() {
		eventManager.add(BlockBreakingListener.class, this);
		eventManager.add(AttackListener.class, this);
		eventManager.add(ItemUseListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(BlockBreakingListener.class, this);
		eventManager.remove(AttackListener.class, this);
		eventManager.remove(ItemUseListener.class, this);
		super.onDisable();
	}

	@Override
	public void onAttack(AttackEvent event) {
		if (mc.player == null || mc.crosshairTarget == null) return;
		if (mc.crosshairTarget instanceof BlockHitResult hit) {
			if (BlockUtils.isBlock(hit.getBlockPos(), Blocks.OBSIDIAN) && obiPunch.getValue() && mc.player.isHolding(Items.END_CRYSTAL))
				event.cancel();
		}
	}

	@Override
	public void onBlockBreaking(BlockBreakingEvent event) {
		if (mc.player == null || mc.crosshairTarget == null) return;
		if (mc.crosshairTarget instanceof BlockHitResult hit) {
			if (BlockUtils.isBlock(hit.getBlockPos(), Blocks.OBSIDIAN) && obiPunch.getValue() && mc.player.isHolding(Items.END_CRYSTAL))
				event.cancel();
		}
	}

	@Override
	public void onItemUse(ItemUseEvent event) {
		if (mc.player == null || mc.crosshairTarget == null) return;
		if (mc.crosshairTarget instanceof BlockHitResult hit) {
			if (antiExplode.getValue()
					&& BlockUtils.isBlock(hit.getBlockPos(), Blocks.RESPAWN_ANCHOR)
					&& BlockUtils.isAnchorCharged(hit.getBlockPos())
					&& !mc.player.isHolding(Items.GLOWSTONE)
					&& !mc.player.isHolding(Items.RESPAWN_ANCHOR)) {
				event.cancel();
			}

			if (BlockUtils.isAnchorCharged(hit.getBlockPos()) && doubleGlowstone.getValue() && mc.player.isHolding(Items.GLOWSTONE))
				event.cancel();

			if (!BlockUtils.isBlock(hit.getBlockPos(), Blocks.RESPAWN_ANCHOR) && glowstoneMisplace.getValue() && mc.player.isHolding(Items.GLOWSTONE))
				event.cancel();

			if (BlockUtils.isAnchorNotCharged(hit.getBlockPos()) && anchorOnAnchor.getValue() && mc.player.isHolding(Items.RESPAWN_ANCHOR))
				event.cancel();

			if (BlockUtils.isBlock(hit.getBlockPos(), Blocks.GLOWSTONE) && glowstoneOnGlowstone.getValue() && mc.player.isHolding(Items.GLOWSTONE))
				event.cancel();

			if (BlockUtils.isBlock(hit.getBlockPos(), Blocks.ENDER_CHEST) && echestClick.getValue() &&
					(WorldUtils.isSword(mc.player.getMainHandStack())
							|| mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL
							|| mc.player.getMainHandStack().getItem() == Items.OBSIDIAN
							|| mc.player.getMainHandStack().getItem() == Items.RESPAWN_ANCHOR
							|| mc.player.getMainHandStack().getItem() == Items.GLOWSTONE))
				event.cancel();
		}
	}
}