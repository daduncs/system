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

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor
	boolean getJumping();

	@Accessor("lastDamageSource")
	DamageSource getLastDamageSource();

	@Accessor("lastDamageSource")
	void setLastDamageSource(DamageSource source);

	@Accessor("lastDamageTime")
	long getLastDamageTime();

	@Accessor("lastDamageTime")
	void setLastDamageTime(long time);

	@Accessor("lastBlockPos")
	BlockPos getLastBlockPos();

	@Accessor("lastBlockPos")
	void setLastBlockPos(BlockPos pos);

	@Accessor("attacking")
	void setAttacking(LivingEntity attacking);

	@Accessor("POTION_SWIRLS")
	static TrackedData<List<ParticleEffect>> getPotionSwirls() {
		throw new AssertionError();
	}
}
