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

import dlindustries.uk.daduncs.event.events.GameRenderListener;
import dlindustries.uk.daduncs.event.events.HudListener;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.colour;
import dlindustries.uk.daduncs.module.setting.mode;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.ProjectionUtils;
import dlindustries.uk.daduncs.utils.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.awt.*;

public final class PlayerESP extends Module implements GameRenderListener, HudListener {
	public enum Mode {
		OneD, TwoD, ThreeD
	}

	public final mode<Mode> mode = new mode<>(EncryptedString.of("Mode"), Mode.ThreeD, Mode.class);
	private final colour color = new colour(EncryptedString.of("Color"), 0xFF9B6DFF);
	private final number alpha = new number(EncryptedString.of("Alpha"), 0, 255, 100, 1);
	private final number width = new number(EncryptedString.of("Line width"), 1, 10, 1, 1);
	private final bool tracers = new bool(EncryptedString.of("Tracers"), false)
			.setDescription(EncryptedString.of("Draws a line from your player to the other"));
	private final bool hideFriends = new bool(EncryptedString.of("Hide Friends"), true)
			.setDescription(EncryptedString.of("Doesn't draw friends at all"));
	private final bool friendColor = new bool(EncryptedString.of("Highlight Friends"), true)
			.setDescription(EncryptedString.of("Draws friends green if they are shown"));
	private final bool threeDOutline = new bool(EncryptedString.of("3D box outline"), false);
	private final bool threeDFill = new bool(EncryptedString.of("3D Fill"), true);
	private final bool twoDOutline = new bool(EncryptedString.of("2D Outline"), false);
	private final bool twoDFill = new bool(EncryptedString.of("2D Fill"), false);

	public PlayerESP() {
		super(EncryptedString.of("Player ESP"),
				EncryptedString.of("Renders players through walls"),
				-1,
				Category.RENDER);
		addSettings(color, alpha, mode, hideFriends, friendColor, threeDOutline, threeDFill, twoDOutline, twoDFill, width, tracers);
	}

