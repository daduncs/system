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

import dlindustries.uk.daduncs.utils.Cutout;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.imageio.ImageIO;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MediaManager {

	private static final String SPLIT = "|||";

	private static final String PROBE = """
			[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false
			$out = ''
			try {
			    [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
			    Add-Type -AssemblyName System.Runtime.WindowsRuntime
			    $wait = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
			    $kind = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
			    $ask = $kind::RequestAsync()
			    $hub = $wait.MakeGenericMethod($kind).Invoke($null, @([object]$ask)).Result
			    $live = $null
			    foreach ($one in $hub.GetSessions()) {
			        try { if ($one.GetPlaybackInfo().PlaybackStatus -eq 'Playing') { $live = $one; break } } catch {}
			    }
			    if (-not $live) { $live = $hub.GetCurrentSession() }
			    if ($live) {
			        $shape = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
			        $grab = $live.TryGetMediaPropertiesAsync()
			        $tags = $wait.MakeGenericMethod($shape).Invoke($null, @([object]$grab)).Result
			        $span = $live.GetTimelineProperties()
			        $state = $live.GetPlaybackInfo()
			        $at = [long]$span.Position.TotalMilliseconds
			        $len = [long](($span.EndTime.Ticks - $span.StartTime.Ticks) / 10000)
			        $going = if ($state.PlaybackStatus -eq 'Playing') { 1 } else { 0 }
			        if ($going -eq 1 -and $span.LastUpdatedTime.Ticks -gt 0) {
			            $drift = ((Get-Date).ToUniversalTime() - $span.LastUpdatedTime.UtcDateTime).TotalMilliseconds
			            if ($drift -ge 0) { $at = $at + [long]$drift; if ($len -gt 0 -and $at -gt $len) { $at = $len } }
			        }
			        $tag = ''
			        try { $tag = $live.SourceAppUserModelId } catch {}
			        $out = $tags.Artist + 'PH' + $tags.Title + 'PH' + $at + 'PH' + $len + 'PH' + $going + 'PH' + $tag
			    }
			} catch {}
			if ($out) { Write-Output $out }
			""".replace("PH", SPLIT);

	private static final String COVER = """
			[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false
			$done = ''
			try {
			    [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
			    [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime] | Out-Null
			    Add-Type -AssemblyName System.Runtime.WindowsRuntime
			    $wait = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
			    $kind = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
			    $hub = $wait.MakeGenericMethod($kind).Invoke($null, @([object]$kind::RequestAsync())).Result
			    $live = $null
			    foreach ($one in $hub.GetSessions()) {
			        try { if ($one.GetPlaybackInfo().PlaybackStatus -eq 'Playing') { $live = $one; break } } catch {}
			    }
			    if (-not $live) { $live = $hub.GetCurrentSession() }
			    if ($live) {
			        $shape = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
			        $tags = $wait.MakeGenericMethod($shape).Invoke($null, @([object]$live.TryGetMediaPropertiesAsync())).Result
			        if ($tags.Thumbnail) {
			            $kind = [Windows.Storage.Streams.IRandomAccessStreamWithContentType]
			            $stream = $wait.MakeGenericMethod($kind).Invoke($null, @([object]$tags.Thumbnail.OpenReadAsync())).Result
			            $read = [System.IO.WindowsRuntimeStreamExtensions].GetMethod('AsStreamForRead', [Type[]]@([Windows.Storage.Streams.IInputStream])).Invoke($null, @($stream))
			            $file = [System.IO.File]::Create($args[0])
			            $read.CopyTo($file)
			            $file.Close()
			            $read.Close()
			            $done = $tags.Artist + 'PH' + $tags.Title
			        }
			    }
			} catch {}
			if ($done) { Write-Output $done }
			""".replace("PH", SPLIT);

	private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor(job -> {
		Thread thread = new Thread(job, "media");
		thread.setDaemon(true);
		return thread;
	});

	private volatile Track track = Track.NOTHING;
	private volatile Identifier cover;
	private volatile Portrait artist;
	private volatile String artistKey = "";
	private final Map<String, Portrait> artists = new HashMap<>();
	private volatile int hot;
	private volatile int cold;
	private volatile String coverKey = "";
	private volatile String tried = "";
	private volatile long triedAt;
	private volatile boolean spotify;
	private volatile long stamp;

	private Path script;
	private Path coverScript;
	private boolean started;

	public record Portrait(Identifier id, float aspect) {
	}

	public record Track(String artist, String title, int position, int duration, boolean playing) {
		public static final Track NOTHING = new Track("", "", 0, 0, false);

		public boolean empty() {
			return title == null || title.isBlank();
		}

		public String key() {
			return (artist + "|" + title).toLowerCase();
		}
	}

	public void start() {
		if (started)
			return;

		started = true;
		clock.scheduleWithFixedDelay(this::poll, 0, 1, TimeUnit.SECONDS);
	}

	public Track getTrack() {
		return track;
	}

	public int getHot() {
		return hot;
	}

	public int getCold() {
		return cold;
	}

	public Identifier getCover() {
		return cover;
	}

	public Portrait getArtist(Track now) {
		String name = lead(now.artist());

		if (!name.equalsIgnoreCase(artistKey)) {
			artistKey = name;
			artist = artists.get(name.toLowerCase());

			if (artist == null && !name.isEmpty()) portrait(name);
		}

		return artist;
	}

	public boolean isSpotify() {
		return spotify;
	}

	public int elapsed() {
		Track now = track;
		if (now.empty())
			return 0;

		int drift = now.playing() ? (int) (java.lang.System.currentTimeMillis() - stamp) : 0;
		int at = now.position() + drift;

		return now.duration() > 0 ? Math.min(at, now.duration()) : at;
	}

	private void poll() {
		try {
			if (script == null || !Files.exists(script)) {
				script = Files.createTempFile("system_media_", ".ps1");
				Files.writeString(script, PROBE, StandardCharsets.UTF_8);
				script.toFile().deleteOnExit();
			}

			ProcessBuilder builder = new ProcessBuilder(shell(), "-NoProfile", "-NonInteractive",
					"-ExecutionPolicy", "Bypass", "-File", script.toString());
			builder.redirectErrorStream(false);

			Process process = builder.start();
			String line;

			try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				line = reader.readLine();
			}

			process.waitFor(4, TimeUnit.SECONDS);

			if (line == null || line.isBlank()) {
				track = Track.NOTHING;
				return;
			}

			read(line);
		} catch (Exception ignored) {
		}
	}

	private void read(String line) {
		String[] parts = line.split("\\|\\|\\|", -1);
		if (parts.length < 5)
			return;

		int at = number(parts[2]);
		int len = number(parts[3]);
		boolean going = "1".equals(parts[4].trim());

		track = new Track(parts[0].trim(), parts[1].trim(), at, len, going);
		stamp = java.lang.System.currentTimeMillis();
		spotify = parts.length > 5 && parts[5].toLowerCase().contains("spotify");

		load(track);
	}

	private void load(Track now) {
		String key = now.key();

		if (now.empty() || key.equals(coverKey))
			return;

		if (key.equals(tried) && java.lang.System.currentTimeMillis() - triedAt < 12000)
			return;

		tried = key;
		triedAt = java.lang.System.currentTimeMillis();

		Thread hunt = new Thread(() -> {
			byte[] bytes = thumbnail(now);

			for (int i = 0; i < 2 && bytes == null; i++) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ignored) {
					return;
				}

				bytes = thumbnail(now);
			}

			if (bytes == null)
				bytes = art("artist:\"" + now.artist() + "\" track:\"" + now.title() + "\"");

			if (bytes == null)
				bytes = art(now.artist() + " " + now.title());

			if (bytes == null)
				bytes = art(now.title());

			if (bytes == null)
				bytes = store(now.artist(), now.title());

			if (bytes == null)
				return;

			byte[] ready = bytes;
			coverKey = key;
			MinecraftClient.getInstance().execute(() -> upload(ready));
		}, "cover");

		hunt.setDaemon(true);
		hunt.start();
	}

	private byte[] thumbnail(Track now) {
		try {
			if (coverScript == null || !Files.exists(coverScript)) {
				coverScript = Files.createTempFile("system_cover_", ".ps1");
				Files.writeString(coverScript, COVER, StandardCharsets.UTF_8);
				coverScript.toFile().deleteOnExit();
			}

			Path image = Files.createTempFile("system_cover_", ".img");
			Process process = new ProcessBuilder(shell(), "-NoProfile", "-NonInteractive",
					"-ExecutionPolicy", "Bypass", "-File", coverScript.toString(), image.toString()).start();

			String line;
			try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				line = reader.readLine();
			}

			process.waitFor(4, TimeUnit.SECONDS);

			byte[] bytes = Files.readAllBytes(image);
			Files.deleteIfExists(image);

			if (line == null)
				return null;

			String[] parts = line.split("\\|\\|\\|", -1);
			if (parts.length < 2 || !parts[0].trim().equals(now.artist()) || !parts[1].trim().equals(now.title()))
				return null;

			return bytes.length > 0 ? bytes : null;
		} catch (Exception ignored) {
			return null;
		}
	}

	private static String shell() {
		String root = java.lang.System.getenv("SystemRoot");
		return (root == null ? "C:\\Windows" : root) + "\\System32\\WindowsPowerShell\\v1.0\\powershell.exe";
	}

	private static byte[] art(String term) {
		try {
			HttpClient web = HttpClient.newHttpClient();
			HttpRequest ask = HttpRequest.newBuilder()
					.uri(URI.create("https://api.deezer.com/search?limit=1&q="
							+ URLEncoder.encode(term, StandardCharsets.UTF_8)))
					.GET()
					.build();

			JsonArray found = JsonParser.parseString(web.send(ask, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body())
					.getAsJsonObject().getAsJsonArray("data");

			if (found.isEmpty())
				return null;

			String link = found.get(0).getAsJsonObject().getAsJsonObject("album").get("cover_big").getAsString();

			byte[] bytes = web.send(HttpRequest.newBuilder().uri(URI.create(link)).GET().build(),
					HttpResponse.BodyHandlers.ofByteArray()).body();

			return bytes.length > 0 ? bytes : null;
		} catch (Exception ignored) {
			return null;
		}
	}

	private static byte[] store(String artist, String title) {
		try {
			HttpClient web = HttpClient.newHttpClient();
			String term = URLEncoder.encode(artist + " " + title, StandardCharsets.UTF_8);

			HttpRequest ask = HttpRequest.newBuilder()
					.uri(URI.create("https://itunes.apple.com/search?entity=song&limit=8&term=" + term))
					.GET()
					.build();

			JsonArray found = JsonParser.parseString(web.send(ask, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body())
					.getAsJsonObject().getAsJsonArray("results");

			JsonObject best = pick(found, artist, title);

			if (best == null)
				return null;

			String link = best.get("artworkUrl100").getAsString().replace("100x100", "512x512");

			byte[] bytes = web.send(HttpRequest.newBuilder().uri(URI.create(link)).GET().build(),
					HttpResponse.BodyHandlers.ofByteArray()).body();

			return bytes.length > 0 ? bytes : null;
		} catch (Exception ignored) {
			return null;
		}
	}

	private static JsonObject pick(JsonArray found, String artist, String title) {
		JsonObject best = null;
		int score = -1;

		for (int i = 0; i < found.size(); i++) {
			JsonObject song = found.get(i).getAsJsonObject();

			if (!song.has("artworkUrl100"))
				continue;

			int worth = 0;

			if (song.has("artistName") && song.get("artistName").getAsString().equalsIgnoreCase(artist))
				worth += 2;

			if (song.has("trackName")) {
				String name = song.get("trackName").getAsString();

				if (name.equalsIgnoreCase(title)) worth += 2;
				else if (name.toLowerCase().contains(title.toLowerCase())) worth += 1;
			}

			if (worth <= score)
				continue;

			score = worth;
			best = song;
		}

		return best;
	}

	private void portrait(String name) {
		Thread hunt = new Thread(() -> {
			try {
				byte[] bytes = face(name);
				if (bytes == null) return;

				BufferedImage photo = ImageIO.read(new ByteArrayInputStream(bytes));
				if (photo == null) return;

				BufferedImage cut = Cutout.cut(photo);
				if (cut == null) return;

				ByteArrayOutputStream out = new ByteArrayOutputStream();
				ImageIO.write(cut, "PNG", out);
				byte[] ready = out.toByteArray();
				float aspect = (float) cut.getWidth() / cut.getHeight();

				MinecraftClient.getInstance().execute(() -> {
					try {
						NativeImage image = NativeImage.read(new ByteArrayInputStream(ready));
						NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "system_artist", image);

						Identifier id = Identifier.of("system", "artist_" + java.lang.System.nanoTime());
						MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
						texture.upload();

						Portrait done = new Portrait(id, aspect);
						artists.put(name.toLowerCase(), done);
						if (name.equalsIgnoreCase(artistKey)) artist = done;
					} catch (Exception ignored) {
					}
				});
			} catch (Exception ignored) {
			}
		}, "artist");

		hunt.setDaemon(true);
		hunt.start();
	}

	private static byte[] face(String name) {
		try {
			HttpClient web = HttpClient.newHttpClient();
			HttpRequest ask = HttpRequest.newBuilder()
					.uri(URI.create("https://api.deezer.com/search/artist?limit=5&q="
							+ URLEncoder.encode(name, StandardCharsets.UTF_8)))
					.GET()
					.build();

			JsonArray found = JsonParser.parseString(web.send(ask, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body())
					.getAsJsonObject().getAsJsonArray("data");

			for (int i = 0; i < found.size(); i++) {
				JsonObject one = found.get(i).getAsJsonObject();
				if (!one.get("name").getAsString().equalsIgnoreCase(name)) continue;

				String link = one.get("picture_xl").getAsString();
				if (link.contains("/artist//")) return null;

				byte[] bytes = web.send(HttpRequest.newBuilder().uri(URI.create(link)).GET().build(),
						HttpResponse.BodyHandlers.ofByteArray()).body();

				return bytes.length > 0 ? bytes : null;
			}

			return null;
		} catch (Exception ignored) {
			return null;
		}
	}

	private static String lead(String artist) {
		if (artist == null) return "";

		return artist.split("(?i),|&|;| feat\\.? | ft\\.? | x ")[0].trim();
	}

	private void upload(byte[] bytes) {
		try {
			BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
			if (decoded == null)
				return;

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(decoded, "PNG", out);

			NativeImage image = NativeImage.read(new ByteArrayInputStream(out.toByteArray()));
			NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "system_cover", image);

			Identifier id = Identifier.of("system", "cover_" + java.lang.System.nanoTime());
			MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
			texture.upload();

			cover = id;

			shades(decoded);
		} catch (Exception ignored) {
		}
	}

	private void shades(BufferedImage art) {
		int stepX = Math.max(1, art.getWidth() / 28);
		int stepY = Math.max(1, art.getHeight() / 28);

		int loud = 0;
		int other = 0;
		float best = -1f;
		float apart = -1f;

		for (int x = 0; x < art.getWidth(); x += stepX) {
			for (int y = 0; y < art.getHeight(); y += stepY) {
				int pixel = art.getRGB(x, y);
				float punch = punch(pixel);

				if (punch <= best) continue;

				best = punch;
				loud = pixel;
			}
		}

		for (int x = 0; x < art.getWidth(); x += stepX) {
			for (int y = 0; y < art.getHeight(); y += stepY) {
				int pixel = art.getRGB(x, y);
				float gap = gap(pixel, loud) + punch(pixel) * 0.5f;

				if (gap <= apart) continue;

				apart = gap;
				other = pixel;
			}
		}

		hot = 0xFF000000 | (loud & 0xFFFFFF);
		cold = 0xFF000000 | (other & 0xFFFFFF);
	}

	private static float punch(int color) {
		int r = (color >> 16) & 0xFF;
		int g = (color >> 8) & 0xFF;
		int b = color & 0xFF;

		int top = Math.max(r, Math.max(g, b));
		int low = Math.min(r, Math.min(g, b));

		return (top - low) * 0.72f + top * 0.28f;
	}

	private static float gap(int one, int two) {
		int dr = ((one >> 16) & 0xFF) - ((two >> 16) & 0xFF);
		int dg = ((one >> 8) & 0xFF) - ((two >> 8) & 0xFF);
		int db = (one & 0xFF) - (two & 0xFF);

		return (float) Math.sqrt(dr * dr + dg * dg + db * db);
	}

	private static int number(String raw) {
		try {
			return Math.max(0, (int) Double.parseDouble(raw.trim()));
		} catch (Exception ignored) {
			return 0;
		}
	}
}
