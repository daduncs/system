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
import dlindustries.uk.daduncs.event.events.ButtonListener;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.modules.client.Configs;
import dlindustries.uk.daduncs.module.modules.client.Friends;
import dlindustries.uk.daduncs.module.modules.cart.*;
import dlindustries.uk.daduncs.module.modules.crystal.*;
import dlindustries.uk.daduncs.module.modules.mace.*;
import dlindustries.uk.daduncs.module.modules.spear.*;
import dlindustries.uk.daduncs.module.modules.sword.*;
import dlindustries.uk.daduncs.module.modules.misc.*;
import dlindustries.uk.daduncs.module.modules.render.*;
import dlindustries.uk.daduncs.module.setting.Setting;
import dlindustries.uk.daduncs.module.setting.bind;
import dlindustries.uk.daduncs.utils.EncryptedString;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleManager implements ButtonListener {
	private final List<Module> modules = new ArrayList<>();
	private final List<Module> coordinatedModules = new ArrayList<>();

	public ModuleManager() {
		addModules();
		addKeybinds();
	}

	public void addModules() {
		addWithNativeInput(new ClickGUI());
		addWithNativeInput(new Friends());
		addWithNativeInput(new Configs());

		add(new AnchorMacro());
		add(new AutoCrystal());
		add(new AutoDoubleHand());
		add(new AutoInventoryTotem());
		add(new DHand());
		add(new DoubleAnchor());
		add(new DtapSetup());
		add(new HoverTotem());
		add(new UtilitySwapper());
		add(new HitManager());

		add(new AutoPearlPull());
		add(new AutoXP());
		add(new CrystalOptimizer());
		add(new ElytraSwap());
		addWithNativeInput(new FastPlace());
		add(new AutoBridge());
		add(new FireworkMacro());
		add(new KeyPearl());
		add(new NoBreakDelay());
		add(new NoJumpDelay());
		addWithNativeInput(new AntiMiss());
		addWithNativeInput(new PackSpoof());
		add(new PingSpoof());
		addWithNativeInput(new Prevent());
		addWithNativeInput(new SelfFlag());
		addWithNativeInput(new NameHider());
		add(new Fly());
		add(new NoDamage());
		addWithNativeInput(new AutoReconnect());

		addWithNativeInput(new Freecam());
		addWithNativeInput(new ModuleHud());
		addWithNativeInput(new EffectsHud());
		addWithNativeInput(new HotkeysHud());
		addWithNativeInput(new InventoryHud());
		addWithNativeInput(new NoBounce());
		addWithNativeInput(new PlayerESP());
		addWithNativeInput(new Esp());
		addWithNativeInput(new HandShaders());
		addWithNativeInput(new CustomSky());
		addWithNativeInput(new StorageEsp());
		addWithNativeInput(new TargetHud());
		addWithNativeInput(new Watermark());
		addWithNativeInput(new MusicHud());
		addWithNativeInput(new WorldLyrics());
		addWithNativeInput(new HitFx());
		addWithNativeInput(new Nametags());
		addWithNativeInput(new PopFx());

		add(new MaceSwap());
		add(new Grappler());

		add(new InvCart());

		add(new SpearLunge());

		add(new AimAssist());
		add(new AutoJumpReset());
		add(new AutoWTap());
		addWithNativeInput(new Sprint());
		add(new TriggerBot());
	}

	public List<Module> getEnabledModules() {
		return modules.stream()
				.filter(Module::isEnabled)
				.toList();
	}

	public boolean hasGameplayModulesEnabled() {
		for (Module module : coordinatedModules) {
			if (module.isEnabled()) return true;
		}
		return false;
	}

	public void onPlayerChanged() {
		for (Module module : modules) {
			if (module.isEnabled()) module.onPlayerChanged();
		}
	}


	public List<Module> getModules() {
		return Collections.unmodifiableList(modules);
	}

	public void addKeybinds() {
		System.INSTANCE.getEventManager().add(ButtonListener.class, this);

		for (Module module : modules)
			module.addSetting(new bind(EncryptedString.of("Keybind"), module.getKey(), true).setDescription(EncryptedString.of("Key to enabled the module")));
	}

	public List<Module> getModulesInCategory(Category category) {
		return modules.stream()
				.filter(module -> module.getCategory() == category)
				.toList();
	}

	@SuppressWarnings("unchecked")
	public <T extends Module> T getModule(Class<T> moduleClass) {
		return (T) modules.stream()
				.filter(moduleClass::isInstance)
				.findFirst()
				.orElse(null);
	}

	/** Register a module that needs the shared gameplay action rules. */
	public void add(Module module) {
		modules.add(module);
		coordinatedModules.add(module);
	}

	/** This module leaves gameplay actions to the normal Minecraft input loop. */
	public void addWithNativeInput(Module module) {
		modules.add(module);
	}

	@Override
	public void onButtonPress(ButtonEvent event) {
		if (event.button >= 179 && event.button <= 183 ||
				event.button == GLFW.GLFW_KEY_UNKNOWN) {
			return;
		}

		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.world == null || mc.player == null || mc.currentScreen instanceof TitleScreen) {
			return;
		}

		modules.forEach(module -> {
			if (module.getKey() != event.button)
				return;

			if (hold(module)) {
				boolean pressed = event.action != GLFW.GLFW_RELEASE;
				if (module.isEnabled() != pressed) module.setEnabled(pressed);
			} else if (event.action == GLFW.GLFW_PRESS)
				module.toggle();
		});
	}

	private static boolean hold(Module module) {
		for (Setting<?> setting : module.getSettings())
			if (setting instanceof bind value && value.isModuleKey())
				return value.isHold();

		return false;
	}
}
