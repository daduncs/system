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

import dlindustries.uk.daduncs.event.events.CameraUpdateListener;
import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.imixin.IKeyBinding;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;


public final class Freecam extends Module implements TickListener, CameraUpdateListener {
	private final number speed = new number(EncryptedString.of("Speed"), 1, 10, 1, 1);
	public Vec3d oldPos;
	public Vec3d pos;

	public Freecam() {
		super(EncryptedString.of("Freecam"),
				EncryptedString.of("Lets you move freely around the world without actually moving"),
				-1,
				Category.RENDER);
		addSettings(speed);

		oldPos = Vec3d.ZERO;
		pos = Vec3d.ZERO;
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		eventManager.add(CameraUpdateListener.class, this);
		if (mc.world != null) {
			this.oldPos = this.pos = mc.player.getEyePos();
		}

		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		eventManager.remove(CameraUpdateListener.class, this);

		if (mc.options != null) {
			((IKeyBinding) mc.options.useKey).resetPressed();
			((IKeyBinding) mc.options.attackKey).resetPressed();
			((IKeyBinding) mc.options.forwardKey).resetPressed();
			((IKeyBinding) mc.options.backKey).resetPressed();
			((IKeyBinding) mc.options.leftKey).resetPressed();
			((IKeyBinding) mc.options.rightKey).resetPressed();
			((IKeyBinding) mc.options.jumpKey).resetPressed();
			((IKeyBinding) mc.options.sneakKey).resetPressed();
			((IKeyBinding) mc.options.sprintKey).resetPressed();
		}

		if (mc.world != null) {
			mc.worldRenderer.reload();
		}
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.currentScreen != null || mc.player == null || mc.options == null)
			return;

		drainAndUnpress(mc.options.useKey);
		drainAndUnpress(mc.options.attackKey);
		drainAndUnpress(mc.options.dropKey);
		drainAndUnpress(mc.options.swapHandsKey);
		if (mc.options.hotbarKeys != null) {
			for (KeyBinding hotbarKey : mc.options.hotbarKeys) {
				drainAndUnpress(hotbarKey);
			}
		}
		mc.options.forwardKey.setPressed(false);
		mc.options.backKey.setPressed(false);
		mc.options.leftKey.setPressed(false);
		mc.options.rightKey.setPressed(false);
		mc.options.jumpKey.setPressed(false);
		((IKeyBinding) mc.options.sneakKey).setRawPressed(false);
		((IKeyBinding) mc.options.sprintKey).setRawPressed(false);

		float f = (float) Math.PI / 180;
		float f2 = (float) Math.PI;
		Vec3d vec3d = new Vec3d(-MathHelper.sin(-mc.player.getYaw() * f - f2), 0.0, -MathHelper.cos(-mc.player.getYaw() * f - f2));
		Vec3d vec3d2 = new Vec3d(0.0, 1.0, 0.0);
		Vec3d vec3d3 = vec3d2.crossProduct(vec3d);
		Vec3d vec3d4 = vec3d.crossProduct(vec3d2);
		Vec3d vec3d5 = Vec3d.ZERO;

		if (((IKeyBinding) mc.options.forwardKey).isActuallyPressed()) {
			vec3d5 = vec3d5.add(vec3d);
		}

		if (((IKeyBinding) mc.options.backKey).isActuallyPressed()) {
			vec3d5 = vec3d5.subtract(vec3d);
		}

		if (((IKeyBinding) mc.options.leftKey).isActuallyPressed()) {
			vec3d5 = vec3d5.add(vec3d3);
		}

		if (((IKeyBinding) mc.options.rightKey).isActuallyPressed()) {
			vec3d5 = vec3d5.add(vec3d4);
		}

		if (((IKeyBinding) mc.options.jumpKey).isActuallyPressed()) {
			vec3d5 = vec3d5.add(0.0, speed.getValue(), 0.0);
		}

		if (((IKeyBinding) mc.options.sneakKey).isActuallyPressed()) {
			vec3d5 = vec3d5.add(0.0, -speed.getValue(), 0.0);
		}

		vec3d5 = vec3d5.normalize().multiply(speed.getValue() * (((IKeyBinding) mc.options.sprintKey).isActuallyPressed() ? 2 : 1));

		oldPos = pos;
		pos = pos.add(vec3d5);
	}

	private static void drainAndUnpress(KeyBinding key) {
		if (key == null) return;
		while (key.wasPressed()) {}
		key.setPressed(false);
	}


	@Override
	public void onCameraUpdate(CameraUpdateEvent event) {
		float tickDelta = mc.getRenderTickCounter().getTickProgress(true);

		if (mc.currentScreen != null)
			return;

		event.setX(MathHelper.lerp(tickDelta, oldPos.x, pos.x));
		event.setY(MathHelper.lerp(tickDelta, oldPos.y, pos.y));
		event.setZ(MathHelper.lerp(tickDelta, oldPos.z, pos.z));

	}
}
