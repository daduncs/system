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

import java.awt.Font;

public final class Fonts {
	public static GlyphPageFontRenderer QUICKSAND = GlyphPageFontRenderer.createFromID("/assets/system/font/font.ttf", 40, false, false, false);
	public static GlyphPageFontRenderer BOLD = GlyphPageFontRenderer.createStyled("/assets/system/font/unbounded.ttf", 40, Font.BOLD);
	public static GlyphPageFontRenderer LIGHT = GlyphPageFontRenderer.createStyled("/assets/system/font/unbounded.ttf", 26, Font.PLAIN);
	public static GlyphPageFontRenderer TIP = GlyphPageFontRenderer.createStyled("/assets/system/font/unbounded.ttf", 31, Font.PLAIN);
	public static GlyphPageFontRenderer SMALL = GlyphPageFontRenderer.createStyled("/assets/system/font/unbounded.ttf", 18, Font.PLAIN);
	public static GlyphPageFontRenderer SMALLBOLD = GlyphPageFontRenderer.createStyled("/assets/system/font/unbounded.ttf", 20, Font.BOLD);
	public static GlyphPageFontRenderer TITLE = GlyphPageFontRenderer.createStyled("/assets/system/font/unbounded.ttf", 30, Font.BOLD);
	public static GlyphPageFontRenderer MENU = GlyphPageFontRenderer.createStyled("/assets/system/font/unbounded.ttf", 24, Font.BOLD);
	public static GlyphPageFontRenderer TINY = GlyphPageFontRenderer.createStyled("/assets/system/font/unbounded.ttf", 15, Font.PLAIN);
	public static GlyphPageFontRenderer LABEL = GlyphPageFontRenderer.createStyled("/assets/system/font/misans.ttf", 30, Font.PLAIN);
	public static GlyphPageFontRenderer LABEL_MENU = GlyphPageFontRenderer.createStyled("/assets/system/font/misans.ttf", 26, Font.PLAIN);
	public static GlyphPageFontRenderer LABEL_SMALL = GlyphPageFontRenderer.createStyled("/assets/system/font/misans.ttf", 18, Font.PLAIN);
	public static GlyphPageFontRenderer PRODUCT_SANS_MEDIUM_36 = GlyphPageFontRenderer.createStyled("/assets/system/font/product_sans_medium.ttf", 36, Font.PLAIN);
	public static GlyphPageFontRenderer PRODUCT_SANS_MEDIUM_18 = GlyphPageFontRenderer.createStyled("/assets/system/font/product_sans_medium.ttf", 18, Font.PLAIN);
	public static GlyphPageFontRenderer PRODUCT_SANS_REGULAR_32 = GlyphPageFontRenderer.createStyled("/assets/system/font/product_sans_regular.ttf", 32, Font.PLAIN);
	public static GlyphPageFontRenderer PRODUCT_SANS_REGULAR_16 = GlyphPageFontRenderer.createStyled("/assets/system/font/product_sans_regular.ttf", 16, Font.PLAIN);
	public static GlyphPageFontRenderer CHAKRA = GlyphPageFontRenderer.createStyled("/assets/system/font/chakrapetch.ttf", 18, Font.PLAIN);
	public static GlyphPageFontRenderer RAJDHANI = GlyphPageFontRenderer.createStyled("/assets/system/font/rajdhani.ttf", 21, Font.PLAIN);
	public static GlyphPageFontRenderer TITILLIUM = GlyphPageFontRenderer.createStyled("/assets/system/font/titilliumweb.ttf", 18, Font.PLAIN);
	public static GlyphPageFontRenderer MICHROMA = GlyphPageFontRenderer.createStyled("/assets/system/font/michroma.ttf", 15, Font.PLAIN);
	public static GlyphPageFontRenderer POPPINS = GlyphPageFontRenderer.createStyled("/assets/system/font/poppins.ttf", 18, Font.PLAIN);
	public static GlyphPageFontRenderer SUPERHERO = GlyphPageFontRenderer.createStyled("/assets/system/font/superherotufffont.ttf", 80, Font.PLAIN);

	public static void preload() {
	}
}
