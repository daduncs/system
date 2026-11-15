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

package dlindustries.uk.daduncs;

import dlindustries.uk.daduncs.event.EventManager;
import dlindustries.uk.daduncs.gui.ClickGui;
import dlindustries.uk.daduncs.utils.Sounds;
import dlindustries.uk.daduncs.managers.AccentManager;
import dlindustries.uk.daduncs.managers.ConfigManager;
import dlindustries.uk.daduncs.managers.FriendManager;
import dlindustries.uk.daduncs.managers.LyricsManager;
import dlindustries.uk.daduncs.managers.MediaManager;
import dlindustries.uk.daduncs.managers.ProfileManager;
import dlindustries.uk.daduncs.managers.Recorder;
import dlindustries.uk.daduncs.module.ModuleManager;
import dlindustries.uk.daduncs.utils.rotation.RotatorManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.io.File;
import java.io.IOException;
import java.net.*;

@SuppressWarnings("all")
public final class System {
	public RotatorManager rotatorManager;
	public ProfileManager profileManager;
	public ConfigManager configManager;
	public ModuleManager moduleManager;
	public EventManager eventManager;
	public FriendManager friendManager;
	public AccentManager accentManager;
	public MediaManager mediaManager;
	public LyricsManager lyricsManager;
	public Recorder recorder;
	public dlindustries.uk.daduncs.managers.UpdateManager updateManager;
	public static MinecraftClient mc;
	public final String version = FabricLoader.getInstance().getModContainer("marlowcrystal")
			.orElseThrow().getMetadata().getCustomValue("system:version").getAsString();
	public static boolean BETA;
	public static System INSTANCE;
	public boolean guiInitialized;
	public ClickGui clickGui;
	public dlindustries.uk.daduncs.argonsui.ClickGui argonGui;
	private dlindustries.uk.daduncs.gui.muon.MuonGui muonGui;
	public Screen previousScreen = null;
	public long lastModified;
	public File systemJar;

	public System() throws InterruptedException, IOException {
		INSTANCE = this;
		Sounds.init();
		this.eventManager = new EventManager();
		this.eventManager.add(dlindustries.uk.daduncs.event.events.TickListener.class, dlindustries.uk.daduncs.managers.SlotManager.INSTANCE);
		this.recorder = new Recorder();
		this.moduleManager = new ModuleManager();
		this.rotatorManager = new RotatorManager();
		this.profileManager = new ProfileManager();
		this.configManager = new ConfigManager(profileManager.getProfileFolderPath());
		this.friendManager = new FriendManager();
		this.accentManager = new AccentManager();
		this.mediaManager = new MediaManager();
		this.lyricsManager = new LyricsManager();
		this.updateManager = new dlindustries.uk.daduncs.managers.UpdateManager();
		this.clickGui = new ClickGui();
		this.argonGui = new dlindustries.uk.daduncs.argonsui.ClickGui();
		this.accentManager.scan();
		mc = MinecraftClient.getInstance();
		this.guiInitialized = false;

		this.configManager.init();
		this.setLastModified();
		this.updateManager.fetchAsync();
	}

	public ProfileManager getProfileManager() {
		return profileManager;
	}

	public ConfigManager getConfigManager() {
		return configManager;
	}

	public ModuleManager getModuleManager() {
		return moduleManager;
	}

	public AccentManager getAccentManager() {
		return accentManager;
	}

	public MediaManager getMediaManager() {
		return mediaManager;
	}

	public LyricsManager getLyricsManager() {
		return lyricsManager;
	}

	public FriendManager getFriendManager() {
		return friendManager;
	}

	public EventManager getEventManager() {
		return eventManager;
	}

	public Recorder getRecorder() {
		return recorder;
	}

	public dlindustries.uk.daduncs.managers.UpdateManager getUpdateManager() {
		return updateManager;
	}

	public ClickGui getClickGui() {
		return clickGui;
	}

	public void resetModifiedDate() {
		this.systemJar.setLastModified(lastModified);
	}

	public String getVersion() {
		return version;
	}

	public void setLastModified() {
		try {
			this.systemJar = new File(System.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			this.lastModified = systemJar.lastModified();
		} catch (URISyntaxException ignored) {}
	}

	public dlindustries.uk.daduncs.gui.muon.MuonGui muonGui() {
		if (muonGui == null)
			muonGui = new dlindustries.uk.daduncs.gui.muon.MuonGui();

		return muonGui;
	}
}
