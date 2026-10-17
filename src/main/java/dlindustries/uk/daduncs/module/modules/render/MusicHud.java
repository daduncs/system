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
import dlindustries.uk.daduncs.event.events.HudListener;
import dlindustries.uk.daduncs.gui.Anim;
import dlindustries.uk.daduncs.gui.HudEditor;
import dlindustries.uk.daduncs.gui.Theme;
import dlindustries.uk.daduncs.managers.LyricsManager;
import dlindustries.uk.daduncs.managers.MediaManager;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.render.Render2D;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.Sounds;
import dlindustries.uk.daduncs.utils.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class MusicHud extends Module implements HudListener {

	private static final int MIN_W = 168;
	private static final int MAX_W = 290;
	private static final int HEIGHT = 66;
	private static final int ART = 56;
	private static final int BAR = 7;
	private static final int TORN = 1000;
	private static final int GAP = 4;
	private static final int WORDS = 26;

	private final bool outline = new bool(EncryptedString.of("Outline"), false);
	private final number size = new number(EncryptedString.of("Size"), 50, 200, 100, 5);
	private final bool lyrics = new bool(EncryptedString.of("Lyrics"), true);
	private final bool lyricsGradient = new bool(EncryptedString.of("Lyrics Gradient"), true).setVisibility(lyrics::getValue);
	private final bool barGradient = new bool(EncryptedString.of("Bar Gradient"), true);
	private final bool artGradient = new bool(EncryptedString.of("Art Gradient"), false);
	private final bool fading = new bool(EncryptedString.of("Fade"), false);
	private final number fadeSize = new number(EncryptedString.of("Fade Size"), 4, 40, 18, 1).setVisibility(fading::getValue);
	private final bool fadeGradient = new bool(EncryptedString.of("Fade Gradient"), true).setVisibility(fading::getValue);

	private final Anim show = new Anim(0f, 6f);
	private final Anim bar = new Anim(0f, 8f);
	private final Anim grow = new Anim(MIN_W, 7f);
	private final Anim slide = new Anim(0f, 9f);
	private final Anim opened = new Anim(0f, 7f);
	private final Anim scroll = new Anim(1f, 6f);

	private boolean holding;
	private boolean detached;
	private long grabbed;
	private int grabX;
	private int singing;
	private List<String> leaving = new ArrayList<>();

	public MusicHud() {
		super(EncryptedString.of("Music Hud"),
				EncryptedString.of("Shows what you are listening to"),
				-1,
				Category.RENDER);
		addSettings(outline, size, lyrics, lyricsGradient, barGradient, artGradient, fading, fadeSize, fadeGradient);
		HudEditor.register(this);
	}

	@Override
	public void onEnable() {
		System.INSTANCE.getMediaManager().start();
		eventManager.add(HudListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(HudListener.class, this);
		show.snap(0f);
		holding = false;
		super.onDisable();
	}

	@Override
	public boolean claim(int mouseX, int mouseY) {
		int x = HudEditor.x(this, 8);
		int y = HudEditor.y(this, 300);
		int art = x + Math.round(slide.get());

		if (mouseX < art + 5 || mouseX > art + 5 + ART || mouseY < y + 5 || mouseY > y + 5 + ART)
			return false;

		holding = true;
		grabbed = java.lang.System.currentTimeMillis();
		grabX = mouseX;
		return true;
	}

	@Override
	public void onRenderHud(HudEvent event) {
		MediaManager media = System.INSTANCE.getMediaManager();
		MediaManager.Track track = media.getTrack();

		DrawContext context = event.context;
		float delta = mc.getRenderTickCounter().getDynamicDeltaTicks() / 20f;

		boolean wanted = !track.empty() || HudEditor.editing();

		show.to(wanted ? 1f : 0f);
		show.update(delta);

		if (show.get() <= 0.06f)
			return;

		grip();

		float fade = show.get();
		int x = HudEditor.x(this, 8);
		int y = HudEditor.y(this, 300);

		HudEditor.push(context, size.getValueFloat() / 100f, x, y);

		String title = track.empty() ? "nothing playing" : track.title();
		String artist = track.empty() ? "" : track.artist();

		int elapsed = media.elapsed();
		int length = track.duration();

		slide.to(detached ? -(ART + 10 + GAP) : 0f);
		slide.update(delta);

		int torn = Math.round(slide.get());
		int column = detached ? 9 : 5 + ART + 8;
		int want = column + TextRenderer.smallBoldWidth(title) + 7;
		int least = MIN_W - (5 + ART + 8 - 9);

		grow.to(Math.max(detached ? least : MIN_W, Math.min(MAX_W, want)));
		grow.update(delta);

		int wide = Math.round(grow.get());

		LyricsManager singer = System.INSTANCE.getLyricsManager();
		singer.follow(track);

		LyricsManager.Line line = lyrics.getValue() ? singer.showing(elapsed) : null;

		opened.to(line != null ? 1f : 0f);
		opened.update(delta);

		int room = Math.round(WORDS * opened.get());
		int height = HEIGHT + (detached ? 0 : room);

		glass(context, x, y, wide, height, fade);

		if (detached)
			glass(context, x + torn, y, ART + 10, HEIGHT, fade);

		Identifier cover = media.getCover();
		if (cover != null)
			Render2D.drawRoundImage(context, x + torn + 5, y + 5, ART, ART, 9f, cover, Theme.fade(0xFFFFFFFF, fade));
		else
			Render2D.drawRoundRect(context, x + torn + 5, y + 5, ART, ART, 9f, Theme.fade(Theme.off, fade));

		int left = x + column;
		int right = x + wide - 7;
		int span = right - left;

		TextRenderer.drawTiny(EncryptedString.of("now playing"), context, left, y + 13, Theme.fade(Theme.muted, fade));
		TextRenderer.drawSmallBold(fit(title, span, TextRenderer::smallBoldWidth), context, left, y + 22,
				Theme.fade(Theme.text, fade));
		TextRenderer.drawSmall(fit(artist, span, TextRenderer::smallWidth), context, left, y + 33,
				Theme.fade(Theme.muted, fade));

		bar.to(length > 0 ? (float) elapsed / length : 0f);
		bar.update(delta);

		float done = span * Math.min(1f, bar.get());
		float round = BAR / 2f;

		Render2D.drawRoundRect(context, left, y + 44, span, BAR, round, Theme.fade(Theme.off, fade));

		if (done > 0.5f) {
			if (barGradient.getValue())
				Render2D.drawGradientRect(context, left, y + 44, Math.max(done, BAR), BAR, round, Theme.fade(Theme.flow(from(), to(), 0f), fade),
						Theme.fade(Theme.flow(from(), to(), Math.min(1f, bar.get())), fade));
			else Render2D.drawRoundRect(context, left, y + 44, Math.max(done, BAR), BAR, round, Theme.fade(from(), fade));
		}

		String from = clock(elapsed);
		String rest = length > 0 ? "-" + clock(length - elapsed) : "";
		int foot = y + 5 + ART + 4 - TextRenderer.tinyHeight();

		TextRenderer.drawTiny(from, context, left, foot, Theme.fade(Theme.muted, fade));
		TextRenderer.drawTiny(rest, context, right - TextRenderer.tinyWidth(rest), foot, Theme.fade(Theme.muted, fade));

		int edge = Math.min(x, x + torn);
		int wordsX = (detached ? edge : x) + 9;
		int wordsY = detached ? y + HEIGHT + GAP : y + HEIGHT;
		int reach = x + wide - 9 - wordsX;

		if (detached && room > 2)
			glass(context, edge, y + HEIGHT + GAP, x + wide - edge, room, fade);

		if (line != null && room > 8)
			sing(context, singer, line, wordsX, wordsY, reach, room, elapsed, delta, fade * opened.get(), !detached);

		HudEditor.bounds(this, edge, y, x + wide - edge, height + (detached ? room + GAP : 0));

		HudEditor.pop(context);
	}

	private void glass(DrawContext context, int x, int y, int width, int height, float fade) {
		if (fading.getValue())
			Render2D.drawFade(context, x, y, width, height, 12, fadeSize.getValueFloat(),
					Theme.fade(fadeGradient.getValue() ? Theme.flow(from(), to(), 0f) : from(), fade),
					Theme.fade(fadeGradient.getValue() ? Theme.flow(from(), to(), 1f) : from(), fade));

		if (ClickGUI.hudGlass.getValue())
			Render2D.drawBubble(context, x, y, width, height, 12, Theme.SHEEN, 7f, 0.35f, 0x66101010, fade);
		else
			Render2D.drawGlassRoundRect(context, x, y, width, height, 12, Theme.SHEEN, Theme.BLUR, Theme.surface, fade);

		if (outline.getValue())
			Render2D.drawRoundOutline(context, x, y, width, height, 12, 1, Theme.fade(Theme.accent, fade));
	}

	private void grip() {
		if (!HudEditor.editing() || !holding) {
			holding = false;
			return;
		}

		if (!HudEditor.held()) {
			holding = false;
			System.INSTANCE.getProfileManager().saveProfile();
			return;
		}

		if (java.lang.System.currentTimeMillis() - grabbed < TORN)
			return;

		int moved = HudEditor.mouseX() - grabX;

		if (!detached && moved < -GAP) {
			detached = true;
			grabX = HudEditor.mouseX();
			Sounds.play(Sounds.COLLAPSE);
		} else if (detached && moved > GAP) {
			detached = false;
			grabX = HudEditor.mouseX();
			Sounds.play(Sounds.COLLAPSE);
		}
	}

	private void sing(DrawContext context, LyricsManager singer, LyricsManager.Line line, int x, int y, int room, int tall,
					  int elapsed, float delta, float fade, boolean divider) {
		List<String> rows = wrap(line.text(), room);

		if (line.time() != singing) {
			leaving = singing == 0 ? new ArrayList<>() : rows;
			singing = line.time();
			scroll.snap(0f);
		}

		scroll.to(1f);
		scroll.update(delta);

		if (divider)
			Render2D.drawRoundRect(context, x, y + 2, room, 1, 0.5f, Theme.fade(Theme.divider, fade));

		context.enableScissor(x - 9, y + 4, x + room + 9, y + tall);

		float gone = scroll.get();
		int above = y + 8 - Math.round(gone * 11f);

		for (String row : leaving) {
			int cursor = x;

			for (int letter = 0; letter < row.length(); letter++) {
				String glyph = String.valueOf(row.charAt(letter));
				TextRenderer.drawTiny(glyph, context, cursor, above, Theme.fade(read((float) letter / row.length()), fade * Math.max(0f, 1f - gone * 2.5f)));
				cursor += TextRenderer.tinyWidth(glyph);
			}

			above += 11;
		}

		LyricsManager.Line coming = singer.next(elapsed);
		int sung = rows.size();

		if (sung < 2 && coming != null)
			rows.add(fit(coming.text(), room, TextRenderer::tinyWidth));

		int letters = 0;
		for (String row : rows)
			letters += row.length();

		float mouth = singer.read(elapsed) * letters;
		float rise = (1f - gone) * 11f;

		int seen = 0;
		int top = y + 8;

		for (int i = 0; i < rows.size(); i++) {
			String row = rows.get(i);
			boolean ahead = i >= sung;
			int cursor = x;

			for (int letter = 0; letter < row.length(); letter++) {
				float lit = ahead ? 0f : ease(Math.max(0f, Math.min(1f, mouth - (seen + letter))));
				int colour = Theme.lerp(Theme.muted, read((seen + letter) / (float) letters), lit);
				String glyph = String.valueOf(row.charAt(letter));

				TextRenderer.drawTiny(glyph, context, cursor, Math.round(top + rise + (1f - lit) * 2f),
						Theme.fade(colour, (ahead ? fade * 0.5f : fade) * gone));
				cursor += TextRenderer.tinyWidth(glyph);
			}

			seen += row.length();
			top += 11;
		}

		context.disableScissor();
	}

	private int read(float span) {
		return lyricsGradient.getValue() ? Theme.flow(from(), to(), span) : from();
	}

	private int from() {
		MediaManager media = System.INSTANCE.getMediaManager();
		return artGradient.getValue() && media.getHot() != 0 ? media.getHot() : Theme.accent;
	}

	private int to() {
		MediaManager media = System.INSTANCE.getMediaManager();
		return artGradient.getValue() && media.getHot() != 0 ? media.getCold() : Theme.flowEnd();
	}

	private static float ease(float step) {
		return step * step * (3f - 2f * step);
	}

	private static List<String> wrap(String text, int room) {
		List<String> rows = new ArrayList<>();
		String rest = text == null ? "" : text.trim();

		while (!rest.isEmpty() && rows.size() < 2) {
			if (TextRenderer.tinyWidth(rest) <= room) {
				rows.add(rest);
				break;
			}

			int cut = rest.length();
			while (cut > 1 && TextRenderer.tinyWidth(rest.substring(0, cut)) > room)
				cut--;

			int space = rest.lastIndexOf(' ', cut);
			if (space > 1)
				cut = space;

			rows.add(rest.substring(0, cut).trim());
			rest = rest.substring(cut).trim();
		}

		return rows;
	}

	private static String fit(String text, int room, java.util.function.ToIntFunction<CharSequence> width) {
		if (text == null || text.isEmpty())
			return "";

		if (width.applyAsInt(text) <= room)
			return text;

		String cut = text;
		while (cut.length() > 1 && width.applyAsInt(cut + "...") > room)
			cut = cut.substring(0, cut.length() - 1);

		return cut.trim() + "...";
	}

	private static String clock(int ms) {
		int seconds = Math.max(0, ms) / 1000;
		return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
	}
}