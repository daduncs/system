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

package dlindustries.uk.daduncs.font;

import dlindustries.uk.daduncs.utils.EncryptedString;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

import java.awt.*;
import java.util.Random;

public final class GlyphPageFontRenderer {

	private static final char SECTION_SIGN = '\u00A7';

	public Random fontRandom = new Random();

	private float posX;
	private float posY;
	private final int[] colorCode = new int[32];
	private boolean boldStyle;
	private boolean italicStyle;
	private boolean underlineStyle;
	private boolean strikethroughStyle;

	private final GlyphPage regularGlyphPage;

	private final GlyphPage boldGlyphPage;
	private final GlyphPage italicGlyphPage;
	private final GlyphPage boldItalicGlyphPage;


	GlyphPage page() {
		return regularGlyphPage;
	}

	public GlyphPageFontRenderer(GlyphPage regularGlyphPage, GlyphPage boldGlyphPage, GlyphPage italicGlyphPage,
								 GlyphPage boldItalicGlyphPage) {
		this.regularGlyphPage = regularGlyphPage;
		this.boldGlyphPage = boldGlyphPage;
		this.italicGlyphPage = italicGlyphPage;
		this.boldItalicGlyphPage = boldItalicGlyphPage;

		for (int i = 0; i < 32; ++i) {
			int j = (i >> 3 & 1) * 85;
			int k = (i >> 2 & 1) * 170 + j;
			int l = (i >> 1 & 1) * 170 + j;
			int i1 = (i & 1) * 170 + j;

			if (i == 6) {
				k += 85;
			}

			if (i >= 16) {
				k /= 4;
				l /= 4;
				i1 /= 4;
			}

			this.colorCode[i] = (k & 255) << 16 | (l & 255) << 8 | i1 & 255;
		}
	}

	public static GlyphPageFontRenderer create(CharSequence fontName, int size, boolean bold, boolean italic,
											   boolean boldItalic) {
		char[] chars = new char[256];

		for (int i = 0; i < chars.length; i++) {
			chars[i] = (char) i;
		}

		GlyphPage regularPage;

		regularPage = new GlyphPage(new Font(fontName.toString(), Font.PLAIN, size), true, true);

		regularPage.generateGlyphPage(chars);
		regularPage.setupTexture();

		GlyphPage boldPage = regularPage;
		GlyphPage italicPage = regularPage;
		GlyphPage boldItalicPage = regularPage;

		if (bold) {
			boldPage = new GlyphPage(new Font(fontName.toString(), Font.BOLD, size), true, true);

			boldPage.generateGlyphPage(chars);
			boldPage.setupTexture();
		}

		if (italic) {
			italicPage = new GlyphPage(new Font(fontName.toString(), Font.ITALIC, size), true, true);

			italicPage.generateGlyphPage(chars);
			italicPage.setupTexture();
		}

		if (boldItalic) {
			boldItalicPage = new GlyphPage(new Font(fontName.toString(), Font.BOLD | Font.ITALIC, size), true, true);

			boldItalicPage.generateGlyphPage(chars);
			boldItalicPage.setupTexture();
		}

		return new GlyphPageFontRenderer(regularPage, boldPage, italicPage, boldItalicPage);
	}

	public static GlyphPageFontRenderer createStyled(CharSequence id, int size, int style) {
		char[] chars = new char[256];

		for (int i = 0; i < chars.length; i++) {
			chars[i] = (char) i;
		}

		Font font = null;

		try {
			font = Font.createFont(Font.TRUETYPE_FONT, GlyphPageFontRenderer.class.getResourceAsStream(id.toString()))
					.deriveFont(style, size);
		} catch (Exception e) {
			return null;
		}

		GlyphPage page = new GlyphPage(font, true, true);
		page.generateGlyphPage(chars);
		page.setupTexture();

		return new GlyphPageFontRenderer(page, page, page, page);
	}

