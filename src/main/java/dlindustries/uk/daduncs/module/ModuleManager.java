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
import java.util.List;

public final class ModuleManager implements ButtonListener {
	private final List<Module> modules = new ArrayList<>();

	public ModuleManager() {
		addModules();
		addKeybinds();
	}

	public void addModules() {
		add(new ClickGUI());
		add(new Friends());
		add(new Configs());

		add(new AnchorMacro());
		add(new AutoCrystal());
		add(new AutoDoubleHand());
		add(new AutoInventoryTotem());
		add(new DoubleAnchor());
		add(new DtapSetup());
		add(new HoverTotem());
		add(new UtilitySwapper());
		add(new HitManager());

		add(new AutoPearlPull());
		add(new AutoXP());
		add(new CrystalOptimizer());
		add(new ElytraSwap());
		add(new FastPlace());
		add(new FireworkMacro());
		add(new KeyPearl());
		add(new NoBreakDelay());
		add(new NoJumpDelay());
		add(new AntiMiss());
		add(new PackSpoof());
		add(new PingSpoof());
		add(new Prevent());
		add(new SelfFlag());
		add(new NameHider());
		add(new Fly());
		add(new NoDamage());
		add(new AutoReconnect());

		add(new Freecam());
		add(new ModuleHud());
		add(new EffectsHud());
		add(new HotkeysHud());
		add(new InventoryHud());
		add(new NoBounce());
		add(new PlayerESP());
		add(new Esp());
		add(new HandShaders());
		add(new CustomSky());
		add(new StorageEsp());
		add(new TargetHud());
		add(new Watermark());
		add(new MusicHud());
		add(new WorldLyrics());
		add(new HitFx());
		add(new Nametags());

		add(new AutoMaceV1());
		add(new MaceSwap());
		add(new Grappler());
		add(new ElytraSwapV2());

		add(new AutoCart());
		add(new CrossbowCart());
		add(new InvCart());

		add(new SpearLunge());

		add(new AimAssist());
		add(new AutoJumpReset());
		add(new AutoWTap());
		add(new Sprint());
		add(new TriggerBot());
	}

	public List<Module> getEnabledModules() {
		return modules.stream()
				.filter(Module::isEnabled)
				.toList();
	}


	public List<Module> getModules() {
		return modules;
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

	public void add(Module module) {
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

			if (hold(module))
				module.setEnabled(event.action != GLFW.GLFW_RELEASE);
			else if (event.action == GLFW.GLFW_PRESS)
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