	@Override
	public void onEnable() {
		eventManager.add(GameRenderListener.class, this);
		eventManager.add(HudListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(GameRenderListener.class, this);
		eventManager.remove(HudListener.class, this);
		super.onDisable();
	}

	@Override
	public void onGameRender(GameRenderEvent event) {
		if (mc.world == null || mc.player == null) {
			return;
		}

		Camera camera = mc.gameRenderer.getCamera();
		if (camera == null) {
			return;
		}

		event.matrices.push();
		Vec3d cameraPos = camera.getCameraPos();
		event.matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
		event.matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
		event.matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

		for (PlayerEntity player : mc.world.getPlayers()) {
			if (!shouldRender(player)) {
				continue;
			}

			Box box = getRenderBox(player, event.delta).expand(0.02);
			if (mode.isMode(Mode.OneD)) {
				Vec3d center = box.getCenter();
				RenderUtils.renderLine(event.matrices, getColor(player, 255), new Vec3d(center.x, box.minY, center.z), new Vec3d(center.x, box.maxY, center.z), width.getValueInt());
			}

			if (mode.isMode(Mode.ThreeD) && threeDFill.getValue()) {
				RenderUtils.renderFilledBox(
						event.matrices,
						(float) box.minX,
						(float) box.minY,
						(float) box.minZ,
						(float) box.maxX,
						(float) box.maxY,
						(float) box.maxZ,
						getColor(player, alpha.getValueInt()).brighter()
				);
			}

			if (mode.isMode(Mode.ThreeD)) {
				if (threeDOutline.getValue())
					RenderUtils.renderBoxOutline(event.matrices, box, getColor(player, 255), width.getValueInt());
			}

			if (tracers.getValue() && mc.crosshairTarget != null) {
				RenderUtils.renderLine(event.matrices, getColor(player, 255), mc.crosshairTarget.getPos(), player.getLerpedPos(RenderUtils.tickProgress()));
			}
		}

		event.matrices.pop();
	}

	@Override
	public void onRenderHud(HudEvent event) {
		if (!mode.isMode(Mode.TwoD) || mc.world == null || mc.player == null) {
			return;
		}

		for (PlayerEntity player : mc.world.getPlayers()) {
			if (!shouldRender(player)) {
				continue;
			}

			ScreenBounds bounds = projectBounds(getRenderBox(player, event.delta));
			if (bounds == null || !bounds.isValid()) {
				continue;
			}

			Color outlineColor = getColor(player, 255);
			int thickness = Math.max(1, width.getValueInt());
			if (twoDFill.getValue())
				event.context.fill(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY, getColor(player, alpha.getValueInt()).getRGB());
			if (twoDOutline.getValue()) {
				Color borderColor = new Color(0, 0, 0, Math.min(255, outlineColor.getAlpha()));
				drawOutline(event.context, bounds, thickness + 1, borderColor);
			}
			drawOutline(event.context, bounds, thickness, outlineColor);
		}
	}

	private boolean shouldRender(Entity entity) {
		if (!(entity instanceof PlayerEntity player) || player == mc.player || !player.isAlive())
			return false;

		if (!isFriend(player))
			return true;

		return friendColor.getValue() || !hideFriends.getValue();
	}

	private boolean isFriend(PlayerEntity player) {
		return dlindustries.uk.daduncs.System.INSTANCE.getFriendManager().isFriend(player);
	}

	private Color getColor(PlayerEntity player, int alpha) {
		if (friendColor.getValue() && isFriend(player))
			return new Color(0, 255, 60, alpha);

		return getColor(alpha);
	}

	private Box getRenderBox(PlayerEntity player, float tickDelta) {
		double x = MathHelper.lerp(tickDelta, player.lastX, player.getX());
		double y = MathHelper.lerp(tickDelta, player.lastY, player.getY());
		double z = MathHelper.lerp(tickDelta, player.lastZ, player.getZ());
		return player.getBoundingBox().offset(x - player.getX(), y - player.getY(), z - player.getZ());
	}

	private ScreenBounds projectBounds(Box box) {
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		int visibleCorners = 0;

		for (double x : new double[]{box.minX, box.maxX}) {
			for (double y : new double[]{box.minY, box.maxY}) {
				for (double z : new double[]{box.minZ, box.maxZ}) {
					ProjectionUtils.ProjectedPoint projected = ProjectionUtils.project(new Vec3d(x, y, z));
					if (projected == null) {
						continue;
					}

					visibleCorners++;
					minX = Math.min(minX, projected.x());
					minY = Math.min(minY, projected.y());
					maxX = Math.max(maxX, projected.x());
					maxY = Math.max(maxY, projected.y());
				}
			}
		}

		if (visibleCorners == 0) {
			return null;
		}

		return new ScreenBounds((int) Math.floor(minX), (int) Math.floor(minY), (int) Math.ceil(maxX), (int) Math.ceil(maxY));
	}

	private void drawOutline(DrawContext context, ScreenBounds bounds, int thickness, Color color) {
		int packedColor = color.getRGB();
		context.fill(bounds.minX, bounds.minY, bounds.maxX, bounds.minY + thickness, packedColor);
		context.fill(bounds.minX, bounds.maxY - thickness, bounds.maxX, bounds.maxY, packedColor);
		context.fill(bounds.minX, bounds.minY + thickness, bounds.minX + thickness, bounds.maxY - thickness, packedColor);
		context.fill(bounds.maxX - thickness, bounds.minY + thickness, bounds.maxX, bounds.maxY - thickness, packedColor);
	}

	private Color getColor(int alpha) {
		return new Color((color.getValue() & 0xFFFFFF) | (alpha << 24), true);
	}

	private record ScreenBounds(int minX, int minY, int maxX, int maxY) {
		private boolean isValid() {
			return maxX > minX && maxY > minY;
		}
	}
}