	public static GlyphPageFontRenderer createFromID(CharSequence id, int size, boolean bold, boolean italic,
													 boolean boldItalic) {
		char[] chars = new char[256];

		for (int i = 0; i < chars.length; i++) {
			chars[i] = (char) i;
		}

		Font font = null;

		try {
			font = Font.createFont(Font.TRUETYPE_FONT, GlyphPageFontRenderer.class.getResourceAsStream(id.toString()))
					.deriveFont(Font.PLAIN, size);
		} catch (Exception ignored) {
		}

		GlyphPage regularPage;

		regularPage = new GlyphPage(font, true, true);
		regularPage.generateGlyphPage(chars);
		regularPage.setupTexture();

		GlyphPage boldPage = regularPage;
		GlyphPage italicPage = regularPage;
		GlyphPage boldItalicPage = regularPage;

		try {
			if (bold) {
				boldPage = new GlyphPage(
						Font.createFont(Font.TRUETYPE_FONT, GlyphPageFontRenderer.class.getResourceAsStream(id.toString()))
								.deriveFont(Font.BOLD, size),
						true, true);

				boldPage.generateGlyphPage(chars);
				boldPage.setupTexture();
			}

			if (italic) {
				italicPage = new GlyphPage(
						Font.createFont(Font.TRUETYPE_FONT, GlyphPageFontRenderer.class.getResourceAsStream(id.toString()))
								.deriveFont(Font.ITALIC, size),
						true, true);

				italicPage.generateGlyphPage(chars);
				italicPage.setupTexture();
			}

			if (boldItalic) {
				boldItalicPage = new GlyphPage(
						Font.createFont(Font.TRUETYPE_FONT, GlyphPageFontRenderer.class.getResourceAsStream(id.toString()))
								.deriveFont(Font.BOLD | Font.ITALIC, size),
						true, true);

				boldItalicPage.generateGlyphPage(chars);
				boldItalicPage.setupTexture();
			}

		} catch (Exception ignored) {
		}

		return new GlyphPageFontRenderer(regularPage, boldPage, italicPage, boldItalicPage);
	}

	public void drawGlow(DrawContext context, CharSequence text, float x, float y, int color, float spread) {
		GlyphPage glyphPage = getCurrentGlyphPage();

		Matrix3x2fStack matrices = context.getMatrices();
		matrices.pushMatrix();
		matrices.scale(0.5f, 0.5f);

		float posX = x * 2.0f;
		float posY = y * 2.0f;

		for (int i = 0; i < text.length(); i++)
			posX += glyphPage.drawCharGlow(context, text.charAt(i), posX, posY, color, spread);

		matrices.popMatrix();
	}

	public int drawStringWithShadow(DrawContext context, CharSequence text, float x, float y, int color) {
		return drawString(context, text, x, y, color, true);
	}

	public int drawStringWithShadow(DrawContext context, CharSequence text, double x, double y, int color) {
		return drawString(context, text, (float) x, (float) y, color, true);
	}

	public int drawString(DrawContext context, CharSequence text, float x, float y, int color) {
		return drawString(context, text, x, y, color, false);
	}

	public int drawString(DrawContext context, CharSequence text, double x, double y, int color) {
		return drawString(context, text, (float) x, (float) y, color, false);
	}

	public int drawCenteredString(DrawContext context, CharSequence text, double x, double y, float scale, int color) {
		return drawString(context, text, (float) x - getStringWidth(text) / 2, (float) y, scale, color, false);
	}

	public int drawCenteredString(DrawContext context, CharSequence text, double x, double y, int color) {
		return drawString(context, text, (float) x - getStringWidth(text) / 2, (float) y, color, false);
	}

	public int drawCenteredStringWidthShadow(DrawContext context, CharSequence text, double x, double y, int color) {
		return drawString(context, text, (float) x - getStringWidth(text) / 2, (float) y, color, true);
	}

	public int drawString(DrawContext context, CharSequence text, float x, float y, float scale, int color, boolean dropShadow) {
		this.resetStyles();
		int i;

		if (dropShadow) {
			i = this.renderString(context, text, x + 1.0F, y + 1.0F, scale, color, true);
			i = Math.max(i, this.renderString(context, text, x, y, scale, color, false));
		} else {
			i = this.renderString(context, text, x, y, scale, color, false);
		}

		return i;
	}

	public int drawString(DrawContext context, CharSequence text, float x, float y, int color, boolean dropShadow) {
		this.resetStyles();
		int i;

		if (dropShadow) {
			i = this.renderString(context, text, x + 1.0F, y + 1.0F, color, true);
			i = Math.max(i, this.renderString(context, text, x, y, color, false));
		} else {
			i = this.renderString(context, text, x, y, color, false);
		}

		return i;
	}

	private int renderString(DrawContext context, CharSequence text, float x, float y, int color, boolean dropShadow) {
		if (text == null) {
			return 0;
		} else {

			if ((color & -67108864) == 0) {
				return 0;
			}

			if (dropShadow) {
				color = (color & 16579836) >> 2 | color & -16777216;
			}
			this.posX = x * 2.0f;
			this.posY = y * 2.0f;
			this.renderStringAtPos(context, text, dropShadow, color);
			return (int) (this.posX / 4.0f);
		}
	}

	private int renderString(DrawContext context, CharSequence text, float x, float y, float scale, int color, boolean dropShadow) {
		if (text == null) {
			return 0;
		} else {

			if ((color & -67108864) == 0) {
				return 0;
			}

			if (dropShadow) {
				color = (color & 16579836) >> 2 | color & -16777216;
			}
			this.posX = x * 2.0f;
			this.posY = y * 2.0f;
			this.renderStringAtPos(context, text, scale, dropShadow, color);
			return (int) (this.posX / 4.0f);
		}
	}

