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

package dlindustries.uk.daduncs.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LyricsManager {

	private static final String AGENT = "system-client";
	private static final String GET = "https://lrclib.net/api/get?artist_name=";
	private static final String SEARCH = "https://lrclib.net/api/search?q=";
	private static final int LAST_LINE = 5000;
	private static final int PICKS = 4;

	private final List<Line> lines = new CopyOnWriteArrayList<>();
	private volatile String key = "";

	public record Line(int time, String text) {
	}

	public void follow(MediaManager.Track track) {
		if (track == null || track.empty()) return;
		if (track.key().equals(key)) return;

		key = track.key();
		lines.clear();

		String artist = track.artist();
		String title = track.title();
		int length = track.duration();

		Thread thread = new Thread(() -> hunt(artist, title, length), "lyrics");
		thread.setDaemon(true);
		thread.start();
	}

	public Line showing(int position) {
		Line found = null;

		for (Line line : lines) {
			if (line.time() > position) break;

			found = line;
		}

		return found;
	}

	public Line next(int position) {
		for (Line line : lines) {
			if (line.time() > position) return line;
		}

		return null;
	}

	public float read(int position) {
		Line found = null;
		int end = 0;

		for (Line line : lines) {
			if (line.time() > position) {
				end = line.time();
				break;
			}

			found = line;
		}

		if (found == null) return 0f;

		if (end <= found.time()) end = found.time() + LAST_LINE;

		return Math.max(0f, Math.min(1f, (position - found.time()) / (float) (end - found.time())));
	}

	private void hunt(String artist, String title, int length) {
		String plain = strip(title);

		if (get(artist, plain)) return;
		if (!plain.equals(title) && get(artist, title)) return;

		search(artist + " " + plain, artist, plain, length);
	}

	private boolean get(String artist, String title) {
		try {
			HttpResponse<String> response = send(GET + encode(artist) + "&track_name=" + encode(title));

			if (response.statusCode() != 200) return false;

			return read(JsonParser.parseString(response.body()).getAsJsonObject());
		} catch (Exception ignored) {
			return false;
		}
	}

	private void search(String term, String artist, String title, int length) {
		try {
			HttpResponse<String> response = send(SEARCH + encode(term));

			if (response.statusCode() != 200) return;

			JsonArray found = JsonParser.parseString(response.body()).getAsJsonArray();

			for (int i = 0; i < found.size() && i < PICKS; i++) {
				JsonObject song = found.get(i).getAsJsonObject();

				if (same(song, artist, title, length) && read(song)) return;
			}
		} catch (Exception ignored) {
		}
	}

	private static boolean same(JsonObject song, String artist, String title, int length) {
		boolean named = song.has("trackName") && close(song.get("trackName").getAsString(), title);
		boolean sung = song.has("artistName") && close(song.get("artistName").getAsString(), artist);
		boolean timed = length > 0 && song.has("duration") && !song.get("duration").isJsonNull()
				&& Math.abs(song.get("duration").getAsDouble() * 1000 - length) <= 4000;

		return named && (sung || timed);
	}

	private static boolean close(String found, String wanted) {
		String one = found.toLowerCase().trim();
		String two = wanted.toLowerCase().trim();

		return !one.isEmpty() && !two.isEmpty() && (one.contains(two) || two.contains(one));
	}

	private boolean read(JsonObject song) {
		if (!song.has("syncedLyrics")) return false;
		if (song.get("syncedLyrics").isJsonNull()) return false;

		List<Line> found = new ArrayList<>();

		for (String row : song.get("syncedLyrics").getAsString().split("\n")) {
			int close = row.indexOf(']');

			if (!row.startsWith("[") || close < 0) continue;

			String text = row.substring(close + 1).trim();
			int time = stamp(row.substring(1, close));

			if (time >= 0 && !text.isEmpty()) found.add(new Line(time, text));
		}

		if (found.isEmpty()) return false;

		found.sort((one, two) -> Integer.compare(one.time(), two.time()));
		lines.clear();
		lines.addAll(found);
		return true;
	}

	private static HttpResponse<String> send(String url) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.header("User-Agent", AGENT)
				.GET()
				.build();

		return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
	}

	private static String encode(String text) {
		return URLEncoder.encode(text, StandardCharsets.UTF_8);
	}

	private static int stamp(String time) {
		try {
			String[] parts = time.split(":");

			if (parts.length != 2) return -1;

			return (int) (Integer.parseInt(parts[0].trim()) * 60000 + Float.parseFloat(parts[1].trim()) * 1000);
		} catch (Exception ignored) {
			return -1;
		}
	}

	private static String strip(String title) {
		return title.replaceAll("(?i)\\s*[(\\[](feat|ft)\\.?[^)\\]]*[)\\]]", "")
				.replaceAll("(?i)\\s*[-(]\\s*(remaster(ed)?|radio edit)[^)]*\\)?\\s*$", "")
				.trim();
	}
}
