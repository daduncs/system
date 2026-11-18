package dlindustries.uk.daduncs.module.modules.crystal;

import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.module.setting.range;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.InventoryUtils;
import dlindustries.uk.daduncs.utils.TimerUtils;

public final class DHand extends Module implements TickListener {

	private final number slot = new number(EncryptedString.of("Slot"), 1, 9, 9, 1);
	private final range delay = new range(EncryptedString.of("Delay"), 0, 50, 1, 5, 50);

	private final TimerUtils timer = new TimerUtils();

	private boolean pending;
	private net.minecraft.client.gui.screen.ingame.InventoryScreen deferredScreen;
	private net.minecraft.client.network.ClientPlayerEntity pendingPlayer;

	public boolean deferInventoryScreen(net.minecraft.client.gui.screen.Screen screen) {
		if (!isEnabled() || !pending || !(screen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen inv)) return false;
		deferredScreen = inv;
		return true;
	}

	private void finishOpen() {
		var screen = deferredScreen;
		deferredScreen = null;
		pending = false;
		if (screen != null && mc.player == pendingPlayer && InventoryUtils.canOpenPlayerInventory()) mc.setScreen(screen);
		pendingPlayer = null;
	}
	private long wait;

	public DHand() {
		super(EncryptedString.of("DHand"), EncryptedString.of("Goes on your totem hotbar slot when you open inv."), -1, Category.CRYSTAL);
		addSettings(slot, delay);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this, Integer.MAX_VALUE);
		super.onEnable();
		pending = false;
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		finishOpen();
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (!pending && InventoryUtils.isPlayerInventoryOpenQueued()) {
			pendingPlayer = mc.player;
			pending = true;
			wait = delay.getRandomValueLong();
			timer.reset();
		}
		if (!pending) return;
		if (mc.player != pendingPlayer || !InventoryUtils.canOpenPlayerInventory()) {
			deferredScreen = null;
			pendingPlayer = null;
			pending = false;
			return;
		}
		if (mc.player.isUsingItem()) {
			finishOpen();
			return;
		}
		if (timer.hasReached(wait)) swap();
		// Other modules must not replace the chosen slot before vanilla opens the screen.
		// While the configured delay runs, give the pending open request priority too.
		AntibanbyDaduncs.reserveInventoryOpen();
	}

	private void swap() {
		if (mc.player == null || mc.player != pendingPlayer || mc.currentScreen != null) {
			deferredScreen = null;
			pendingPlayer = null;
			pending = false;
			return;
		}
		if (mc.player.isUsingItem()) {
			finishOpen();
			return;
		}
		if (!AntibanbyDaduncs.canSwapSlotNow() || AntibanbyDaduncs.isSlotPending()) {
			return;
		}
		pending = false;

		int target = slot.getValueInt() - 1;
		if (mc.player.getInventory().getSelectedSlot() != target)
			InventoryUtils.setInvSlot(target);
		if (mc.player.getInventory().getSelectedSlot() != target || AntibanbyDaduncs.isSlotPending()) {
			pending = true;
			return;
		}
		finishOpen();
	}
}
