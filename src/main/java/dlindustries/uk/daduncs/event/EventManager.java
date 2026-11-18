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

package dlindustries.uk.daduncs.event;

import dlindustries.uk.daduncs.System;
import dlindustries.uk.daduncs.managers.InputContext;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Objects;


public final class EventManager {
	private final HashMap<Class<? extends Listener>, ArrayList<PrioritizedListener<? extends Listener>>> listenerMap;

	public EventManager() {
		listenerMap = new HashMap<>();
	}

	public static <L extends Listener, E extends Event<L>> void fire(E event) {
		EventManager eventManager = System.INSTANCE == null ? null : System.INSTANCE.getEventManager();
		if (eventManager != null) {
			eventManager.fireImpl(event);
		}
	}

	private <L extends Listener, E extends Event<L>> void fireImpl(E event) {
		Class<L> listenerType = event.getListenerType();
		ArrayList<PrioritizedListener<L>> listeners2;
		synchronized (listenerMap) {
			ArrayList<PrioritizedListener<L>> listeners = (ArrayList<PrioritizedListener<L>>) (Object) listenerMap.get(listenerType);
			if (listeners == null || listeners.isEmpty())
				return;
			listeners2 = new ArrayList<>(listeners);
		}

		listeners2.removeIf(Objects::isNull);
		listeners2.sort(Comparator.comparingInt((PrioritizedListener<L> listener) -> listener.getPriority()).reversed());

		ArrayList<L> listeners3 = new ArrayList<>(listeners2.size());
		for (PrioritizedListener<L> listener : listeners2) {
			listeners3.add(listener.getListener());
		}

		InputContext.dispatchListeners(() -> event.fire(listeners3));
	}

	public <L extends Listener> void add(Class<L> type, L listener) {
		add(type, listener, 0);
	}

	public <L extends Listener> void add(Class<L> type, L listener, int priority) {
		synchronized (listenerMap) {
			ArrayList<PrioritizedListener<L>> listeners = (ArrayList<PrioritizedListener<L>>) (Object) listenerMap.get(type);
			if (listeners == null) {
				listeners = new ArrayList<>();
				listenerMap.put(type, (ArrayList<PrioritizedListener<? extends Listener>>) (Object) listeners);
			}
			if (listeners.stream().anyMatch(l -> l.getListener().equals(listener)))
				return;
			listeners.add(new PrioritizedListener(listener, priority));
		}
	}

	public <L extends Listener> void remove(Class<L> type, L listener) {
		synchronized (listenerMap) {
			ArrayList<PrioritizedListener<L>> listeners = (ArrayList<PrioritizedListener<L>>) (Object) listenerMap.get(type);
			if (listeners != null)
				listeners.removeIf(l -> l.getListener().equals(listener));
		}
	}

	
	private static class PrioritizedListener<L extends Listener> {
		private final L listener;
		private final int priority;

		public PrioritizedListener(L listener) {
			this(listener, 0);
		}

		public PrioritizedListener(L listener, int priority) {
			this.listener = listener;
			this.priority = priority;
		}

		public int getPriority() {
			return priority;
		}

		public L getListener() {
			return listener;
		}
	}
}
