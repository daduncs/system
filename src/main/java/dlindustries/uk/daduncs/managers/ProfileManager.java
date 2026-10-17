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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dlindustries.uk.daduncs.gui.HudEditor;
import dlindustries.uk.daduncs.System;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.setting.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public final class ProfileManager {
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private final Path profileFolderPath;
	private final Path profilePath;

	public ProfileManager() {
		this.profileFolderPath = Paths.get(java.lang.System.getProperty("user.dir"), "systemconfigv2");
		this.profilePath = profileFolderPath.resolve("profile.json");
	}

	public Path getProfileFolderPath() {
		return profileFolderPath;
	}

	public void loadProfile() {
		loadProfile(profilePath, false);
	}

	public void loadProfile(Path path, boolean keepGui) {
		if (path == null || !Files.isRegularFile(path)) return;

		try {
			if (Files.size(path) > 10 * 1024 * 1024) return;
			String content = Files.readString(path, StandardCharsets.UTF_8);
			JsonObject profile = gson.fromJson(content, JsonObject.class);
			if (profile == null) return;

			if (System.INSTANCE == null || System.INSTANCE.getModuleManager() == null) return;

			for (Module module : System.INSTANCE.getModuleManager().getModules()) {
				try {
					String moduleName = module.getName().toString();
					if (!profile.has(moduleName)) continue;

					JsonElement moduleElement = profile.get(moduleName);
					if (!moduleElement.isJsonObject()) continue;
					JsonObject moduleConfig = moduleElement.getAsJsonObject();

					if (moduleConfig.has("hudX") && moduleConfig.has("hudY")) {
						try {
							HudEditor.place(module, moduleConfig.get("hudX").getAsInt(), moduleConfig.get("hudY").getAsInt());
						} catch (Throwable ignored) {
						}
					}

					for (Setting<?> setting : module.getSettings()) {
						try {
							String settingName = setting.getName().toString();

							if (!moduleConfig.has(settingName)) {
								if (settingName.equals("Airplace key - HOLD") && moduleConfig.has("Activate key - HOLD")) {
									settingName = "Activate key - HOLD";
								} else if (settingName.equals("Activate key - HOLD") && moduleConfig.has("Airplace key - HOLD")) {
									settingName = "Airplace key - HOLD";
								} else if (settingName.equalsIgnoreCase("Airplace key hold") && moduleConfig.has("Airplace key hold")) {
									settingName = "Airplace key hold";
								} else {
									continue;
								}
							}

							JsonElement settingJson = moduleConfig.get(settingName);
							if (settingJson == null) continue;

							if (setting instanceof bool booleanSetting) {
								if (settingJson.isJsonPrimitive()) {
									booleanSetting.setValue(settingJson.getAsBoolean());
								}
							} else if (setting instanceof mode<?> modeSetting) {
								if (settingJson.isJsonPrimitive()) {
									modeSetting.setModeIndex(settingJson.getAsInt());
								}
							} else if (setting instanceof colour colourSetting) {
								if (settingJson.isJsonPrimitive()) {
									colourSetting.setValue(settingJson.getAsInt());
								}
							} else if (setting instanceof number numberSetting) {
								if (settingJson.isJsonPrimitive()) {
									numberSetting.setValue(settingJson.getAsDouble());
								}
							} else if (setting instanceof bind keybindSetting) {
								if (settingJson.isJsonPrimitive()) {
									keybindSetting.setKey(settingJson.getAsInt());
									if (keybindSetting.isModuleKey()) {
										module.setKey(settingJson.getAsInt());
									}
								} else if (settingJson.isJsonObject()) {
									JsonObject bObj = settingJson.getAsJsonObject();
									if (bObj.has("key")) {
										keybindSetting.setKey(bObj.get("key").getAsInt());
										if (keybindSetting.isModuleKey()) {
											module.setKey(bObj.get("key").getAsInt());
										}
									}
									if (bObj.has("hold")) {
										keybindSetting.setHold(bObj.get("hold").getAsBoolean());
									}
								}
							} else if (setting instanceof string stringSetting) {
								if (settingJson.isJsonPrimitive()) {
									stringSetting.setValue(settingJson.getAsString());
								}
							} else if (setting instanceof range minMaxSetting) {
								if (settingJson.isJsonObject()) {
									JsonObject minMaxObject = settingJson.getAsJsonObject();
									if (minMaxObject.has("min") && minMaxObject.has("max")) {
										minMaxSetting.setMinValue(minMaxObject.get("min").getAsDouble());
										minMaxSetting.setMaxValue(minMaxObject.get("max").getAsDouble());
									}
								}
							}
						} catch (Throwable ignored) {
						}
					}

					if (moduleConfig.has("enabled") && moduleConfig.get("enabled").isJsonPrimitive()) {
						boolean shouldEnable = moduleConfig.get("enabled").getAsBoolean();
						if (module instanceof ClickGUI) {
							if (!keepGui) {
								module.setEnabledStatus(false);
							}
						} else {
							try {
								module.setEnabled(shouldEnable);
							} catch (Throwable t) {
								try {
									module.setEnabledStatus(shouldEnable);
								} catch (Throwable ignored) {
								}
							}
						}
					}
				} catch (Throwable ignored) {
				}
			}
		} catch (Throwable ignored) {
		}
	}

	public boolean isProfile(JsonObject profile) {
		if (profile == null) {
			return false;
		}

		if (System.INSTANCE == null || System.INSTANCE.getModuleManager() == null) {
			return false;
		}

		for (Module module : System.INSTANCE.getModuleManager().getModules()) {
			JsonElement element = profile.get(module.getName().toString());
			if (element != null && element.isJsonObject()) {
				return true;
			}
		}

		return false;
	}

	public void saveProfile() {
		try {
			Files.createDirectories(profileFolderPath);
			JsonObject profile = createProfile();
			if (!isProfile(profile)) return;

			File target = profilePath.toFile();
			File temp = new File(profileFolderPath.toFile(), "profile.tmp." + java.lang.System.nanoTime());
			Files.writeString(temp.toPath(), gson.toJson(profile), StandardCharsets.UTF_8);

			try {
				Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (Exception e) {
				Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (Throwable ignored) {
		}
	}

	public JsonObject createProfile() {
		JsonObject profile = new JsonObject();
		if (System.INSTANCE == null || System.INSTANCE.getModuleManager() == null) {
			return profile;
		}

		for (Module module : System.INSTANCE.getModuleManager().getModules()) {
			JsonObject moduleConfig = new JsonObject();
			moduleConfig.addProperty("enabled", module.isEnabled());

			if (HudEditor.placed(module)) {
				moduleConfig.addProperty("hudX", HudEditor.x(module, 0));
				moduleConfig.addProperty("hudY", HudEditor.y(module, 0));
			}

			for (Setting<?> setting : module.getSettings()) {
				String settingName = setting.getName().toString();

				if (setting instanceof bool booleanSetting) {
					moduleConfig.addProperty(settingName, booleanSetting.getValue());
				} else if (setting instanceof mode<?> modeSetting) {
					moduleConfig.addProperty(settingName, modeSetting.getModeIndex());
				} else if (setting instanceof colour colourSetting) {
					moduleConfig.addProperty(settingName, colourSetting.getPicked());
				} else if (setting instanceof number numberSetting) {
					moduleConfig.addProperty(settingName, numberSetting.getValue());
				} else if (setting instanceof bind keybindSetting) {
					moduleConfig.addProperty(settingName, keybindSetting.getKey());
				} else if (setting instanceof string stringSetting) {
					moduleConfig.addProperty(settingName, stringSetting.getValue());
				} else if (setting instanceof range minMaxSetting) {
					JsonObject minMaxObject = new JsonObject();
					minMaxObject.addProperty("min", minMaxSetting.getMinValue());
					minMaxObject.addProperty("max", minMaxSetting.getMaxValue());
					moduleConfig.add(settingName, minMaxObject);
				}
			}

			profile.add(module.getName().toString(), moduleConfig);
		}

		return profile;
	}
}