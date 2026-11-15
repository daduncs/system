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
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.utils.BlockUtils;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.KeyUtils;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

public final class DoubleAnchor extends Module implements TickListener {
	public DoubleAnchor() {
		super(EncryptedString.of("Double Anchor"),
				EncryptedString.of("Helps you do the air place/double anchor - required for airplace mode to work in anchormacro"),
				-1,
				Category.CRYSTAL);
	}

	private BlockPos pos;

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		pos = null;
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		pos = null;
		super.onDisable();
	}

	public static boolean doAirplace(BlockHitResult hit) {
		net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
		if (mc.player == null || mc.interactionManager == null || hit == null) {
			return false;
		}
		if (dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isSlotPending()
				|| dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isActionSent()
				|| dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isMovementSent()
				|| !dlindustries.uk.daduncs.managers.AntibanbyDaduncs.canBlockActionNow()) {
			return false;
		}
		if (mc.player.isUsingItem() || !mc.player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR)) {
			return false;
		}

		// Two identical interacts in one tick (same pos/face/cursor), exactly like a vanilla
		// double-click processed in the same tick: r1 explodes the charged anchor server-side,
		// r2 lands on the now-air position and places the new anchor there (airplace).
		ActionResult r1 = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
		ActionResult r2 = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
		if (r1.isAccepted() || r2.isAccepted()) {
			mc.player.swingHand(Hand.MAIN_HAND);
			return true;
		}
		return false;
	}

	@Override
	public void onTick() {
		if (mc.currentScreen != null || mc.player == null || mc.world == null) {
			pos = null;
			return;
		}

		AnchorMacro anchorMacro = System.INSTANCE.getModuleManager().getModule(AnchorMacro.class);
		if (anchorMacro != null && anchorMacro.isEnabled()) {
			pos = null;
			return;
		}

		if (!KeyUtils.isKeyPressed(GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
			pos = null;
			return;
		}

		if (mc.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult) {
			mc.options.useKey.setPressed(false);
			return;
		}

		if (mc.crosshairTarget instanceof BlockHitResult h) {
			BlockPos hitPos = h.getBlockPos();

			if (pos != null) {
				if (hitPos.equals(pos) || (hitPos.equals(pos.down()) && h.getSide() == net.minecraft.util.math.Direction.UP)) {
					mc.options.useKey.setPressed(false);
				}
			}

			if (BlockUtils.isBlock(hitPos, net.minecraft.block.Blocks.RESPAWN_ANCHOR)) {
				mc.options.useKey.setPressed(false);

				if (mc.player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR) && BlockUtils.isAnchorCharged(hitPos)) {
					if (hitPos.equals(pos)) {
						return;
					}
					pos = hitPos;
					doAirplace(h);
				}
			}
		}
	}
}