	private void renderStringAtPos(DrawContext context, CharSequence text, boolean shadow, int color) {
		GlyphPage glyphPage = getCurrentGlyphPage();
		float alpha = (float) (color >> 24 & 255) / 255.0F;
		float red = (float) (color >> 16 & 255) / 255.0F;
		float green = (float) (color >> 8 & 255) / 255.0F;
		float blue = (float) (color & 255) / 255.0F;

		Matrix3x2fStack matrices = context.getMatrices();
		matrices.pushMatrix();
		matrices.scale(0.5F, 0.5F);

		for (int i = 0; i < text.length(); ++i) {
			char c0 = text.charAt(i);

			if (c0 == SECTION_SIGN && i + 1 < text.length()) {
				int i1 = "0123456789abcdefklmnor".indexOf(Character.toLowerCase(text.charAt(i + 1)));

				if (i1 < 16) {
					this.boldStyle = false;
					this.strikethroughStyle = false;
					this.underlineStyle = false;
					this.italicStyle = false;

					if (i1 < 0) {
						i1 = 15;
					}

					if (shadow) {
						i1 += 16;
					}

					int j1 = this.colorCode[i1];

					red = (float) (j1 >> 16 & 255) / 255.0F;
					green = (float) (j1 >> 8 & 255) / 255.0F;
					blue = (float) (j1 & 255) / 255.0F;
				} else if (i1 == 16) {
				} else if (i1 == 17) {
					this.boldStyle = true;
				} else if (i1 == 18) {
					this.strikethroughStyle = true;
				} else if (i1 == 19) {
					this.underlineStyle = true;
				} else if (i1 == 20) {
					this.italicStyle = true;
				} else {
					this.boldStyle = false;
					this.strikethroughStyle = false;
					this.underlineStyle = false;
					this.italicStyle = false;
				}

				++i;
			} else {
				glyphPage = getCurrentGlyphPage();

				int currentColor = toArgb(red, green, blue, alpha);
				float f = glyphPage.drawChar(context, c0, posX, posY, currentColor);

				doDraw(context, f, glyphPage, currentColor);
			}
		}

		matrices.popMatrix();
	}

	private void renderStringAtPos(DrawContext context, CharSequence text, float scale, boolean shadow, int color) {
		GlyphPage glyphPage = getCurrentGlyphPage();
		float alpha = (float) (color >> 24 & 255) / 255.0F;
		float red = (float) (color >> 16 & 255) / 255.0F;
		float green = (float) (color >> 8 & 255) / 255.0F;
		float blue = (float) (color & 255) / 255.0F;

		Matrix3x2fStack matrices = context.getMatrices();
		matrices.pushMatrix();
		matrices.scale(scale, scale);

		for (int i = 0; i < text.length(); ++i) {
			char c0 = text.charAt(i);

			if (c0 == SECTION_SIGN && i + 1 < text.length()) {
				int i1 = "0123456789abcdefklmnor".indexOf(Character.toLowerCase(text.charAt(i + 1)));

				if (i1 < 16) {
					this.boldStyle = false;
					this.strikethroughStyle = false;
					this.underlineStyle = false;
					this.italicStyle = false;

					if (i1 < 0) {
						i1 = 15;
					}

					if (shadow) {
						i1 += 16;
					}

					int j1 = this.colorCode[i1];

					red = (float) (j1 >> 16 & 255) / 255.0F;
					green = (float) (j1 >> 8 & 255) / 255.0F;
					blue = (float) (j1 & 255) / 255.0F;
				} else if (i1 == 16) {
				} else if (i1 == 17) {
					this.boldStyle = true;
				} else if (i1 == 18) {
					this.strikethroughStyle = true;
				} else if (i1 == 19) {
					this.underlineStyle = true;
				} else if (i1 == 20) {
					this.italicStyle = true;
				} else {
					this.boldStyle = false;
					this.strikethroughStyle = false;
					this.underlineStyle = false;
					this.italicStyle = false;
				}

				++i;
			} else {
				glyphPage = getCurrentGlyphPage();

				int currentColor = toArgb(red, green, blue, alpha);
				float f = glyphPage.drawChar(context, c0, posX, posY, currentColor);

				doDraw(context, f, glyphPage, currentColor);
			}
		}

		matrices.popMatrix();
	}

	private void doDraw(DrawContext context, float f, GlyphPage glyphPage, int color) {
		if (this.strikethroughStyle) {
			drawLine(context, this.posX, this.posX + f, this.posY + (glyphPage.getMaxFontHeight() / 2.0F), color);
		}

		if (this.underlineStyle) {
			drawLine(context, this.posX - 1.0F, this.posX + f, this.posY + glyphPage.getMaxFontHeight(), color);
		}

		this.posX += f;
	}

