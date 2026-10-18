package dlindustries.uk.daduncs.font;

import java.text.Bidi;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ScriptFonts {

	private static final int LIMIT = 400;
	private static final String TWO = "\u0622\u0623\u0624\u0625\u0627\u0629\u062F\u0630\u0631\u0632\u0648\u0649";
	private static final int[] FORMS = {
			0xFE80, 0xFE81, 0xFE83, 0xFE85, 0xFE87, 0xFE89, 0xFE8D, 0xFE8F, 0xFE93, 0xFE95,
			0xFE99, 0xFE9D, 0xFEA1, 0xFEA5, 0xFEA9, 0xFEAB, 0xFEAD, 0xFEAF, 0xFEB1, 0xFEB5,
			0xFEB9, 0xFEBD, 0xFEC1, 0xFEC5, 0xFEC9, 0xFECD, 0, 0, 0, 0,
			0, 0, 0xFED1, 0xFED5, 0xFED9, 0xFEDD, 0xFEE1, 0xFEE5, 0xFEE9, 0xFEED,
			0xFEEF, 0xFEF1
	};
	private static final Map<String, Atlas> atlases = new HashMap<>();

	public static GlyphPageFontRenderer of(CharSequence text, int size, int style) {
		String file = file(text);
		if (file == null) return null;

		String key = file + ":" + size + ":" + style;
		Atlas atlas = atlases.get(key);

		if (atlas == null || !atlas.covers(text)) {
			Set<Character> chars = atlas == null || atlas.chars().size() > LIMIT ? new HashSet<>() : new HashSet<>(atlas.chars());

			for (int i = 0; i < text.length(); i++)
				chars.add(text.charAt(i));
			chars.add('.');

			if (atlas != null && atlas.font() != null)
				atlas.font().destroy();

			atlas = new Atlas(chars, GlyphPageFontRenderer.createFor("/assets/system/font/" + file, size, style, letters(chars)));
			atlases.put(key, atlas);
		}

		return atlas.font();
	}

	private static String file(CharSequence text) {
		boolean wide = false;
		boolean hangul = false;
		boolean han = false;
		boolean greek = false;

		for (int i = 0; i < text.length(); i++) {
			char letter = text.charAt(i);
			if (letter < 256) continue;

			wide = true;

			switch (Character.UnicodeScript.of(letter)) {
				case HIRAGANA, KATAKANA -> {
					return "Japanese.ttf";
				}
				case HANGUL -> hangul = true;
				case HAN -> han = true;
				case GREEK -> greek = true;
				case ARABIC -> {
					return "Arabic.ttf";
				}
				case DEVANAGARI -> {
					return "Hindi.ttf";
				}
				default -> {
				}
			}
		}

		if (!wide) return null;
		if (hangul) return "Korean.ttf";
		if (han) return "Chinese.ttf";
		if (greek) return "misans.ttf";

		return "unbounded.ttf";
	}

	public static String shape(String text) {
		StringBuilder shaped = new StringBuilder();

		for (int i = 0; i < text.length(); i++) {
			char letter = text.charAt(i);
			int base = form(letter);

			if (letter == 'ि' && !shaped.isEmpty()) {
				int at = shaped.length() - 1;
				if (shaped.charAt(at) == '़' && at > 0) at--;
				while (at >= 2 && shaped.charAt(at - 1) == '्') at -= 2;

				shaped.insert(at, letter);
				continue;
			}

			if (base == 0) {
				shaped.append(letter);
				continue;
			}

			char before = neighbour(text, i, -1);
			char after = neighbour(text, i, 1);
			boolean joinBefore = joinsAfter(before);

			if (letter == '\u0644' && "\u0622\u0623\u0625\u0627".indexOf(after) != -1) {
				int lamAlef = switch (after) {
					case '\u0622' -> 0xFEF5;
					case '\u0623' -> 0xFEF7;
					case '\u0625' -> 0xFEF9;
					default -> 0xFEFB;
				};
				shaped.append((char) (lamAlef + (joinBefore ? 1 : 0)));
				i = text.indexOf(after, i + 1);
				continue;
			}

			boolean joinAfter = joinsAfter(letter) && form(after) != 0 && after != '\u0621';

			if (letter == '\u0621') shaped.append((char) base);
			else if (joinBefore && joinAfter) shaped.append((char) (base + 3));
			else if (joinAfter) shaped.append((char) (base + 2));
			else if (joinBefore) shaped.append((char) (base + 1));
			else shaped.append((char) base);
		}

		return shaped.toString();
	}

	public static String visual(String text) {
		Bidi bidi = new Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
		if (bidi.isLeftToRight()) return text;

		int count = bidi.getRunCount();
		byte[] levels = new byte[count];
		Object[] runs = new Object[count];

		for (int i = 0; i < count; i++) {
			String run = text.substring(bidi.getRunStart(i), bidi.getRunLimit(i));
			levels[i] = (byte) bidi.getRunLevel(i);
			runs[i] = levels[i] % 2 == 1 ? new StringBuilder(run).reverse().toString() : run;
		}

		Bidi.reorderVisually(levels, 0, runs, 0, count);

		StringBuilder line = new StringBuilder();
		for (Object run : runs) line.append(run);
		return line.toString();
	}

	private static int form(char letter) {
		if (letter < 0x0621 || letter > 0x064A) return 0;
		return FORMS[letter - 0x0621];
	}

	private static boolean joinsAfter(char letter) {
		if (letter == '\u0640') return true;
		return form(letter) != 0 && letter != '\u0621' && TWO.indexOf(letter) == -1;
	}

	private static char neighbour(String text, int i, int step) {
		for (int j = i + step; j >= 0 && j < text.length(); j += step) {
			char letter = text.charAt(j);
			if (letter < 0x064B || letter > 0x0652) return letter;
		}
		return ' ';
	}

	private static char[] letters(Set<Character> chars) {
		char[] letters = new char[chars.size()];
		int i = 0;

		for (char letter : chars)
			letters[i++] = letter;

		return letters;
	}

	private record Atlas(Set<Character> chars, GlyphPageFontRenderer font) {
		private boolean covers(CharSequence text) {
			for (int i = 0; i < text.length(); i++)
				if (!chars.contains(text.charAt(i))) return false;

			return true;
		}
	}

	private ScriptFonts() {
	}
}
