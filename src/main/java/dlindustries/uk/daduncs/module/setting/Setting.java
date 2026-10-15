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

package dlindustries.uk.daduncs.module.setting;

import java.util.function.BooleanSupplier;

public abstract class Setting<T extends Setting<T>> {
	private CharSequence name;
	public CharSequence description;

	public Setting(CharSequence name) {
		this.name = name;
	}

	public void setName(CharSequence name) {
		this.name = name;
	}

	public CharSequence getName() {
		return name;
	}

	public CharSequence getDescription() {
		return description;
	}

	public T setDescription(CharSequence desc) {
		this.description = desc;
		return (T) this;
	}

	private CharSequence tag;
	private boolean showDescription;

	public CharSequence getTag() {
		return tag;
	}

	public T setTag(CharSequence tag) {
		this.tag = tag;
		return (T) this;
	}

	public boolean shouldShowDescription() {
		return showDescription || (tag != null && tag.toString().toUpperCase().contains("DESC"));
	}

	public T setShowDescription(boolean showDescription) {
		this.showDescription = showDescription;
		return (T) this;
	}

	private BooleanSupplier visibility = () -> true;

	public boolean isVisible() {
		return visibility.getAsBoolean();
	}

	public T setVisibility(BooleanSupplier visibility) {
		this.visibility = visibility;
		return (T) this;
	}
}
