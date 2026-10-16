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

package dlindustries.uk.daduncs.gui;

import dlindustries.uk.daduncs.System;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.misc.NameHider;
import dlindustries.uk.daduncs.render.Draw;
import dlindustries.uk.daduncs.render.Render2D;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.Sounds;
import dlindustries.uk.daduncs.utils.TextRenderer;
import dlindustries.uk.daduncs.gui.announcements.AnnouncementCard;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static dlindustries.uk.daduncs.System.mc;

public final class ClickGui extends Screen {

	private final AnnouncementCard announcementCard = new AnnouncementCard();

	private static final float SCALE = 0.78f;

	private static final Anim zoom = new Anim(1f, 12f);

	private static float scale() {
		return SCALE * zoom.get();
	}

	private static final int RAIL_W = 46;
	private static final int RAIL_WIDE = 132;
	private static final int RAIL_H = 403;
	private static final int PANEL_W = 368;
	private static final int PANEL_H = 404;
	private static final int GAP = 6;
	private static final int CARD_W = 344;
	private static final int DUO_W = 600;
	private static final int CARD_GAP = 4;
	private static final int PAD = 12;

	private static final int GLYPH = 26;
	private static final int SLOT = 39;
	private static final float PILL_ROUND = 10f;


	private static final Identifier LOGO = Identifier.of("system", "textures/gui/client_icon.png");
	private static final Identifier SEARCH = Identifier.of("system", "textures/gui/search.png");

	private static int form = 0;

	private final Map<Category, Sprite> icons = new EnumMap<>(Category.class);
	private final Map<Category, List<Card>> cards = new EnumMap<>(Category.class);
	private final Map<Category, Anim> railHover = new EnumMap<>(Category.class);

	private final Anim fade = new Anim(0f, 9f);
	private final Anim searchOpen = new Anim(0f, 7f);
	private final Anim marker = new Anim(0f, 8f);
	private final Anim tip = new Anim(0f, 9f);
	private final Anim dwellName = new Anim(0f, 10f);
	private final Anim rail = new Anim(form == 0 ? 0 : form == 2 ? RAIL_WIDE : RAIL_W, 9f);
	private final Anim inset = new Anim(form == 0 ? RAIL_WIDE : 0, 9f);
	private final Anim pill = new Anim(RAIL_W - 8, 9f);
	private final Anim swell = new Anim(0f, 11f, 6f);
	private final Anim hint = new Anim(1f, 12f);
	private final Anim caret = new Anim(0f, 18f);
	private final Anim ghostFade = new Anim(0f, 11f);
	private final Anim helper = new Anim(0f, 6f);
	private final List<Anim> letters = new ArrayList<>();

	private CharSequence tipText;


	private boolean editing;
	private boolean holding;
	private boolean pressing;
	private boolean layered;
	private float glide = -1f;
	private float wobble;
	private float wobbleSpeed;
	private float lastMarker;
	private long heldSince;
	private int grabbedAt;

	private Category dwell;
	private long dwellStart;

	private Category selected = Category.CLIENT;
	private String query = "";
	private String typedLast = "";
	private long scrolled;
	private String ghost = "";
	private int ghostAt;
	private boolean searching;
	private float scroll;
	private float progress;
	private float closing;
	private float haze;
	private boolean shutting;

	public ClickGui() {
		super(Text.empty());

		for (Category category : Category.values()) {
			icons.put(category, new Sprite(iconName(category)));
			railHover.put(category, new Anim(0f, 16f));

			List<Card> list = new ArrayList<>();
			for (Module module : System.INSTANCE.getModuleManager().getModulesInCategory(category))
				list.add(new Card(module));

			cards.put(category, list);
		}

		marker.snap(selected.ordinal());
		lastMarker = selected.ordinal();
	}

	private static String iconName(Category category) {
		return switch (category) {
			case CLIENT -> "client";
			case CRYSTAL -> "crystal";
			case MACE -> "mace";
			case MISC -> "optimizer";
			case CART -> "cart";
			case RENDER -> "render";
			case SPEAR -> "spear";
			case SWORD -> "sword";
		};
	}

	private static String label(Category category) {
		String raw = category.name();
		return raw.charAt(0) + raw.substring(1).toLowerCase();
	}

	private Identifier icon(Category category) {
		return icons.get(category).get();
	}

