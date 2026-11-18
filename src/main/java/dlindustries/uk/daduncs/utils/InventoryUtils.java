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

package dlindustries.uk.daduncs.utils;

import dlindustries.uk.daduncs.mixin.ClientPlayerInteractionManagerAccessor;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

import static dlindustries.uk.daduncs.System.mc;

public final class InventoryUtils {

	public static boolean isPlayerInventoryOpenQueued() {
		return canOpenPlayerInventory()
				&& ((dlindustries.uk.daduncs.mixin.KeyBindingAccessor) mc.options.inventoryKey).getTimesPressed() > 0;
	}

	/** A survival inventory may only open when it is the screen vanilla would select. */
	public static boolean canOpenPlayerInventory() {
		return mc.player != null && mc.player.isAlive() && !mc.player.isSpectator()
				&& mc.currentScreen == null && mc.getOverlay() == null && mc.isWindowFocused()
				&& mc.interactionManager != null && !mc.player.isInCreativeMode()
				&& !mc.interactionManager.hasRidingInventory()
				&& mc.player.currentScreenHandler == mc.player.playerScreenHandler;
	}

	public static boolean setInvSlot(int slot) {
		if (!dlindustries.uk.daduncs.managers.AntibanbyDaduncs.canSwapSlotNow()
				|| dlindustries.uk.daduncs.managers.AntibanbyDaduncs.isSlotPending()) return false;
		if (mc.player == null || mc.interactionManager == null) return false;
		if (slot < 0 || slot > 8) return false;
		if (mc.currentScreen != null) return false;
		mc.player.getInventory().setSelectedSlot(slot);
		((ClientPlayerInteractionManagerAccessor) mc.interactionManager).syncSlot();
		return true;
	}

	public static boolean selectItemFromHotbar(Predicate<Item> item) {
		if (mc.player == null) return false;
		PlayerInventory inv = mc.player.getInventory();

		for (int i = 0; i < 9; i++) {
			ItemStack itemStack = inv.getStack(i);
			if (!item.test(itemStack.getItem()))
				continue;

			if (inv.getSelectedSlot() == i)
				return true;
			return setInvSlot(i);
		}

		return false;
	}

	public static int getMaceSlot() {
		Inventory inv = mc.player.getInventory();
		for (int i = 0; i < 9; i++) {
			if (inv.getStack(i).getItem() instanceof MaceItem) {
				return i;
			}
		}
		return -1;
	}

	public static boolean hasMaceInHotbar() {
		return getMaceSlot() != -1;
	}

	public static int findMace(RegistryKey<Enchantment> enchantKey, boolean allowNoEnch) {
		if (mc.player == null) return -1;
		if (enchantKey != null) {
			for (int i = 0; i < 9; i++) {
				ItemStack stack = mc.player.getInventory().getStack(i);
				if (stack.getItem() instanceof MaceItem) {
					for (RegistryEntry<Enchantment> enchant : stack.getEnchantments().getEnchantments()) {
						if (enchant.matchesKey(enchantKey)) {
							return i;
						}
					}
				}
			}
		}

		if (allowNoEnch) {
			for (int i = 0; i < 9; i++) {
				ItemStack stack = mc.player.getInventory().getStack(i);
				if (stack.getItem() instanceof MaceItem) {
					return i;
				}
			}
		}

		return -1;
	}

	public static boolean selectItemFromHotbar(Item item) {
		return selectItemFromHotbar(i -> i == item);
	}

	public static boolean hasItemInHotbar(Predicate<Item> item) {
		PlayerInventory inv = mc.player.getInventory();

		for (int i = 0; i < 9; i++) {
			ItemStack itemStack = inv.getStack(i);
			if (item.test(itemStack.getItem()))
				return true;
		}
		return false;
	}

	public static int countItem(Predicate<Item> item) {
		PlayerInventory inv = mc.player.getInventory();

		int count = 0;

		for (int i = 0; i < 36; i++) {
			ItemStack itemStack = inv.getStack(i);
			if (item.test(itemStack.getItem()))
				count += itemStack.getCount();
		}

		return count;
	}

	public static int countItemExceptHotbar(Predicate<Item> item) {
		PlayerInventory inv = mc.player.getInventory();

		int count = 0;

		for (int i = 9; i < 36; i++) {
			ItemStack itemStack = inv.getStack(i);
			if (item.test(itemStack.getItem()))
				count += itemStack.getCount();
		}

		return count;
	}

