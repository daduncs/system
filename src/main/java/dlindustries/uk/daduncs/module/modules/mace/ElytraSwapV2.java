package dlindustries.uk.daduncs.module.modules.mace;

import dlindustries.uk.daduncs.event.events.MouseUpdateListener;
import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.setting.bind;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.InventoryUtils;
import dlindustries.uk.daduncs.utils.KeyUtils;
import dlindustries.uk.daduncs.utils.TimerUtils;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

import java.util.Random;

public final class ElytraSwapV2 extends Module implements TickListener, MouseUpdateListener {

	private enum State {
		IDLE,
		AWAIT_OPEN_DELAY,
		SWAP_ELYTRA,
		AWAIT_INTER_CLICK,
		SWAP_FIREWORK
	}

	private final number elytraSlot = new number(EncryptedString.of("Elytra Hotbar Slot"), 1, 9, 1, 1);
	private final number fireworkSlot = new number(EncryptedString.of("Firework Hotbar Slot"), 1, 9, 2, 1);
	private final bind swapKey = new bind(EncryptedString.of("Swap Key"), -1, false);
	private final bool switchElytra = new bool(EncryptedString.of("Switch to Elytra after close"), false);
	private final bool putOn = new bool(EncryptedString.of("Put On the Elytra"), false)
			.setVisibility(switchElytra::getValue);

	private final TimerUtils timer = new TimerUtils();
	private final Random random = new Random();

	private State state = State.IDLE;
	private long waitMs;
	private boolean wanted;
	private boolean swapped;
	private boolean keyWasDown;
	private boolean switchPending;
	private int elytraFrom = -1;
	private int fireworkFrom = -1;

	public ElytraSwapV2() {
		super(EncryptedString.of("ElytraSwapV2"),
				EncryptedString.of("Helps you swap your elytra and sh"),
				-1,
				Category.MACE);
		addSettings(elytraSlot, fireworkSlot, swapKey, switchElytra, putOn);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		eventManager.add(MouseUpdateListener.class, this);
		wanted = false;
		swapped = false;
		keyWasDown = false;
		switchPending = false;
		elytraFrom = -1;
		fireworkFrom = -1;
		resetState();
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		eventManager.remove(MouseUpdateListener.class, this);
		resetState();
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.world == null) {
			resetState();
			return;
		}

		boolean keyDown = KeyUtils.isKeyPressed(swapKey.getKey());
		if (keyDown && !keyWasDown && state == State.IDLE) {
			wanted = !wanted;
		}
		keyWasDown = keyDown;

		if (switchPending && mc.currentScreen == null) {
			switchToElytra();
		}

		process();
	}

	@Override
	public void onMouseUpdate() {
		process();
	}

	private void process() {
		if (mc.player == null || mc.interactionManager == null) return;

		if (!(mc.currentScreen instanceof InventoryScreen screen)) {
			if (state != State.IDLE) {
				resetState();
			}
			return;
		}

		if (state == State.IDLE) {
			if (wanted == swapped) return;
			state = State.AWAIT_OPEN_DELAY;
			waitMs = randomDelay();
			timer.reset();
			return;
		}

		if (mc.player.isSprinting()) {
			mc.player.setSprinting(false);
		}

		if (state == State.AWAIT_OPEN_DELAY) {
			if (!timer.hasReached(waitMs)) return;
			state = State.SWAP_ELYTRA;
		}

		if (state == State.SWAP_ELYTRA) {
			if (AntibanbyDaduncs.isActionSent() || AntibanbyDaduncs.isSlotPending()) return;

			elytraFrom = swap(screen, Items.ELYTRA, elytraSlot.getValueInt() - 1, elytraFrom);
			waitMs = randomDelay();
			timer.reset();
			state = State.AWAIT_INTER_CLICK;
			return;
		}

		if (state == State.AWAIT_INTER_CLICK) {
			if (!timer.hasReached(waitMs)) return;
			state = State.SWAP_FIREWORK;
		}

		if (state == State.SWAP_FIREWORK) {
			if (AntibanbyDaduncs.isActionSent() || AntibanbyDaduncs.isSlotPending()) return;

			fireworkFrom = swap(screen, Items.FIREWORK_ROCKET, fireworkSlot.getValueInt() - 1, fireworkFrom);
			swapped = wanted;
			switchPending = swapped && switchElytra.getValue();
			state = State.IDLE;
		}
	}

	private void switchToElytra() {
		if (AntibanbyDaduncs.isActionSent() || AntibanbyDaduncs.isSlotPending()) return;
		switchPending = false;

		int slot = elytraSlot.getValueInt() - 1;
		if (!mc.player.getInventory().getStack(slot).isOf(Items.ELYTRA)) return;

		InventoryUtils.setInvSlot(slot);
		if (putOn.getValue()) {
			mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
			InventoryUtils.setInvSlot(fireworkSlot.getValueInt() - 1);
		}
	}

	private int swap(InventoryScreen screen, Item item, int hotbar, int from) {
		int syncId = screen.getScreenHandler().syncId;

		if (!wanted) {
			if (from != -1) {
				mc.interactionManager.clickSlot(syncId, from, hotbar, SlotActionType.SWAP, mc.player);
			}
			return -1;
		}

		if (from != -1) return from;

		int slot = findSlot(item, hotbar);
		if (slot != -1) {
			mc.interactionManager.clickSlot(syncId, slot, hotbar, SlotActionType.SWAP, mc.player);
		}
		return slot;
	}

	private int findSlot(Item item, int hotbar) {
		PlayerInventory inv = mc.player.getInventory();
		if (inv.getStack(hotbar).isOf(item)) return -1;

		for (int i = 9; i < 36; i++) {
			if (inv.getStack(i).isOf(item)) return i;
		}

		for (int i = 0; i < 9; i++) {
			if (inv.getStack(i).isOf(item)) return 36 + i;
		}

		return -1;
	}

	private long randomDelay() {
		long jitter = Math.round(random.nextGaussian() * 10.0);

		if (ClickGUI.randomisedTiming.getValue()) {
			jitter += (long) ((random.nextDouble() * 2.0 - 1.0) * (ClickGUI.VARIANCE * 0.3));
		}

		return Math.max(20L, 35L + jitter);
	}

	private void resetState() {
		state = State.IDLE;
		waitMs = 0L;
		timer.reset();
	}
}
