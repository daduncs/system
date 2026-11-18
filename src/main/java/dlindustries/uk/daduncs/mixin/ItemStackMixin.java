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

import dlindustries.uk.daduncs.module.modules.render.NoBounce;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(ItemStack.class)
public class ItemStackMixin {

	@org.spongepowered.asm.mixin.Unique
	private net.minecraft.item.Item system$usedOnBlock;

	@Inject(method = "useOnBlock", at = @At("HEAD"))
	private void beforeItemUseOnBlock(net.minecraft.item.ItemUsageContext context,
			CallbackInfoReturnable<net.minecraft.util.ActionResult> cir) {
		system$usedOnBlock = ((ItemStack) (Object) this).getItem();
	}

	@Inject(method = "useOnBlock", at = @At("RETURN"))
	private void afterItemUseOnBlock(net.minecraft.item.ItemUsageContext context,
			CallbackInfoReturnable<net.minecraft.util.ActionResult> cir) {
		// A successful block callback (for example opening a chest) is not item use.
		// Only this item callback can complete an item-on-block request.
		dlindustries.uk.daduncs.managers.InputContext.recordItemResult(
				context.getHand(), system$usedOnBlock.getRegistryEntry().registryKey(), cir.getReturnValue());
	}

	@Inject(method = "getBobbingAnimationTime", at = @At("HEAD"), cancellable = true)
	private void removeBounceAnimation(CallbackInfoReturnable<Integer> cir) {
		if (((ItemStack) (Object) this).isOf(Items.END_CRYSTAL) && NoBounce.active()) {
			cir.setReturnValue(0);
		}
	}
}
