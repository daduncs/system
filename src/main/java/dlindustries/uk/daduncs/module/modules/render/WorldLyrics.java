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

import dlindustries.uk.daduncs.System;
import dlindustries.uk.daduncs.event.events.GameRenderListener;
import dlindustries.uk.daduncs.font.Fonts;
import dlindustries.uk.daduncs.font.WorldFont;
import dlindustries.uk.daduncs.gui.Theme;
import dlindustries.uk.daduncs.managers.LyricsManager;
import dlindustries.uk.daduncs.managers.MediaManager;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class WorldLyrics extends Module implements GameRenderListener {

	private static final int WORDS = 3;
	private static final int ROWS = 3;
	private static final double AHEAD = 2.6;
	private static final double SIDE = 1.15;
	private static final double STEP = 0.34;
	private static final float SCALE = 0.008f;
	private static final float GLOW = 9f;
	private static final float TILT = 14f;

	private final bool artColours = new bool(EncryptedString.of("Art Colors"), false);
	private final number flow = new number(EncryptedString.of("Gradient Speed"), 0, 10, 4, 0.5);
	private final number spawn = new number(EncryptedString.of("Spawn Delay"), 100, 1200, 420, 10);
	private final number lifetime = new number(EncryptedString.of("Lifetime"), 800, 6000, 2400, 100);

	private final List<Word> words = new ArrayList<>();
	private final List<String> queue = new ArrayList<>();
	private final Random random = new Random();
	private int lastLine = Integer.MIN_VALUE;
	private long lastSpawn;
	private int seat;

	public WorldLyrics() {
		super(EncryptedString.of("World Lyrics"),
				EncryptedString.of("Lyrics of yo song showing in the world"),
				-1,
				Category.RENDER);
		addSettings(artColours, flow, spawn, lifetime);
	}

	@Override
	public void onEnable() {
		System.INSTANCE.getMediaManager().start();
		eventManager.add(GameRenderListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(GameRenderListener.class, this);
		words.clear();
		queue.clear();
		super.onDisable();
	}

	@Override
	public void onGameRender(GameRenderEvent event) {
		if (mc.player == null || mc.world == null) return;

		update();
		draw(event);
	}

	private void update() {
		MediaManager media = System.INSTANCE.getMediaManager();
		MediaManager.Track track = media.getTrack();

		if (track.empty()) {
			words.clear();
			queue.clear();
			lastLine = Integer.MIN_VALUE;
			return;
		}

		LyricsManager lyrics = System.INSTANCE.getLyricsManager();
		lyrics.follow(track);

		LyricsManager.Line line = lyrics.showing(media.elapsed());
		if (line != null && line.time() != lastLine) {
			lastLine = line.time();
			queue.clear();
			split(line.text());
		}

		long now = java.lang.System.currentTimeMillis();
		if (queue.isEmpty() || now - lastSpawn < spawn.getValue()) return;

		lastSpawn = now;
		place(queue.remove(0), now);
	}

	private void split(String text) {
		if (text.isBlank()) return;

		String[] parts = text.trim().split("\\s+");
		for (int i = 0; i < parts.length; i += WORDS)
			queue.add(String.join(" ", Arrays.copyOfRange(parts, i, Math.min(parts.length, i + WORDS))));
	}

	private void place(String text, long now) {
		int slot = Math.floorMod(seat++, ROWS * 2);
		double side = slot < ROWS ? -SIDE : SIDE;

		double yaw = Math.toRadians(mc.player.getYaw());
		Vec3d forward = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3d left = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));

		Vec3d spot = mc.player.getEyePos()
				.add(forward.multiply(AHEAD))
				.add(left.multiply(side))
				.add(0, 0.2 + (slot % ROWS) * STEP, 0);

		float tilt = random.nextFloat() < 0.38f ? (random.nextFloat() - 0.5f) * TILT : 0f;
		words.add(new Word(text, spot, tilt, now));

		if (words.size() > ROWS * 2)
			words.remove(0);
	}

	private void draw(GameRenderEvent event) {
		Camera camera = mc.gameRenderer.getCamera();
		if (camera == null || words.isEmpty()) return;

		long now = java.lang.System.currentTimeMillis();
		long life = (long) lifetime.getValue();
		words.removeIf(word -> now - word.born() > life);

		MediaManager media = System.INSTANCE.getMediaManager();
		boolean art = artColours.getValue() && media.getHot() != 0;
		int hot = art ? media.getHot() : Theme.accent;
		int cold = art ? media.getCold() : Theme.secondary;
		float phase = (now % 60000L) / 1000f * flow.getValueFloat() * 0.12f;

		boolean modern = ClickGUI.modern.getValue();
		float scale = modern ? SCALE : SCALE * 4f;
		Vec3d eye = camera.getCameraPos();

		for (Word word : words) {
			long age = now - word.born();
			float born = Math.min(1f, age / 300f);
			float gone = Math.max(0f, (age / (float) life - 0.6f) / 0.4f);
			float width = modern ? WorldFont.width(Fonts.BOLD, word.text()) : WorldFont.vanillaWidth(word.text());

			event.matrices.push();
			event.matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
			event.matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180f));
			event.matrices.translate(word.spot().x - eye.x, word.spot().y - eye.y, word.spot().z - eye.z);
			event.matrices.multiply(camera.getRotation());
			event.matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(word.tilt()));
			event.matrices.scale(scale, modern ? scale : -scale, scale);
			event.matrices.translate(-width / 2f, 0f, 0f);

			if (modern) WorldFont.draw(Fonts.BOLD, event.matrices, word.text(), hot, cold, phase, gone, born, GLOW);
			else WorldFont.vanilla(event.matrices, word.text(), Theme.fade(hot, 1f - gone));

			event.matrices.pop();
		}
	}

	private record Word(String text, Vec3d spot, float tilt, long born) {
	}
}
