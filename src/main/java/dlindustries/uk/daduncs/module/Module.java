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

package dlindustries.uk.daduncs.module;

import dlindustries.uk.daduncs.System;
import dlindustries.uk.daduncs.event.EventManager;
import dlindustries.uk.daduncs.module.setting.Setting;
import dlindustries.uk.daduncs.module.setting.bind;

import net.minecraft.client.MinecraftClient;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class Module implements Serializable {
	private final List<Setting<?>> settings = new ArrayList<>();
	public final EventManager eventManager = System.INSTANCE.eventManager;
	protected MinecraftClient mc = MinecraftClient.getInstance();
	private CharSequence name;
	private CharSequence description;
	private CharSequence tag;
	private boolean enabled;
	private int key;
	private Category category;

	public Module(CharSequence name, CharSequence description, int key, Category category) {
		this.name = name;
		this.description = description;
		this.enabled = false;
		this.key = key;
		this.category = category;
	}

	public void toggle() {
		enabled = !enabled;
		if (enabled)
			onEnable();
		else onDisable();
	}

	public CharSequence getName() {
		return name;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public CharSequence getDescription() {
		return description;
	}

	public int getKey() {
		for (Setting<?> setting : settings)
			if (setting instanceof bind value && value.isModuleKey())
				return value.getKey();

		return key;
	}

	public Category getCategory() {
		return category;
	}

	public void setCategory(Category category) {
		this.category = category;
	}

	public void setName(CharSequence name) {
		this.name = name;
	}

	public void setDescription(CharSequence description) {
		this.description = description;
	}

	public CharSequence getTag() {
		return tag;
	}

	public void setTag(CharSequence tag) {
		this.tag = tag;
	}

	public boolean claim(int mouseX, int mouseY) {
		return false;
	}

	public void setKey(int key) {
		this.key = key;
	}

	public List<Setting<?>> getSettings() {
		return settings;
	}

	public void onEnable() {}

	public void onDisable() {}

	/** Discard work that belongs to the previous player, including after respawn. */
	public void onPlayerChanged() {}

	public void addSetting(Setting<?> setting) {
		this.settings.add(setting);
	}

	public void addSettings(Setting<?>... settings) {
		this.settings.addAll(Arrays.asList(settings));
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
		if (enabled)
			onEnable();
		else onDisable();
	}

	public void setEnabledStatus(boolean enabled) {
		this.enabled = enabled;
	}

}
