package dlindustries.uk.daduncs.module.modules.crystal;

import dlindustries.uk.daduncs.event.events.ButtonListener;
import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.mixin.KeyBindingAccessor;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.module.setting.range;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.InventoryUtils;
import dlindustries.uk.daduncs.utils.TimerUtils;
import org.lwjgl.glfw.GLFW;

public final class DHand extends Module implements ButtonListener, TickListener {

	private final number slot = new number(EncryptedString.of("Slot"), 1, 9, 9, 1);
	private final range delay = new range(EncryptedString.of("Delay"), 0, 50, 1, 5, 50);

	private final TimerUtils timer = new TimerUtils();

	private boolean pending;
	private long wait;

	public DHand() {
		super(EncryptedString.of("DHand"), EncryptedString.of("Goes on your totem hotbar slot when you open inv."), -1, Category.CRYSTAL);
		addSettings(slot, delay);
	}

	@Override
	public void onEnable() {
		eventManager.add(ButtonListener.class, this);
		eventManager.add(TickListener.class, this);
		super.onEnable();
		pending = false;
	}

	@Override
	public void onDisable() {
		eventManager.remove(ButtonListener.class, this);
		eventManager.remove(TickListener.class, this);
		pending = false;
		super.onDisable();
	}

	@Override
	public void onButtonPress(ButtonEvent event) {
		if (event.action != GLFW.GLFW_PRESS || mc.player == null || mc.currentScreen != null) return;
		if (event.button != ((KeyBindingAccessor) mc.options.inventoryKey).getBoundKey().getCode()) return;

		pending = true;
		wait = delay.getRandomValueLong();
		timer.reset();
	}

	@Override
	public void onTick() {
		if (pending && timer.hasReached(wait)) swap();
	}

	private void swap() {
		if (mc.player == null || mc.currentScreen != null) {
			pending = false;
			return;
		}
		if (!AntibanbyDaduncs.canSwapSlotNow() || AntibanbyDaduncs.isSlotPending()) {
			return;
		}
		pending = false;

		int target = slot.getValueInt() - 1;
		if (mc.player.getInventory().getSelectedSlot() != target)
			InventoryUtils.setInvSlot(target);
	}
}
