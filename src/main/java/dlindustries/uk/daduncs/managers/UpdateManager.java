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

	private String latestReleaseTag = "";
	private String latestReleaseTitle = "";
	private String latestReleaseUrl = "https://github.com/daduncs/system/releases";
	private String latestReleaseDownloadUrl = "";

	private boolean fetched = false;
	private boolean loading = false;

	public UpdateManager() {
	}

	public void fetchAsync() {
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
						if (relJson.has("tag_name") && !relJson.get("tag_name").isJsonNull()) {
							this.latestReleaseTag = relJson.get("tag_name").getAsString();
						}
						if (relJson.has("name") && !relJson.get("name").isJsonNull()) {
							this.latestReleaseTitle = relJson.get("name").getAsString();
						}
						if (relJson.has("html_url") && !relJson.get("html_url").isJsonNull()) {
							this.latestReleaseUrl = relJson.get("html_url").getAsString();
						}
						if (relJson.has("assets") && relJson.get("assets").isJsonArray()) {
							JsonArray assets = relJson.getAsJsonArray("assets");
							if (!assets.isEmpty() && assets.get(0).isJsonObject()) {
								JsonObject firstAsset = assets.get(0).getAsJsonObject();
								if (firstAsset.has("browser_download_url") && !firstAsset.get("browser_download_url").isJsonNull()) {
									this.latestReleaseDownloadUrl = firstAsset.get("browser_download_url").getAsString();
								}
							}
						}
					}
				}
			} catch (Exception ignored) {
			} finally {
				loading = false;
				fetched = true;
			}
		});
	}

	public void openUrl(String url) {
		if (url == null || url.isBlank()) return;
		try {
			Util.getOperatingSystem().open(URI.create(url));
		} catch (Exception ignored) {
		}
	}

	public boolean hasUpdate() {
		if (!fetched || latestReleaseTag.isEmpty()) return false;
		if (System.INSTANCE == null || System.INSTANCE.version == null) return false;
		String current = System.INSTANCE.version.trim();
		if (current.isEmpty()) return false;

		boolean equalsTag = current.equalsIgnoreCase(latestReleaseTag.trim());
		boolean equalsTitle = !latestReleaseTitle.isEmpty() && current.equalsIgnoreCase(latestReleaseTitle.trim());

		String cleanCurrent = current.replaceAll("^[vVbB\\s]+", "");
		String cleanTag = latestReleaseTag.replaceAll("^[vVbB\\s]+", "");
		String cleanTitle = latestReleaseTitle.replaceAll("^[vVbB\\s]+", "");

		boolean equalsCleanTag = !cleanTag.isEmpty() && cleanCurrent.equalsIgnoreCase(cleanTag);
		boolean equalsCleanTitle = !cleanTitle.isEmpty() && cleanCurrent.equalsIgnoreCase(cleanTitle);

		return !(equalsTag || equalsTitle || equalsCleanTag || equalsCleanTitle);
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
		return !latestReleaseTag.isEmpty() ? latestReleaseTag : (!latestReleaseTitle.isEmpty() ? latestReleaseTitle : "");
	}

	public String getDownloadUrl() {
		if (!latestReleaseDownloadUrl.isEmpty()) return latestReleaseDownloadUrl;
		if (!latestReleaseUrl.isEmpty()) return latestReleaseUrl;
		return "https://github.com/daduncs/system/releases";
	}

	public List<?> getBlocks() {
		return Collections.emptyList();
	}
}
