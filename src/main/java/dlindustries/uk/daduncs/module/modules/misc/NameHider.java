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

package dlindustries.uk.daduncs.module.modules.misc;

import dlindustries.uk.daduncs.System;
import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.string;
import dlindustries.uk.daduncs.utils.EncryptedString;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.util.ArrayList;
import java.util.List;

public final class NameHider extends Module implements TickListener {

	public NameHider() {
		super(EncryptedString.of("Name Hider"), EncryptedString.of("Shows your own name as something else"), -1, Category.MISC);
		addSettings(name);
	}

	private final string name = new string(EncryptedString.of("Name"), "system");

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		show(null);
		super.onDisable();
	}

	@Override
	public void onTick() {
		show(name.getValue());
	}

	private void show(String shown) {
		if (mc.player == null) return;
		if (mc.getNetworkHandler() == null) return;

		PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());

		if (entry != null) {
			entry.setDisplayName(shown == null || shown.isEmpty() ? null : Text.literal(shown));
		}
	}

	public static Text hide(Text message) {
		NameHider module = active();

		if (message == null || module == null) return message;

		String real = System.mc.player.getGameProfile().name();

		if (!message.getString().contains(real)) return message;

		return swap(message, real, module.name.getValue());
	}

	public static String hide(String text) {
		NameHider module = active();

		if (text == null || module == null) return text;

		return text.replace(System.mc.player.getGameProfile().name(), module.name.getValue());
	}

	public static OrderedText hide(OrderedText text) {
		NameHider module = active();

		if (text == null || module == null) return text;

		String real = System.mc.player.getGameProfile().name();
		StringBuilder plain = new StringBuilder();
		List<Style> styles = new ArrayList<>();

		text.accept((index, style, codePoint) -> {
			plain.appendCodePoint(codePoint);

			for (int i = 0; i < Character.charCount(codePoint); i++)
				styles.add(style);

			return true;
		});

		String found = plain.toString();

		if (!found.contains(real)) return text;

		MutableText swapped = Text.empty();
		int i = 0;

		while (i < found.length()) {
			if (found.startsWith(real, i)) {
				swapped.append(Text.literal(module.name.getValue()).setStyle(styles.get(i)));
				i += real.length();
				continue;
			}

			int codePoint = found.codePointAt(i);
			swapped.append(Text.literal(Character.toString(codePoint)).setStyle(styles.get(i)));
			i += Character.charCount(codePoint);
		}

		return swapped.asOrderedText();
	}

	private static NameHider active() {
		NameHider module = System.INSTANCE.getModuleManager().getModule(NameHider.class);

		if (module == null || !module.isEnabled() || System.mc.player == null) return null;

		String shown = module.name.getValue();
		String real = System.mc.player.getGameProfile().name();

		if (real == null || real.isEmpty() || shown == null || shown.isEmpty()) return null;

		return module;
	}

	private static Text swap(Text text, String real, String shown) {
		MutableText copy;

		if (text.getContent() instanceof PlainTextContent plain) {
			copy = Text.literal(plain.string().replace(real, shown)).setStyle(text.getStyle());
		} else if (text.getContent() instanceof TranslatableTextContent translatable) {
			Object[] args = translatable.getArgs().clone();

			for (int i = 0; i < args.length; i++) {
				if (args[i] instanceof Text arg) {
					args[i] = swap(arg, real, shown);
				} else if (args[i] instanceof String word) {
					args[i] = word.replace(real, shown);
				}
			}

			copy = Text.translatable(translatable.getKey(), args).setStyle(text.getStyle());
		} else {
			copy = text.copyContentOnly().setStyle(text.getStyle());
		}

		for (Text sibling : text.getSiblings()) {
			copy.append(swap(sibling, real, shown));
		}

		return copy;
	}
}
