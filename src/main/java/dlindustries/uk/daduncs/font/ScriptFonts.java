package dlindustries.uk.daduncs.font;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ScriptFonts {

	private static final int LIMIT = 400;
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
		for (int i = 0; i < text.length(); i++)
			if (text.charAt(i) >= 256) return "unbounded.ttf";

		return null;
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