	private void drawLine(DrawContext context, float startX, float endX, float y, int color) {
		int left = Math.round(Math.min(startX, endX));
		int right = Math.max(left + 1, Math.round(Math.max(startX, endX)));
		int top = Math.round(y - 1.0F);
		context.fill(left, top, right, top + 1, color);
	}

	private int toArgb(float red, float green, float blue, float alpha) {
		int packedAlpha = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
		int packedRed = Math.max(0, Math.min(255, Math.round(red * 255.0F)));
		int packedGreen = Math.max(0, Math.min(255, Math.round(green * 255.0F)));
		int packedBlue = Math.max(0, Math.min(255, Math.round(blue * 255.0F)));
		return packedAlpha << 24 | packedRed << 16 | packedGreen << 8 | packedBlue;
	}

	private GlyphPage getCurrentGlyphPage() {
		if (boldStyle && italicStyle)
			return boldItalicGlyphPage;
		else if (boldStyle)
			return boldGlyphPage;
		else if (italicStyle)
			return italicGlyphPage;
		else
			return regularGlyphPage;
	}

	private void resetStyles() {
		this.boldStyle = false;
		this.italicStyle = false;
		this.underlineStyle = false;
		this.strikethroughStyle = false;
	}

	public int getFontHeight() {
		return regularGlyphPage.getMaxFontHeight() / 2;
	}

	public float getInkLeft(CharSequence text) {
		if (text == null || text.isEmpty()) return 0f;

		return regularGlyphPage.inkLeft(text.charAt(0)) / 2f;
	}

	public float getInkWidth(CharSequence text) {
		if (text == null || text.isEmpty()) return 0f;

		float width = 0f;
		int last = text.length() - 1;

		for (int i = 0; i < last; i++)
			width += regularGlyphPage.getWidth(text.charAt(i)) - 8;

		width += regularGlyphPage.inkRight(text.charAt(last));

		return (width - regularGlyphPage.inkLeft(text.charAt(0))) / 2f;
	}

	public int getStringWidth(CharSequence text) {
		if (text == null) {
			return 0;
		}
		resetStyles();
		int width = 0;

		GlyphPage currentPage;

		int size = text.length();

		boolean on = false;

		for (int i = 0; i < size; i++) {
			char character = text.charAt(i);

			if (character == SECTION_SIGN)
				on = true;
			else if (on && character >= '0' && character <= 'r') {
				int colorIndex = "0123456789abcdefklmnor".indexOf(character);
				if (colorIndex < 16) {
					boldStyle = false;
					italicStyle = false;
				} else if (colorIndex == 17) {
					boldStyle = true;
				} else if (colorIndex == 20) {
					italicStyle = true;
				} else if (colorIndex == 21) {
					boldStyle = false;
					italicStyle = false;
				}
				i++;
				on = false;
			} else {
				if (on)
					i--;

				character = text.charAt(i);

				currentPage = getCurrentGlyphPage();

				width += currentPage.getWidth(character) - 8;
			}
		}

		return width / 2;
	}

	public CharSequence trimStringToWidth(CharSequence text, int width) {
		return this.trimStringToWidth(text, width, false);
	}

	public CharSequence trimStringToWidth(CharSequence text, int maxWidth, boolean reverse) {
		StringBuilder stringbuilder = new StringBuilder();
		resetStyles();

		boolean on = false;

		int j = reverse ? text.length() - 1 : 0;
		int k = reverse ? -1 : 1;
		int width = 0;

		GlyphPage currentPage;

		for (int i = j; i >= 0 && i < text.length() && i < maxWidth; i += k) {
			char character = text.charAt(i);

			if (character == SECTION_SIGN)
				on = true;
			else if (on && character >= '0' && character <= 'r') {
				int colorIndex = "0123456789abcdefklmnor".indexOf(character);
				if (colorIndex < 16) {
					boldStyle = false;
					italicStyle = false;
				} else if (colorIndex == 17) {
					boldStyle = true;
				} else if (colorIndex == 20) {
					italicStyle = true;
				} else if (colorIndex == 21) {
					boldStyle = false;
					italicStyle = false;
				}
				i++;
				on = false;
			} else {
				if (on)
					i--;

				character = text.charAt(i);

				currentPage = getCurrentGlyphPage();

				width += (currentPage.getWidth(character) - 8) / 2;
			}

			if (i > width) {
				break;
			}

			if (reverse) {
				stringbuilder.insert(0, character);
			} else {
				stringbuilder.append(character);
			}
		}

		return EncryptedString.of(stringbuilder.toString());
	}
}