	private static Identifier mark() {
		return LOGO;
	}

	private boolean wide;

	@Override
	public void onDisplayed() {
		wide = ClickGUI.ui.isMode(ClickGUI.Ui.SystemDuo);
		zoom.snap(ClickGUI.guiSize.getValueFloat() / 100f);
	}

	private boolean duo() {
		return wide;
	}

	private int listW() {
		return duo() ? DUO_W : PANEL_W;
	}

	private int cardW() {
		return duo() ? (DUO_W - PAD * 2 - CARD_GAP) / 2 : CARD_W;
	}

	private int[][] spots(List<Card> list, int startY) {
		int[][] spots = new int[list.size()][2];
		int[] columns = {startY, startY};

		for (int i = 0; i < list.size(); i++) {
			int column = duo() ? i % 2 : 0;
			spots[i][0] = column * (cardW() + CARD_GAP);
			spots[i][1] = columns[column];
			columns[column] += list.get(i).height() + CARD_GAP;
		}

		return spots;
	}

	private int stacked(List<Card> list) {
		int[] columns = {0, 0};
		for (int i = 0; i < list.size(); i++)
			columns[duo() ? i % 2 : 0] += list.get(i).height() + CARD_GAP;

		return Math.max(columns[0], columns[1]);
	}

	private int anchor() {
		return (width - (RAIL_W + GAP + listW())) / 2 + RAIL_W + GAP;
	}

	private int railWidth() {
		return Math.round(rail.get());
	}

	private int insetWidth() {
		return Math.round(inset.get());
	}

	private int panelWidth() {
		return listW() + insetWidth();
	}

	private int panelX() {
		return anchor() - insetWidth();
	}

	private int listX() {
		return panelX() + insetWidth();
	}

	private int railX() {
		return anchor() - GAP - railWidth();
	}

	private int top() {
		return (height - PANEL_H) / 2;
	}

	public static float panelTop(int screenHeight) {
		return screenHeight / 2f - PANEL_H / 2f * scale();
	}

	private List<Card> visible() {
		List<Card> list = new ArrayList<>();

		if (query.isEmpty()) {
			list.addAll(cards.get(selected));
			return list;
		}

		String needle = query.toLowerCase();
		for (List<Card> group : cards.values())
			for (Card card : group)
				if (card.module().getName().toString().toLowerCase().contains(needle))
					list.add(card);

		return list;
	}



