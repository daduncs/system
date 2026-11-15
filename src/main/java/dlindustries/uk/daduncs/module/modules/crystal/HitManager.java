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

import dlindustries.uk.daduncs.event.events.AttackListener;
import dlindustries.uk.daduncs.event.events.PacketSendListener;
import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.gui.Notice;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.range;
import dlindustries.uk.daduncs.module.setting.mode;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.managers.Recorder;
import dlindustries.uk.daduncs.mixin.ClientPlayerInteractionManagerAccessor;
import dlindustries.uk.daduncs.module.modules.mace.MaceSwap;
import dlindustries.uk.daduncs.module.modules.misc.ElytraSwap;
import dlindustries.uk.daduncs.module.modules.spear.SpearLunge;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.WorldUtils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HitManager extends Module implements AttackListener, TickListener, PacketSendListener {

    public enum ShieldDisableMode {
        NORMAL_DISABLE("Normal Disable"),
        DISABLE_STUN_SLAM("Disable + StunSlam"),
        NO_SHIELD_DISABLES("No Disable");

        private final String name;

        ShieldDisableMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final mode<ShieldDisableMode> shieldDisableMode = new mode<>(
            EncryptedString.of("Mode"),
            ShieldDisableMode.NORMAL_DISABLE,
            ShieldDisableMode.class
    ).setDescription(EncryptedString.of("Behavior when encountering opponent holding a shield"));

    private final range shieldHoldTime = new range(
            EncryptedString.of("Shield Hold Time"),
            0, 1000, 10, 300, 500
    ).setDescription(EncryptedString.of("Randomized time range (ms) target must hold shield before switching to axe"));

    private final Map<UUID, Long> shieldBlockTimers = new HashMap<>();
    private final Map<UUID, Float> requiredHoldTimes = new HashMap<>();

    public enum SlamStage {
        IDLE,
        WAITING_FOR_MACE_HIT,
        SWAPPED_RESTORE_NEXT
    }

    private SlamStage slamStage = SlamStage.IDLE;
    private int slamTicksRemaining = 0;
    private int slamTimeoutTicks = 0;
    private int slamMaceSlot = -1;
    private int slamReturnSlot = -1;
    private Entity slamTarget = null;
    private static final double MAX_TRACK_REACH = 4.5;

    private boolean actionSentThisTick = false;
    private int queuedSlot = -1;

    private static HitManager instance;

    public HitManager() {
        super(EncryptedString.of("Hit Manager"),
                EncryptedString.of("Automatically switches to appropriate weapon when attacking"),
                -1,
                Category.CRYSTAL);

        addSettings(shieldDisableMode, shieldHoldTime);
        instance = this;
    }

    public static HitManager getInstance() {
        return instance;
    }

    public boolean isSlamActive() {
        return slamStage != SlamStage.IDLE;
    }

    public Entity getSlamTarget() {
        return slamTarget;
    }

    @Override
    public void onEnable() {
        java.util.List<String> disabled = new java.util.ArrayList<>();
        for (Class<? extends Module> type : java.util.List.of(MaceSwap.class)) {
            Module module = dlindustries.uk.daduncs.System.INSTANCE.getModuleManager().getModule(type);
            if (module != null && module.isEnabled()) {
                module.setEnabled(false);
                disabled.add(module.getName().toString());
            }
        }
        if (!disabled.isEmpty()) {
            Notice.show(String.join(" and ", disabled) + (disabled.size() == 1 ? " was" : " were") + " turned off to not interfere with the Hit Manager");
        }

        eventManager.add(AttackListener.class, this);
        eventManager.add(TickListener.class, this);
        eventManager.add(PacketSendListener.class, this);
        shieldBlockTimers.clear();
        requiredHoldTimes.clear();
        resetSlamState();
        actionSentThisTick = false;
        queuedSlot = -1;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(AttackListener.class, this);
        eventManager.remove(TickListener.class, this);
        eventManager.remove(PacketSendListener.class, this);
        shieldBlockTimers.clear();
        requiredHoldTimes.clear();
        resetSlamState();
        super.onDisable();
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
        if (mc.player == null || mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.ENTITY) return;

        Entity target = ((EntityHitResult) mc.crosshairTarget).getEntity();
        Recorder tm = Recorder.getInstance();
        if (tm != null) {
            tm.recordModuleEvent("HitManager", "ON_ATTACK", "onAttack for target " + target.getName().getString() + " (id=" + target.getId() + ")", null);
        }
        swapWeapon(target);
    }

    public void swapWeapon(Entity target) {
        if (target == null || target instanceof EndCrystalEntity || !target.isAlive() || mc.player == null) return;

        if (isSlamActive()) {
            return;
        }

        ElytraSwap elytraSwap = dlindustries.uk.daduncs.System.INSTANCE.getModuleManager().getModule(ElytraSwap.class);
        if (elytraSwap != null && elytraSwap.isEnabled() && elytraSwap.isSwapping()) {
            return;
        }

        SpearLunge spearLunge = dlindustries.uk.daduncs.System.INSTANCE.getModuleManager().getModule(SpearLunge.class);
        if (spearLunge != null && spearLunge.isEnabled() && spearLunge.isLunging()) {
            return;
        }

        ItemStack currentStack = mc.player.getMainHandStack();

        if (isMace(currentStack) || isSpear(currentStack) ||
                currentStack.getItem().getTranslationKey().toLowerCase().contains("chestplate")) {
            return;
        }

        boolean isNormalDisable = shieldDisableMode.isMode(ShieldDisableMode.NORMAL_DISABLE);
        boolean isStunSlamMode = shieldDisableMode.isMode(ShieldDisableMode.DISABLE_STUN_SLAM);
        boolean isHoldingAxe = isAxe(currentStack);

        if (isStunSlamMode && isHoldingAxe) {
            int maceSlot = findMaceSlot();
            int axeSlot = findAxeSlot();

            if (maceSlot != -1 && axeSlot != -1) {
                if (slamStage == SlamStage.IDLE) {
                    int returnSlot = axeSlot;
                    queueSlam(target, maceSlot, returnSlot, 1);
                }
                return;
            }
        }

        if ((isNormalDisable || isStunSlamMode) && target instanceof LivingEntity livingTarget) {
            boolean isShielding = isHoldingShield(livingTarget);

            if (isShielding) {
                long currentTime = System.currentTimeMillis();
                UUID targetUuid = target.getUuid();

                if (!shieldBlockTimers.containsKey(targetUuid)) {
                    shieldBlockTimers.put(targetUuid, currentTime);
                    requiredHoldTimes.put(targetUuid, shieldHoldTime.getRandomValueFloat());
                }

                long activeShieldHoldTime = currentTime - shieldBlockTimers.get(targetUuid);
                float requiredTime = requiredHoldTimes.getOrDefault(targetUuid, (float) shieldHoldTime.getMin());

                if (activeShieldHoldTime >= requiredTime) {
                    int axeSlot = findAxeSlot();
                    if (axeSlot != -1) {
                        if (!isHoldingAxe) {
                            selectSlot(axeSlot);
                        }

                        if (isStunSlamMode) {
                            int maceSlot = findMaceSlot();
                            if (maceSlot != -1 && slamStage == SlamStage.IDLE) {
                                int returnSlot = axeSlot;
                                queueSlam(target, maceSlot, returnSlot, 1);
                            }
                        }

                        return;
                    }
                }
            } else {
                shieldBlockTimers.remove(target.getUuid());
                requiredHoldTimes.remove(target.getUuid());
            }
        }

        if (WorldUtils.isSword(currentStack) || isAxe(currentStack) || isMace(currentStack) || isSpear(currentStack)) return;

        int swordSlot = findSwordSlot();
        if (swordSlot != -1) {
            selectSlot(swordSlot);
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null || mc.getNetworkHandler() == null) {
            resetSlamState();
            return;
        }

        actionSentThisTick = false;
        checkShields();

        ElytraSwap elytraSwap = dlindustries.uk.daduncs.System.INSTANCE.getModuleManager().getModule(ElytraSwap.class);
        if (elytraSwap != null && elytraSwap.isEnabled() && elytraSwap.isSwapping()) {
            return;
        }

        SpearLunge spearLunge = dlindustries.uk.daduncs.System.INSTANCE.getModuleManager().getModule(SpearLunge.class);
        if (spearLunge != null && spearLunge.isEnabled() && spearLunge.isLunging()) {
            return;
        }

        if (queuedSlot != -1 && !actionSentThisTick && !AntibanbyDaduncs.isActionSent() && !AntibanbyDaduncs.isSlotPending()) {
            int slotToSelect = queuedSlot;
            queuedSlot = -1;
            Recorder tm = Recorder.getInstance();
            if (tm != null) {
                tm.recordModuleEvent("HitManager", "EXEC_QUEUED_SLOT", "Executing queuedSlot " + slotToSelect, null);
            }
            selectSlot(slotToSelect);
        }

        if (slamStage == SlamStage.IDLE) return;

        if (slamTarget == null || slamTarget.isRemoved() || !slamTarget.isAlive()) {
            restoreAndReset();
            return;
        }

        double eyeToBox = WorldUtils.getEyeToBoxDistance(mc.player, slamTarget);
        if (eyeToBox > MAX_TRACK_REACH) {
            restoreAndReset();
            return;
        }

        switch (slamStage) {
            case WAITING_FOR_MACE_HIT -> {
                slamTicksRemaining--;
                if (slamTicksRemaining > 0) return;

                if (actionSentThisTick || AntibanbyDaduncs.isActionSent()) {
                    slamTicksRemaining = 1;
                    return;
                }

                if (mc.player.isUsingItem() && mc.interactionManager != null) {
                    mc.interactionManager.stopUsingItem(mc.player);
                    slamTicksRemaining = 1;
                    return;
                }

                if (mc.player.getInventory().getSelectedSlot() != slamMaceSlot) {
                    selectSlot(slamMaceSlot);
                    if (AntibanbyDaduncs.isSlotPending() || mc.player.getInventory().getSelectedSlot() != slamMaceSlot) {
                        slamTicksRemaining = 1;
                        return;
                    }
                }

                slamTimeoutTicks--;

                double safeReach = 2.90;
                if (!mc.player.isOnGround() && mc.player.getVelocity().y < 0) {
                    safeReach -= Math.min(0.30, Math.abs(mc.player.getVelocity().y));
                }
                Vec3d vel = mc.player.getVelocity();
                double horizSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
                if (horizSpeed > 0.05) {
                    safeReach -= Math.min(0.20, horizSpeed * 0.6);
                }

                boolean inSafeReach = eyeToBox <= safeReach;
                boolean rayHits = WorldUtils.doesRaycastHitEntity(mc.player, slamTarget, safeReach);

                if (inSafeReach && rayHits && AntibanbyDaduncs.canAttackNow()) {
                    Recorder tm = Recorder.getInstance();
                    if (tm != null) {
                        tm.recordModuleEvent("HitManager", "MACE_SLAM_ATTACK", "Attacking target " + slamTarget.getName().getString() + " with mace slot " + slamMaceSlot + " (reach=" + String.format(java.util.Locale.ROOT, "%.2f", eyeToBox) + ")", null);
                    }

                    mc.interactionManager.attackEntity(mc.player, slamTarget);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    actionSentThisTick = true;

                    slamStage = SlamStage.SWAPPED_RESTORE_NEXT;
                } else if (slamTimeoutTicks <= 0) {
                    restoreAndReset();
                }
            }
            case SWAPPED_RESTORE_NEXT -> {
                if (actionSentThisTick || AntibanbyDaduncs.isActionSent() || AntibanbyDaduncs.isSlotPending()) {
                    return;
                }
                restoreAndReset();
            }
            case IDLE -> {}
        }
    }

    private void queueSlam(Entity target, int maceSlot, int returnSlot, int delayTicks) {
        this.slamTarget = target;
        this.slamMaceSlot = maceSlot;
        this.slamReturnSlot = returnSlot;
        this.slamTicksRemaining = Math.max(1, delayTicks);
        this.slamTimeoutTicks = 5;
        this.slamStage = SlamStage.WAITING_FOR_MACE_HIT;
        Recorder tm = Recorder.getInstance();
        if (tm != null) {
            tm.recordModuleEvent("HitManager", "SCHEDULE_SLAM", "Scheduled stun slam on " + target.getName().getString() + ", maceSlot=" + maceSlot + ", returnSlot=" + returnSlot + ", delayTicks=" + delayTicks, null);
        }
    }

    private void restoreAndReset() {
        if (slamReturnSlot >= 0 && slamReturnSlot <= 8) {
            Recorder tm = Recorder.getInstance();
            if (tm != null) {
                tm.recordModuleEvent("HitManager", "RESTORE_SLOT", "Restoring slot to slamReturnSlot " + slamReturnSlot, null);
            }
            if (mc.currentScreen == null && !actionSentThisTick && !AntibanbyDaduncs.isActionSent() && !AntibanbyDaduncs.isSlotPending() && !mc.player.isUsingItem()) {
                selectSlot(slamReturnSlot);
            } else {
                queuedSlot = slamReturnSlot;
            }
        }
        resetSlamState();
    }

    private void selectSlot(int slot) {
        if (mc.player == null || mc.getNetworkHandler() == null || mc.currentScreen != null || slot < 0 || slot > 8) return;

        if (mc.player.getInventory().getSelectedSlot() == slot) {
            queuedSlot = -1;
            return;
        }

        if (AntibanbyDaduncs.isSlotPending() && AntibanbyDaduncs.getPendingSlot() == slot) {
            queuedSlot = -1;
            return;
        }

        Recorder tm = Recorder.getInstance();
        if (actionSentThisTick || AntibanbyDaduncs.isActionSent() || mc.player.isUsingItem()) {
            queuedSlot = slot;
            if (tm != null) {
                tm.recordModuleEvent("HitManager", "QUEUE_SLOT", "Queued slot " + slot + " because actionSentThisTick=" + actionSentThisTick + " or packetGuardAction=" + AntibanbyDaduncs.isActionSent(), null);
            }
        } else {
            if (tm != null) {
                tm.recordModuleEvent("HitManager", "SYNC_SLOT", "Syncing selected slot to " + slot, null);
            }
            mc.player.getInventory().setSelectedSlot(slot);
            if (mc.interactionManager != null) {
                ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).syncSlot();
            } else {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }
            queuedSlot = -1;
        }
    }

    public java.util.Map<String, Object> recorder() {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("mode", shieldDisableMode.getMode().toString());
        map.put("slamStage", slamStage.name());
        map.put("slamTicksRemaining", slamTicksRemaining);
        map.put("slamTimeoutTicks", slamTimeoutTicks);
        map.put("slamMaceSlot", slamMaceSlot);
        map.put("slamReturnSlot", slamReturnSlot);
        map.put("slamTarget", slamTarget != null ? slamTarget.getName().getString() + " (id=" + slamTarget.getId() + ")" : "null");
        map.put("actionSentThisTick", actionSentThisTick);
        map.put("queuedSlot", queuedSlot);
        map.put("activeShieldTimersCount", shieldBlockTimers.size());
        return map;
    }

    private void resetSlamState() {
        slamStage = SlamStage.IDLE;
        slamTicksRemaining = 0;
        slamTimeoutTicks = 0;
        slamMaceSlot = -1;
        slamReturnSlot = -1;
        slamTarget = null;
    }

    private void checkShields() {
        if (mc.world == null) return;
        long currentTime = System.currentTimeMillis();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive()) continue;

            UUID uuid = player.getUuid();
            if (isHoldingShield(player)) {
                if (!shieldBlockTimers.containsKey(uuid)) {
                    shieldBlockTimers.put(uuid, currentTime);
                    requiredHoldTimes.put(uuid, shieldHoldTime.getRandomValueFloat());
                }
            } else {
                shieldBlockTimers.remove(uuid);
                requiredHoldTimes.remove(uuid);
            }
        }

        if (mc.crosshairTarget instanceof EntityHitResult entityHit &&
                entityHit.getEntity() instanceof LivingEntity living &&
                living != mc.player && living.isAlive()) {
            UUID uuid = living.getUuid();
            if (isHoldingShield(living)) {
                if (!shieldBlockTimers.containsKey(uuid)) {
                    shieldBlockTimers.put(uuid, currentTime);
                    requiredHoldTimes.put(uuid, shieldHoldTime.getRandomValueFloat());
                }
            } else {
                shieldBlockTimers.remove(uuid);
                requiredHoldTimes.remove(uuid);
            }
        }

        shieldBlockTimers.keySet().removeIf(uuid -> mc.world.getPlayerByUuid(uuid) == null &&
                !(mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity().getUuid().equals(uuid)));
        requiredHoldTimes.keySet().removeIf(uuid -> !shieldBlockTimers.containsKey(uuid));
    }

    private boolean isHoldingShield(LivingEntity entity) {
        if (entity.isBlocking()) {
            return true;
        }
        ItemStack activeItem = entity.getActiveItem();
        return entity.isUsingItem() && !activeItem.isEmpty() && (activeItem.getItem() instanceof ShieldItem || activeItem.isOf(Items.SHIELD));
    }

    private boolean isAxe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return WorldUtils.isAxe(stack) || stack.getItem() instanceof AxeItem;
    }

    private boolean isMace(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.isOf(Items.MACE);
    }

    private boolean isSpear(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem().getTranslationKey().toLowerCase().contains("spear") || stack.isOf(Items.TRIDENT);
    }

    private int findAxeSlot() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (isAxe(stack)) {
                return slot;
            }
        }
        return -1;
    }

    private int findSwordSlot() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (WorldUtils.isSword(stack)) {
                return slot;
            }
        }
        return -1;
    }

    private int findMaceSlot() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (isMace(stack)) {
                return slot;
            }
        }
        return -1;
    }
}
