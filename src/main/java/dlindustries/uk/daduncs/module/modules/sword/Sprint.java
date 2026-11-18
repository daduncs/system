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

package dlindustries.uk.daduncs.module.modules.sword;

import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.imixin.IKeyBinding;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.utils.EncryptedString;
import net.minecraft.entity.effect.StatusEffects;

public final class Sprint extends Module implements TickListener {
    private boolean appliedSprint = false;

    public Sprint() {
        super(EncryptedString.of("Sprint"), EncryptedString.of("Keeps you sprinting at all times"), -1, Category.SWORD);
    }

    @Override
    public void onEnable() {
        appliedSprint = false;
        // Run after AutoWTap and other movement-key modules on the same tick.
        eventManager.add(TickListener.class, this, -100);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        releaseSprint();
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.options == null) return;
        if (mc.currentScreen != null) {
            releaseSprint();
            return;
        }

        if (canSprintSafely()) {
            boolean toggleSprint = mc.options.getSprintToggled() != null && mc.options.getSprintToggled().getValue();
            if (toggleSprint) {
                if (!mc.options.sprintKey.isPressed()) {
                    ((IKeyBinding) mc.options.sprintKey).setRawPressed(true);
                }
            } else {
                mc.options.sprintKey.setPressed(true);
            }
            appliedSprint = true;
        } else {
            releaseSprint();
        }
    }

    private void releaseSprint() {
        if (!appliedSprint || mc.options == null || mc.options.sprintKey == null) {
            appliedSprint = false;
            return;
        }
        boolean toggleSprint = mc.options.getSprintToggled() != null && mc.options.getSprintToggled().getValue();
        if (toggleSprint) {
            if (!((IKeyBinding) mc.options.sprintKey).isActuallyPressed()) {
                ((IKeyBinding) mc.options.sprintKey).setRawPressed(false);
            }
        } else {
            ((IKeyBinding) mc.options.sprintKey).resetPressed();
        }
        appliedSprint = false;
    }

    private boolean canSprintSafely() {
        if (mc.player == null || mc.options == null) return false;
        if (!mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()) return false;
        if (mc.player.isSneaking()) return false;
        if (mc.player.horizontalCollision) return false;

        if (mc.player.getHungerManager() != null && !mc.player.getHungerManager().canSprint()) {
            if (!mc.player.getAbilities().allowFlying) {
                return false;
            }
        }

        if (mc.player.hasStatusEffect(StatusEffects.BLINDNESS)) {
            return false;
        }

        if (mc.player.hasVehicle()) {
            return false;
        }

        return true;
    }
}