	public static int getSwordSlot() {
		Inventory playerInventory = mc.player.getInventory();

		for (int itemIndex = 0; itemIndex < 9; itemIndex++) {
			if (WorldUtils.isSword(playerInventory.getStack(itemIndex)))
				return itemIndex;
		}

		return -1;
	}

	public static boolean selectSword() {
		int itemIndex = getSwordSlot();

		if (itemIndex != -1) {
			InventoryUtils.setInvSlot(itemIndex);
			return true;
		} else return false;
	}

	public static int findSplash(StatusEffect type, int duration, int amplifier) {
		PlayerInventory inv = mc.player.getInventory();
		StatusEffectInstance potion = new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(type), duration, amplifier);

		for (int i = 0; i < 9; i++) {
			ItemStack itemStack = inv.getStack(i);

			if (!(itemStack.getItem() instanceof SplashPotionItem))
				continue;

			String s = itemStack.get(DataComponentTypes.POTION_CONTENTS).getEffects().toString();
			if (s.contains(potion.toString())) {
				return i;
			}
		}

		return -1;
	}

	public static boolean isThatSplash(StatusEffect type, int duration, int amplifier, ItemStack itemStack) {
		StatusEffectInstance potion = new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(type), duration, amplifier);

		return itemStack.getItem() instanceof SplashPotionItem &&
				itemStack.get(DataComponentTypes.POTION_CONTENTS).getEffects().toString().contains(potion.toString());
	}

	public static int findTotemSlot() {
		assert mc.player != null;
		PlayerInventory inv = mc.player.getInventory();
		for (int index = 9; index < 36; index++) {
			if (inv.getStack(index).getItem() == Items.TOTEM_OF_UNDYING)
				return index;
		}
		return -1;
	}

	public static boolean selectAxe() {
		int itemIndex = getAxeSlot();

		if (itemIndex != -1) {
			setInvSlot(itemIndex);
			return true;
		} else return false;
	}

	public static int findRandomTotemSlot() {
		PlayerInventory inventory = mc.player.getInventory();
		Random random = new Random();
		List<Integer> totemIndexes = new ArrayList<>();

		for(int i = 9; i < 36; i++) {
			if(inventory.getStack(i).getItem() == Items.TOTEM_OF_UNDYING)
				totemIndexes.add(i);
		}

		if(!totemIndexes.isEmpty()) {
			return totemIndexes.get(random.nextInt(totemIndexes.size()));
		} else return -1;
	}

	public static int findRandomPot(String potion) {
		PlayerInventory inventory = mc.player.getInventory();
		Random random = new Random();

		int slotIndex = random.nextInt(27) + 9;
		for (int i = 0; i < 27; i++) {
			int index = (slotIndex + i) % 36;
			ItemStack itemStack = inventory.getStack(index);
			if (itemStack.getItem() instanceof SplashPotionItem && (index != 36 || index != 37 || index != 38 || index != 39)) {
				if (!itemStack.get(DataComponentTypes.POTION_CONTENTS).getEffects().toString().contains(potion.toString()))
					return -1;

				return index;
			}
		}
		return -1;
	}

	public static int findPot(StatusEffect effect, int duration, int amplifier) {
		assert mc.player != null;
		PlayerInventory inv = mc.player.getInventory();
		StatusEffectInstance instance = new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(effect), duration, amplifier);

		for (int index = 9; index < 34; index++)
			if (inv.getStack(index).getItem() instanceof SplashPotionItem)
				if (inv.getStack(index).get(DataComponentTypes.POTION_CONTENTS).getEffects().toString().contains(instance.toString()))
					return index;

		return -1;
	}

	public static List<Integer> getEmptyHotbarSlots() {
		PlayerInventory inventory = mc.player.getInventory();
		List<Integer> slots = new ArrayList<>();

		for (int i = 0; i < 9; i++) {
			if (inventory.getStack(i).isEmpty())
				slots.add(i);
			else if (slots.contains(i) && !inventory.getStack(i).isEmpty())
				slots.remove(i);
		}

		return slots;
	}

	public static int getAxeSlot() {
		Inventory playerInventory = mc.player.getInventory();

		for (int itemIndex = 0; itemIndex < 9; itemIndex++) {
			if (WorldUtils.isAxe(playerInventory.getStack(itemIndex)))
				return itemIndex;
		}

		return -1;
	}

	public static int countItem(Item item) {
		return countItem(i -> i == item);
	}
}