	@Override
	protected void init() {
		progress = 0f;
		shutting = false;
		closing = 0f;
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void renderInGameBackground(DrawContext context) {
	}

	@Override
	protected void applyBlur(DrawContext context) {
	}

	@Override
	protected void renderDarkening(DrawContext context) {
	}

	private static float easeOutBack(float t) {
		float c = 1.70158f;
		float p = Math.max(0f, Math.min(1f, t)) - 1f;
		return 1f + (c + 1f) * p * p * p + c * p * p;
	}

	private static float frame() {
		return Frame.delta();
	}

	private static float easeOut(float t) {
		float p = 1f - Math.max(0f, Math.min(1f, t));
		return 1f - p * p * p;
	}

	private float mapX(double value) {
		return (float) ((value - width / 2f) / scale() + width / 2f);
	}

	private float mapY(double value) {
		return (float) ((value - height / 2f) / scale() + height / 2f);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		zoom.to(ClickGUI.guiSize.getValueFloat() / 100f);
		zoom.update(delta);

		Matrix3x2fStack matrices = context.getMatrices();

		matrices.pushMatrix();
		matrices.translate(width / 2f, height / 2f);
		matrices.scale(scale(), scale());
		matrices.translate(-width / 2f, -height / 2f);

		draw(context, (int) mapX(mouseX), (int) mapY(mouseY), delta);

		matrices.popMatrix();
	}

	private void draw(DrawContext context, int mouseX, int mouseY, float delta) {
		if (shutting) {
			closing = Math.min(1f, closing + frame() * 4.2f);

			if (closing >= 1f) {
				shutting = false;
				closing = 0f;
				finish();
				return;
			}
		} else {
			progress = Math.min(1f, progress + frame() * 1.5f);
		}

		fade.to(1f);
		fade.update(delta);
		searchOpen.to(searching ? 1f : 0f);
		searchOpen.update(delta);
		layered = false;
		marker.to(glide >= 0f ? glide : selected.ordinal());
		marker.update(delta);
		swell.to(pressing || Math.abs(marker.target() - marker.get()) > 0.12f ? 1f : 0f);
		swell.update(delta);

		float step = frame();
		float moved = step > 0f ? (marker.get() - lastMarker) / step : 0f;
		float pull = Math.max(-1f, Math.min(1f, moved / 14f));
		lastMarker = marker.get();

		for (float left = step; left > 0f; left -= 0.008f) {
			float dt = Math.min(left, 0.008f);
			wobbleSpeed += ((pull - wobble) * 150f - wobbleSpeed * 10f) * dt;
			wobble += wobbleSpeed * dt;
		}

		if (holding && !editing && java.lang.System.currentTimeMillis() - heldSince > 1500) {
			editing = true;
			Sounds.play(Sounds.COLLAPSE);
		}

		rail.to(form == 2 ? RAIL_WIDE : RAIL_W);
		rail.update(delta);
		inset.to(form == 0 ? RAIL_WIDE : 0);
		inset.update(delta);

		float merge = Math.min(1f, inset.get() / RAIL_WIDE);
		float words = Math.max(spread(railWidth()), merge);

		pill.to(RAIL_W - 8 + words * (TextRenderer.labelWidth(label(selected)) + 10));
		pill.update(delta);

		int panel = panelX();
		int y = top();
		float shut = 1f - closing;
		float alpha = fade.get() * shut;
		float reveal = shutting ? 1f - easeOut(closing * 1.6f) : easeOut((progress - 0.15f) / 0.55f);
		haze = shutting ? 1f - closing * closing : easeOut(progress);

		float eased = easeOutBack(progress);
		float railLift = (1f - eased) * (height - y);
		float panelLift = -(1f - eased) * (y + PANEL_H);

		if (shutting) {
			float exit = closing * closing;
			railLift = -exit * (y + RAIL_H);
			panelLift = exit * (height - y);
		}

		int railW = railWidth();
		int railH = RAIL_H;
		int panelW = panelWidth();
		int panelH = PANEL_H;

		int railDrawX = railX();
		int panelDrawX = panel;
		int railDrawY = Math.round(y + railLift);
		int panelDrawY = Math.round(y + panelLift);

		float loose = alpha * (1f - merge);
		float joined = alpha * merge;

		if (loose > 0.01f)
			Render2D.drawGlassRoundRect(context, railDrawX, railDrawY, railW, railH, Theme.PANEL_ROUND, Theme.SHEEN, Theme.BLUR * haze, Theme.surface, loose);

		Render2D.drawGlassRoundRect(context, panelDrawX, panelDrawY, panelW, panelH, Theme.PANEL_ROUND, Theme.SHEEN, Theme.BLUR * haze, Theme.surface, alpha);

		int column = insetWidth();
		float content = alpha * reveal;

		if (joined > 0.01f)
			renderRail(context, panelDrawX, panelDrawY, RAIL_WIDE, spread(RAIL_WIDE), mouseX, mouseY, delta, joined * reveal);

		if (loose > 0.01f)
			renderRail(context, railDrawX, railDrawY, railW, spread(railW), mouseX, mouseY, delta, loose * reveal);

		renderList(context, panelDrawX + column, panelDrawY, listW(), panelH, mouseX, mouseY, delta, reveal, alpha);
		renderSearch(context, panelDrawX, panelDrawY, panelW, delta, content);
		renderTip(context, panelDrawX, panelDrawY, panelW, delta, content);

		if (searching)
			scrolled = 0;

		helper.to(scrolled != 0 && java.lang.System.currentTimeMillis() - scrolled > 3000 ? 1f : 0f);
		helper.update(delta);

		if (helper.get() > 0.01f) {
			CharSequence hint = EncryptedString.of("ctrl + f for searchbar");

			TextRenderer.drawSmall(hint, context, panelDrawX + panelW / 2 - TextRenderer.smallWidth(hint) / 2,
					panelDrawY + PANEL_H + 20, Theme.fade(0xFFFFFFFF, content * helper.get() * 0.5f));
		}

		CharSequence credits = EncryptedString.of("credits: @daduncs , @yog1s");

		TextRenderer.drawSmall(credits, context, (int) mapX(6), (int) mapY(height - 8), Theme.fade(0xFFFFFFFF, alpha * 0.5f));

		Picker.render(context, delta, alpha);
	}

	private static float spread(int columnWidth) {
		float open = (columnWidth - RAIL_W) / (float) (RAIL_WIDE - RAIL_W);
		return Math.max(0f, Math.min(1f, (open - 0.45f) / 0.55f));
	}

	private void renderRail(DrawContext context, int x, int y, int railW, float words, int mouseX, int mouseY, float delta, float alpha) {

		Render2D.drawRoundImage(context, x + 8, y + 5, 30, 30, 0f, mark(), Theme.fade(Theme.accent, alpha));

		if (words > 0.01f)
			TextRenderer.DrawClickGuiWatermark(context, x + 44, y + 13, alpha * words);

		Render2D.drawRoundRect(context, x + 5, y + 38, railW - 11, 1, 0.5f, Theme.fade(Theme.divider, alpha));

		boolean glassy = ClickGUI.sidebarGlass.getValue();
		float grow = glassy ? swell.overshoot() : 0f;
		float lens = Math.min(1f, grow);
		float stretch = Math.abs(wobble) * lens;

		float pillW = pill.get() + 4f * grow - 3f * stretch;
		float pillH = 31f + 5f * grow + 8f * stretch;
		float pillX = x + 4 + pill.get() / 2f - pillW / 2f;
		float pillY = y + 62.5f + marker.get() * SLOT - pillH / 2f - wobble * 2f * lens;

		if (!glassy || grow < 0.99f)
			Render2D.drawBlurRoundRect(context, pillX, pillY, pillW, pillH, PILL_ROUND, 1f, Theme.BLUR * haze, Theme.slot, glassy ? alpha * (1f - grow) : alpha);

		int slot = y + 50;
		Category under = null;
		int underY = 0;

		for (Category category : Category.values()) {
			Anim anim = railHover.get(category);
			boolean over = mouseX >= x + 4 && mouseX <= x + railW - 4 && mouseY >= slot - 5 && mouseY <= slot + GLYPH + 4;

			if (over) {
				under = category;
				underY = slot;
			}

			anim.to(over || category == selected ? 1f : 0f);
			anim.update(delta);

			int colour = Theme.lerp(Theme.muted, Theme.accent, anim.get());

			Render2D.drawRoundImage(context, x + 10, slot, GLYPH, GLYPH, 0f, icon(category), Theme.fade(colour, alpha));

			if (words > 0.01f)
				TextRenderer.drawLabel(label(category), context, x + 42, slot + 12, Theme.fade(colour, alpha * words));

			slot += SLOT;
		}

		if (glassy && lens > 0.01f) {
			if (!layered) {
				layered = true;
				context.createNewRootLayer();

				try {
					context.applyBlur();
					Draw.askBehind();
				} catch (IllegalStateException ignored) {
				}
			}

			Render2D.drawGuiBubble(context, pillX, pillY, pillW, pillH, PILL_ROUND, Theme.SHEEN, 1.5f, lens,
					0x2E0A0A0A, alpha * Math.min(1f, lens * 1.6f));
		}

		if (under != dwell) {
			dwell = under;
			dwellStart = java.lang.System.currentTimeMillis();
		}

		boolean ready = words < 0.01f && dwell != null && java.lang.System.currentTimeMillis() - dwellStart > 1000;
		dwellName.to(ready ? 1f : 0f);
		dwellName.update(delta);

		if (dwellName.get() > 0.01f && dwell != null) {
			String name = dwell.name().charAt(0) + dwell.name().substring(1).toLowerCase();
			int lenght = TextRenderer.labelSmallWidth(name);
			int slide = Math.round(6f * (1f - dwellName.overshoot()));

			TextRenderer.drawLabelSmall(name, context, x - lenght - 8 + slide, underY + 17,
					Theme.fade(Theme.accent, alpha * dwellName.get()));
		}

		Render2D.drawRoundRect(context, x + 5, y + 355, railW - 11, 1, 0.5f, Theme.fade(Theme.divider, alpha));

		Identifier face = System.INSTANCE.getAccentManager().getAvatar();
		if (face != null)
			Render2D.drawRoundImage(context, x + 5, y + 360, 36, 36, 18f, face, Theme.fade(0xFFFFFFFF, alpha));
		else Render2D.drawLogo(context, x + 5, y + 360, 36, 36, LOGO, Theme.fade(Theme.accent, alpha));

		if (words > 0.01f) {
			String who = System.INSTANCE.getAccentManager().getName();

			if (who.isEmpty())
				who = NameHider.hide(mc.getSession().getUsername());

			if (!who.isEmpty())
				TextRenderer.drawMenu("@" + who, context, x + 48, y + 377, Theme.fade(Theme.text, alpha * words));
		}
	}

	private void renderList(DrawContext context, int x, int y, int w, int h, int mouseX, int mouseY, float delta, float reveal, float alpha) {
		int listTop = y + PAD;
		int listBottom = y + h - PAD;

		if (listBottom <= listTop || w <= 0)
			return;

		context.enableScissor(x + 2, y + 2, x + w - 2, y + h - 2);

		List<Card> list = visible();

		if (list.isEmpty()) {
			String empty = "no modules yet :((((";
			TextRenderer.drawSmall(empty, context, x + w / 2 - TextRenderer.smallWidth(empty) / 2, y + h / 2, Theme.fade(Theme.muted, alpha * reveal));
			context.disableScissor();
			return;
		}

		int cardY = (int) (listTop - scroll);

		if (selected == Category.CLIENT && query.isEmpty() && announcementCard.height() > 0) {
			int aHeight = announcementCard.height();
			if (cardY + aHeight >= listTop - 60 && cardY <= listBottom + 60) {
				announcementCard.render(context, x + PAD, cardY, w - PAD * 2, mouseX, mouseY, delta, alpha * reveal);
			}
			cardY += aHeight + CARD_GAP;
		}

		int[][] spots = spots(list, cardY);
		for (int index = 0; index < list.size(); index++) {
			Card card = list.get(index);
			int spotY = spots[index][1];
			float step = Math.max(0f, Math.min(1f, reveal * 1.48f - Math.min(index, 8) * 0.06f));
			int system = Math.round((1f - easeOut(step)) * 14f);

			if (step > 0.01f && spotY + card.height() >= listTop - 60 && spotY <= listBottom + 60)
				card.render(context, x + PAD + spots[index][0], spotY + system, cardW(), mouseX, mouseY, delta, alpha * step);
		}

		context.disableScissor();

	}

	private void renderTip(DrawContext context, int x, int y, int w, float delta, float alpha) {
		CharSequence found = null;
		for (Card card : visible()) {
			if (card.hoveredSettingDescription() != null) {
				found = card.hoveredSettingDescription();
				break;
			}
			if (card.overInfo()) {
				found = card.module().getDescription();
				break;
			}
		}

		if (found != null)
			tipText = found;

		tip.to(found != null ? 1f : 0f);
		tip.update(delta);

		if (tip.get() <= 0.01f || tipText == null)
			return;

		float eased = easeOutBack(tip.get());
		int label = TextRenderer.tipWidth(tipText);
		int textY = (int) (y - 22 - 20 * (1f - eased));

		int textX = Math.max(10, x + w / 2 - label / 2);
		TextRenderer.drawTip(tipText, context, textX, textY, Theme.fade(Theme.text, alpha * tip.get()));
	}

	private void renderSearch(DrawContext context, int x, int y, int w, float delta, float alpha) {
		syncLetters(delta);

		float open = searchOpen.get();
		if (open <= 0.01f)
			return;

		boolean closing = searchOpen.target() < 0.5f;
		float eased = closing ? open : easeOutBack(open);
		int barWidth = (int) (213 * (closing ? 1f : eased));
		int barX = x + w / 2 - barWidth / 2;
		int barY = closing
				? (int) (y + PANEL_H + 6 + (1f - eased) * (height / 2f + height / (2f * scale()) - y - PANEL_H))
				: (int) (y + PANEL_H + 6 + 26 * (1f - eased));

		Render2D.drawGlassRoundRect(context, barX, barY, barWidth, 32, Theme.SEARCH_ROUND, Theme.SHEEN, Theme.BLUR * haze, Theme.surface, alpha * open);

		if (open < 0.6f)
			return;

		Render2D.drawRoundImage(context, barX + 6, barY + 4, 24, 24, 0f, SEARCH, Theme.fade(Theme.muted, alpha));

		float faint = hint.get();
		if (faint > 0.01f)
			TextRenderer.drawLight("Search...", context, barX + 34 + Math.round((1f - faint) * 6f), barY + 13, Theme.fade(Theme.muted, alpha * faint));

		for (int i = 0; i < query.length(); i++) {
			float in = letters.get(i).get();
			int drop = Math.round((1f - in) * 5f);

			TextRenderer.drawLight(String.valueOf(query.charAt(i)), context, barX + 34 + TextRenderer.lightWidth(query.substring(0, i)), barY + 13 + drop,
					Theme.fade(Theme.text, alpha * in));
		}

		if (ghostFade.get() > 0.01f)
			TextRenderer.drawLight(ghost, context, barX + 34 + ghostAt, barY + 13 - Math.round((1f - ghostFade.get()) * 5f),
					Theme.fade(Theme.text, alpha * ghostFade.get()));

		if (Row.blink())
			Render2D.drawRoundRect(context, barX + 36 + caret.get(), barY + 10, 1, 13, 0.5f,
					Theme.fade(Theme.accent, alpha));
	}

	private void syncLetters(float delta) {
		if (!query.equals(typedLast)) {
			if (typedLast.startsWith(query)) {
				ghost = typedLast.substring(query.length());
				ghostAt = TextRenderer.lightWidth(query);
				ghostFade.snap(1f);
			} else if (!query.startsWith(typedLast))
				letters.clear();

			while (letters.size() > query.length())
				letters.remove(letters.size() - 1);

			while (letters.size() < query.length()) {
				Anim letter = new Anim(0f, 14f);
				letter.to(1f);
				letters.add(letter);
			}
			typedLast = query;
		}

		for (Anim letter : letters)
			letter.update(delta);

		hint.to(query.isEmpty() ? 1f : 0f);
		hint.update(delta);
		caret.to(query.isEmpty() ? 0f : TextRenderer.lightWidth(query));
		caret.update(delta);
		ghostFade.to(0f);
		ghostFade.update(delta);
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		int mouseX = (int) mapX(click.x());
		int mouseY = (int) mapY(click.y());
		int button = click.button();

		if (Picker.click(mouseX, mouseY, button))
			return true;

		for (Card card : visible())
			if (card.binding() && card.mousePressed(button))
				return true;

		int rail = railX();
		int y = top();

		if (button == 0 && (onLogo(mouseX, mouseY, rail, y) || (insetWidth() > 1 && onLogo(mouseX, mouseY, panelX(), y)))) {
			holding = true;
			heldSince = java.lang.System.currentTimeMillis();
			grabbedAt = mouseX;
			return true;
		}

		int column = insetWidth() > 1 ? panelX() : rail;
		int columnW = insetWidth() > 1 ? insetWidth() : railWidth();

		int slot = y + 50;
		for (Category category : Category.values()) {
			if (mouseX >= column + 4 && mouseX <= column + columnW - 4 && mouseY >= slot - 5 && mouseY <= slot + GLYPH + 4) {
				if (selected != category) {
					Sounds.play(Sounds.CLICK);
					icons.get(category).play();
				}

				if (scrolled == 0)
					scrolled = java.lang.System.currentTimeMillis();

				pressing = button == 0;
				selected = category;
				scroll = 0;
				return true;
			}

			slot += SLOT;
		}

		int panel = listX();
		int cardY = (int) (y + PAD - scroll);

		if (selected == Category.CLIENT && query.isEmpty() && announcementCard.height() > 0) {
			if (announcementCard.click(panel + PAD, cardY, listW() - PAD * 2, mouseX, mouseY, button))
				return true;
			cardY += announcementCard.height() + CARD_GAP;
		}

		List<Card> list = visible();
		int[][] spots = spots(list, cardY);
		for (int i = 0; i < list.size(); i++)
			if (list.get(i).click(panel + PAD + spots[i][0], spots[i][1], cardW(), mouseX, mouseY, button))
				return true;

		return super.mouseClicked(click, doubled);
	}

	private boolean onLogo(int mouseX, int mouseY, int rail, int y) {
		return mouseX >= rail + 5 && mouseX <= rail + 41 && mouseY >= y && mouseY <= y + 36;
	}

	@Override
	public boolean mouseDragged(Click click, double deltaX, double deltaY) {
		int mouseX = (int) mapX(click.x());
		int mouseY = (int) mapY(click.y());

		if (pressing) {
			glide = Math.max(0f, Math.min(Category.values().length - 1, (mapY(click.y()) - top() - 63f) / SLOT));
			Category near = Category.values()[Math.round(glide)];

			if (near != selected) {
				Sounds.play(Sounds.CLICK);
				icons.get(near).play();
				selected = near;
				scroll = 0;
			}
			return true;
		}

		if (editing) {
			int moved = grabbedAt - mouseX;

			if (moved > 26)
				form = 2;
			else if (moved < -26)
				form = 0;
			else form = 1;

			return true;
		}

		Picker.drag(mouseX, mouseY);

		List<Card> list = visible();
		int[][] spots = spots(list, 0);
		for (int i = 0; i < list.size(); i++)
			list.get(i).drag(listX() + PAD + spots[i][0], cardW(), mouseX, mouseY);

		return super.mouseDragged(click, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(Click click) {
		pressing = false;
		glide = -1f;

		Picker.release();

		if (editing) {
			editing = false;
			holding = false;
			Sounds.play(Sounds.CLICK);
			return true;
		}

		holding = false;

		for (Card card : visible())
			card.release();

		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int total = 0;
		if (selected == Category.CLIENT && query.isEmpty() && announcementCard.height() > 0)
			total += announcementCard.height() + CARD_GAP;

		total += stacked(visible());

		float limit = Math.max(0, total - (PANEL_H - PAD * 2));
		float before = scroll;
		scroll = Math.max(0, Math.min(limit, scroll - (float) verticalAmount * 20f));

		if (scroll != before) {
			Sounds.play(Sounds.SCROLL, 0.4f);

			if (scrolled == 0)
				scrolled = java.lang.System.currentTimeMillis();
		}

		return true;
	}

	@Override
	public boolean keyPressed(KeyInput keyInput) {
		int key = keyInput.key();

		if (Picker.pressed(key, keyInput.modifiers()))
			return true;

		for (Card card : visible())
			if (card.binding())
				return card.pressed(key);

		for (Card card : visible())
			if (card.pressed(key, keyInput.modifiers()))
				return true;

		if (key == GLFW.GLFW_KEY_S && (keyInput.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
			for (List<Card> list : cards.values())
				for (Card card : list)
					if (card.save())
						return true;

			return true;
		}

		if (key == GLFW.GLFW_KEY_F && (keyInput.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
			searching = !searching;
			if (!searching)
				query = "";

			Sounds.play(Sounds.COLLAPSE);
			return true;
		}

		if (searching && key == GLFW.GLFW_KEY_ESCAPE) {
			searching = false;
			query = "";
			return true;
		}

		if (key == GLFW.GLFW_KEY_BACKSPACE) {
			if (searching) {
				if (!query.isEmpty())
					query = query.substring(0, query.length() - 1);

				return true;
			}

			for (Card card : visible())
				for (Row row : card.rows())
					if (row.focused())
						return row.backspace();
		}

		return super.keyPressed(keyInput);
	}

	@Override
	public boolean charTyped(CharInput input) {
		if (!input.isValidChar())
			return false;

		String typed = input.asString();

		if (Picker.type(typed))
			return true;

		for (Card card : visible())
			if (card.type(typed))
				return true;

		if (searching) {
			query += typed;
			Sounds.play(Sounds.KEYPRESS, 0.6f);
			return true;
		}

		for (Card card : visible())
			for (Row row : card.rows())
				if (row.focused())
					return row.type(typed);

		return super.charTyped(input);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		System.INSTANCE.getModuleManager().getModule(ClickGUI.class).setEnabledStatus(false);
		onGuiClose();
	}

	public void onGuiClose() {
		if (!shutting) {
			Picker.close();
			shutting = true;
			closing = 0f;
			ClickGUI.uiSound(Sounds.CLOSE_QUICK);
			return;
		}
	}

	private void finish() {
		if (System.INSTANCE.previousScreen instanceof TitleScreen) {
			mc.setScreen(null);
		} else {
			mc.setScreen(System.INSTANCE.previousScreen);
		}
		System.INSTANCE.previousScreen = null;

		fade.snap(0f);
		progress = 0f;
		pressing = false;
		glide = -1f;
		searching = false;
		query = "";
		scroll = 0;
	}
}
