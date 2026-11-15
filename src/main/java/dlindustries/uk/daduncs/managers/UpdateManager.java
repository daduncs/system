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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dlindustries.uk.daduncs.System;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.util.Util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class UpdateManager {

	public static final String RELEASE_ON_GITHUB =
			"https://api.github.com/repos/daduncs/system/releases/latest";

	public static final String DISCORD_INVITE = "https://discord.gg/kP8Mj4BPXv";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String RELEASES_PAGE = "https://github.com/daduncs/system/releases";

	private record Release(String version, String downloadUrl, boolean updateAvailable) {}

	private volatile Release latestRelease = new Release("", RELEASES_PAGE, false);
	private volatile boolean fetched = false;
	private volatile boolean loading = false;

	public UpdateManager() {
	}

	public synchronized void fetchAsync() {
		if (loading) return;
		loading = true;

		CompletableFuture.runAsync(() -> {
			try {
				HttpClient client = HttpClient.newBuilder()
						.connectTimeout(Duration.ofSeconds(4))
						.followRedirects(HttpClient.Redirect.NORMAL)
						.build();

				HttpRequest relReq = HttpRequest.newBuilder()
						.uri(URI.create(RELEASE_ON_GITHUB))
						.timeout(Duration.ofSeconds(5))
						.header("User-Agent", "System-Client")
						.header("Accept", "application/vnd.github.v3+json")
						.GET()
						.build();

				HttpResponse<String> relResp = client.send(relReq, HttpResponse.BodyHandlers.ofString());
				if (relResp.statusCode() == 200 && relResp.body() != null && !relResp.body().isBlank()) {
					JsonObject relJson = GSON.fromJson(relResp.body(), JsonObject.class);
					if (relJson != null) {
						latestRelease = readRelease(relJson);
					}
				}
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
			} catch (Exception ignored) {
			} finally {
				fetched = true;
				loading = false;
			}
		});
	}

	private static Release readRelease(JsonObject json) {
		String tag = readString(json, "tag_name");
		String version = tag.isEmpty() ? readString(json, "name") : tag;
		String downloadUrl = readString(json, "html_url");
		if (json.has("assets") && json.get("assets").isJsonArray()) {
			JsonArray assets = json.getAsJsonArray("assets");
			if (!assets.isEmpty() && assets.get(0).isJsonObject()) {
				String assetUrl = readString(assets.get(0).getAsJsonObject(), "browser_download_url");
				if (!assetUrl.isEmpty()) downloadUrl = assetUrl;
			}
		}
		String current = System.INSTANCE == null ? "" : System.INSTANCE.getVersion();
		return new Release(version, downloadUrl.isEmpty() ? RELEASES_PAGE : downloadUrl,
				isNewerVersion(current, tag));
	}

	private static String readString(JsonObject json, String key) {
		return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString().strip() : "";
	}

	static boolean isNewerVersion(String current, String tag) {
		try {
			SemanticVersion installed = SemanticVersion.parse(current.strip().replaceFirst("(?i)^[vb]+", ""));
			SemanticVersion published = SemanticVersion.parse(tag.strip().replaceFirst("(?i)^[vb]+", ""));
			return published.compareTo(installed) > 0;
		} catch (VersionParsingException ignored) {
			return false;
		}
	}

	public void openUrl(String url) {
		if (url == null || url.isBlank()) return;
		try {
			Util.getOperatingSystem().open(URI.create(url));
		} catch (Exception ignored) {
		}
	}

	public boolean hasUpdate() {
		return latestRelease.updateAvailable();
	}

	public boolean hasUnread() {
		return hasUpdate();
	}

	public boolean isFetched() {
		return fetched;
	}

	public boolean isLoading() {
		return loading;
	}

	public String getPageTitle() {
		return "About System";
	}

	public String getLatestVersion() {
		return latestRelease.version();
	}

	public String getDownloadUrl() {
		return latestRelease.downloadUrl();
	}

	public List<?> getBlocks() {
		return Collections.emptyList();
	}
}
