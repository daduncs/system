package dlindustries.uk.daduncs.module.modules.crystal;

import dlindustries.uk.daduncs.event.events.ButtonListener;
import dlindustries.uk.daduncs.event.events.MouseUpdateListener;
import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.mixin.KeyBindingAccessor;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.module.setting.range;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.InventoryUtils;
import dlindustries.uk.daduncs.utils.TimerUtils;
import org.lwjgl.glfw.GLFW;

public final class DHand extends Module implements ButtonListener, MouseUpdateListener, TickListener {

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
		eventManager.add(MouseUpdateListener.class, this);
		eventManager.add(TickListener.class, this);
		super.onEnable();
		pending = false;
	}

	@Override
	public void onDisable() {
		eventManager.remove(ButtonListener.class, this);
		eventManager.remove(MouseUpdateListener.class, this);
		eventManager.remove(TickListener.class, this);
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
	public void onMouseUpdate() {
		if (pending && timer.hasReached(wait)) swap();
	}

	@Override
	public void onTick() {
		if (pending) swap();
	}

	private void swap() {
		pending = false;
		if (mc.player == null || mc.currentScreen != null) return;

		int target = slot.getValueInt() - 1;
		if (mc.player.getInventory().getSelectedSlot() != target)
			InventoryUtils.setInvSlot(target);
	}
}
