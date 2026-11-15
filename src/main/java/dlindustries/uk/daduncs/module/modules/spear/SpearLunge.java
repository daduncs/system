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

package dlindustries.uk.daduncs.module.modules.spear;

import dlindustries.uk.daduncs.event.events.AttackListener;
import dlindustries.uk.daduncs.event.events.PacketSendListener;
import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.mixin.ClientPlayerInteractionManagerAccessor;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.crystal.HitManager;
import dlindustries.uk.daduncs.module.setting.bind;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.mode;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.KeyUtils;
import dlindustries.uk.daduncs.utils.WorldUtils;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PiercingWeaponComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class SpearLunge extends Module implements AttackListener, TickListener, PacketSendListener {

    public enum LungeMode {
        ATTACK("Attack"),
        KEYPRESS("Keypress");

        private final String name;

        LungeMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum CooldownMode {
        VANILLA("Vanilla"),
        PAPER("Paper");

        private final String name;

        CooldownMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final mode<LungeMode> mode = new mode<>(
            EncryptedString.of("Mode"),
            LungeMode.ATTACK,
            LungeMode.class
    ).setDescription(EncryptedString.of("Trigger lunge on attack or via activate key"));

    private final bind activateKey = new bind(
            EncryptedString.of("Activate Key"),
            -1,
            false
    ).setDescription(EncryptedString.of("Key to trigger spear lunge in Keypress mode"));

    private final mode<CooldownMode> cooldown = new mode<>(
            EncryptedString.of("Cooldown"),
            CooldownMode.VANILLA,
            CooldownMode.class
    ).setDescription(EncryptedString.of("Cooldown check logic (Vanilla or Paper)"));

    private final number cooldownThreshold = new number(
            EncryptedString.of("Cooldown %"),
            0, 100, 85, 1
    ).setDescription(EncryptedString.of("Minimum attack strength progress required before lunging"));

    private final bool weaponsOnly = new bool(
            EncryptedString.of("Weapons Only"),
            true
    ).setDescription(EncryptedString.of("Only lunge when holding a sword, axe, or mace in Attack mode"));

    private final bool switchBack = new bool(
            EncryptedString.of("Switch Back"),
            true
    ).setDescription(EncryptedString.of("Switches back to original hotbar slot after lunging"));

    private int originalSlot = -1;
    private boolean shouldSwitchBack = false;
    private boolean keyWasDown = false;
    private static SpearLunge instance;
    private boolean actionSentThisTick = false;
    private int swapTick = -1;

    public SpearLunge() {
        super(
                EncryptedString.of("Spear Lunge"),
                EncryptedString.of("Uses the spear's lunge ability to move quickly"),
                -1,
                Category.SPEAR
        );
        addSettings(mode, activateKey, cooldown, cooldownThreshold, weaponsOnly, switchBack);
        instance = this;
    }

    public static SpearLunge getInstance() {
        return instance;
    }

    public boolean isLunging() {
        return shouldSwitchBack;
    }

    @Override
    public void onEnable() {
        eventManager.add(AttackListener.class, this);
        eventManager.add(TickListener.class, this);
        eventManager.add(PacketSendListener.class, this);
        resetState();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(AttackListener.class, this);
        eventManager.remove(TickListener.class, this);
        eventManager.remove(PacketSendListener.class, this);

        if (originalSlot != -1 && mc.player != null && mc.currentScreen == null && AntibanbyDaduncs.canSwapSlotNow()) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
            if (mc.interactionManager != null) {
                ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).syncSlot();
            }
        }
        resetState();
        super.onDisable();
    }

    private void resetState() {
        originalSlot = -1;
        shouldSwitchBack = false;
        keyWasDown = false;
        actionSentThisTick = false;
        swapTick = -1;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (mc.player == null) return;
        Packet<?> packet = event.packet;

        if (packet instanceof ClientTickEndC2SPacket) {
            actionSentThisTick = false;
        } else if (isActionPacket(packet)) {
            actionSentThisTick = true;
        }
    }

    private boolean isActionPacket(Packet<?> packet) {
        return packet instanceof PlayerInteractEntityC2SPacket ||
                packet instanceof PlayerInteractBlockC2SPacket ||
                packet instanceof PlayerInteractItemC2SPacket ||
                packet instanceof PlayerActionC2SPacket;
    }

    @Override
    public void onAttack(AttackEvent event) {
        if (!mode.isMode(LungeMode.ATTACK)) return;
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (mc.player.isSpectator() || mc.player.isUsingItem() || mc.player.hasVehicle()) return;
        if (isHoldingSpearWithLunge()) return;
        if (!isHoldingAllowed()) return;
        if (shouldSwitchBack) return;

        if (actionSentThisTick || !AntibanbyDaduncs.canAttackNow() || AntibanbyDaduncs.isSlotPending()) return;

        HitManager hitManager = HitManager.getInstance();
        if (hitManager != null && hitManager.isEnabled() && hitManager.isSlamActive()) return;

        int spearSlot = getSpearSlotWithLunge();
        if (spearSlot == -1) return;
        if (!isCooledDown(spearSlot)) return;

        event.cancel();

        originalSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(spearSlot);
        if (mc.interactionManager != null) {
            ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).syncSlot();
        } else if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(spearSlot));
        }

        sendStabPacket();

        shouldSwitchBack = true;
        swapTick = mc.player.age;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) {
            resetState();
            return;
        }

        actionSentThisTick = false;

        if (shouldSwitchBack) {
            if (mc.player.age > swapTick && mc.currentScreen == null) {
                if (!actionSentThisTick && AntibanbyDaduncs.canSwapSlotNow() && !AntibanbyDaduncs.isSlotPending()) {
                    if (switchBack.getValue() && originalSlot >= 0 && originalSlot <= 8) {
                        mc.player.getInventory().setSelectedSlot(originalSlot);
                        if (mc.interactionManager != null) {
                            ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).syncSlot();
                        } else if (mc.getNetworkHandler() != null) {
                            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
                        }
                    }
                    originalSlot = -1;
                    shouldSwitchBack = false;
                }
            }
        }

        if (mode.isMode(LungeMode.KEYPRESS) && mc.currentScreen == null
                && !mc.player.isSpectator() && !mc.player.isUsingItem() && !mc.player.hasVehicle()) {
            boolean keyDown = KeyUtils.isKeyPressed(activateKey.getKey());
            if (keyDown && !keyWasDown) {
                if (isHoldingSpearWithLunge()) {
                    int heldSlot = mc.player.getInventory().getSelectedSlot();
                    if (!actionSentThisTick && AntibanbyDaduncs.canAttackNow() && isCooledDown(heldSlot)) {
                        sendStabPacket();
                    }
                    keyWasDown = true;
                    return;
                }

                if (!shouldSwitchBack && !actionSentThisTick && AntibanbyDaduncs.canAttackNow() && !AntibanbyDaduncs.isSlotPending()) {
                    int spearSlot = getSpearSlotWithLunge();
                    if (spearSlot != -1 && isCooledDown(spearSlot)) {
                        HitManager hitManager = HitManager.getInstance();
                        if (hitManager == null || !hitManager.isEnabled() || !hitManager.isSlamActive()) {
                            originalSlot = mc.player.getInventory().getSelectedSlot();
                            mc.player.getInventory().setSelectedSlot(spearSlot);
                            if (mc.interactionManager != null) {
                                ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).syncSlot();
                            } else if (mc.getNetworkHandler() != null) {
                                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(spearSlot));
                            }

                            sendStabPacket();

                            shouldSwitchBack = true;
                            swapTick = mc.player.age;
                        }
                    }
                }
            }
            keyWasDown = keyDown;
        }
    }

    private void sendStabPacket() {
        if (mc.player == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;
        PiercingWeaponComponent piercing = mc.player.getMainHandStack().get(DataComponentTypes.PIERCING_WEAPON);
        if (piercing == null || !AntibanbyDaduncs.canAttackNow()) return;

        mc.interactionManager.attackWithPiercingWeapon(piercing);
        mc.player.swingHand(Hand.MAIN_HAND);
        actionSentThisTick = true;
    }

    private boolean isCooledDown(int spearSlot) {
        if (mc.player == null) return false;
        if (spearSlot >= 0 && spearSlot < 9) {
            ItemStack spearStack = mc.player.getInventory().getStack(spearSlot);
            if (mc.player.getItemCooldownManager().isCoolingDown(spearStack)) {
                return false;
            }
        }
        if (cooldown.isMode(CooldownMode.VANILLA)) {
            float required = cooldownThreshold.getValueFloat() / 100.0F;
            return mc.player.getAttackCooldownProgress(0.0F) >= required;
        } else if (cooldown.isMode(CooldownMode.PAPER)) {
            return mc.player.getAttackCooldownProgress(0.0F) >= 1.0F;
        }
        return true;
    }

    private boolean isHoldingAllowed() {
        if (mc.player == null) return false;
        if (!weaponsOnly.getValue()) return true;

        ItemStack mainHand = mc.player.getMainHandStack();
        if (mainHand.isEmpty()) return false;

        return WorldUtils.isWeapon(mainHand) ||
                WorldUtils.isSword(mainHand) ||
                WorldUtils.isAxe(mainHand) ||
                mainHand.isOf(Items.MACE);
    }

    private int getSpearSlotWithLunge() {
        if (mc.player == null) return -1;
        int fallbackSpearSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isSpear(stack)) {
                if (hasLungeEnchantment(stack)) {
                    return i;
                }
                if (fallbackSpearSlot == -1) {
                    fallbackSpearSlot = i;
                }
            }
        }
        return fallbackSpearSlot;
    }

    private boolean isHoldingSpearWithLunge() {
        if (mc.player == null) return false;
        ItemStack mainHand = mc.player.getMainHandStack();
        return isSpear(mainHand) && hasLungeEnchantment(mainHand);
    }

    private boolean hasLungeEnchantment(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (RegistryEntry<Enchantment> enchant : stack.getEnchantments().getEnchantments()) {
            if (enchant.matchesKey(Enchantments.LUNGE)) return true;
        }
        return false;
    }

    private boolean isSpear(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.contains(DataComponentTypes.PIERCING_WEAPON);
    }

    public ItemStack spearcd() {
        if (!isEnabled() || !cooldown.isMode(CooldownMode.PAPER) || mc.player == null) {
            return ItemStack.EMPTY;
        }

        int spearSlot = getSpearSlotWithLunge();
        return spearSlot == -1 ? ItemStack.EMPTY : mc.player.getInventory().getStack(spearSlot);
    }
}
