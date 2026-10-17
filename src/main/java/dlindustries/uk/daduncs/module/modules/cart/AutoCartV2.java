package dlindustries.uk.daduncs.module.modules.cart;

import dlindustries.uk.daduncs.event.events.PacketSendListener;
import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.InventoryUtils;
import dlindustries.uk.daduncs.utils.TimerUtils;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class AutoCartV2 extends Module implements TickListener, PacketSendListener {

	private final bool fromRail = new bool(EncryptedString.of("Place From Rail"), true);
	private final bool fromCart = new bool(EncryptedString.of("Place From Cart"), false);
	private final number delay = new number(EncryptedString.of("Switch Delay"), 0, 500, 50, 5);
	private final bool switchSlot = new bool(EncryptedString.of("Switch Slot"), false);
	private final number slot = new number(EncryptedString.of("Slot"), 1, 9, 1, 1).setVisibility(switchSlot::getValue);

	private final TimerUtils timer = new TimerUtils();

	private BlockPos rail;
	private BlockPos ground;
	private boolean holding;
	private boolean holdingOff;
	private boolean placed;
	private int ticks;

	public AutoCartV2() {
		super(EncryptedString.of("AutoCartV2"), EncryptedString.of("Places a cart on the rail you place."), -1, Category.CART);
		addSettings(fromRail, fromCart, delay, switchSlot, slot);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		eventManager.add(PacketSendListener.class, this);
		super.onEnable();
		rail = null;
		ground = null;
		placed = false;
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		eventManager.remove(PacketSendListener.class, this);
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.world == null)
			return;

		holding = isRail(mc.player.getMainHandStack().getItem());
		holdingOff = isRail(mc.player.getOffHandStack().getItem());

		if (placed) {
			if (!timer.hasReached(delay.getValue()) || AntibanbyDaduncs.isActionSent()) return;
			InventoryUtils.setInvSlot(slot.getValueInt() - 1);
			placed = false;
			return;
		}

		if (rail == null && ground == null)
			return;

		if (++ticks > 20 || mc.currentScreen != null) {
			rail = null;
			ground = null;
			return;
		}

		if (ground != null) {
			placeRail();
			return;
		}

		if (!(mc.world.getBlockState(rail).getBlock() instanceof AbstractRailBlock)) return;
		if (!timer.hasReached(delay.getValue()) || AntibanbyDaduncs.isActionSent() || AntibanbyDaduncs.isSlotPending())
			return;

		boolean offhand = mc.player.getOffHandStack().isOf(Items.TNT_MINECART);
		int cart = find(Items.TNT_MINECART);
		if ((cart == -1 && !offhand) || carted()) {
			rail = null;
			return;
		}

		if (!offhand) InventoryUtils.setInvSlot(cart);
		use(offhand ? Hand.OFF_HAND : Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofBottomCenter(rail).add(0, 0.1, 0), Direction.UP, rail, false));

		placed = switchSlot.getValue();
		rail = null;
		timer.reset();
	}

	private void placeRail() {
		if (!timer.hasReached(delay.getValue()) || AntibanbyDaduncs.isActionSent() || AntibanbyDaduncs.isSlotPending())
			return;

		int railSlot = -1;
		for (int i = 0; i < 9; i++) {
			if (isRail(mc.player.getInventory().getStack(i).getItem())) railSlot = i;
		}

		boolean offhand = isRail(mc.player.getOffHandStack().getItem());
		if ((railSlot == -1 && !offhand) || !mc.world.getBlockState(ground).isReplaceable()) {
			ground = null;
			return;
		}

		if (!offhand) InventoryUtils.setInvSlot(railSlot);
		use(offhand ? Hand.OFF_HAND : Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(ground.down()).add(0, 0.5, 0), Direction.UP, ground.down(), false));

		rail = ground;
		ground = null;
		ticks = 0;
		timer.reset();
	}

	@Override
	public void onPacketSend(PacketSendEvent event) {
		if (!(event.packet instanceof PlayerInteractBlockC2SPacket packet) || mc.player == null || mc.world == null)
			return;

		BlockHitResult hit = packet.getBlockHitResult();
		BlockPos pos = hit.getBlockPos();
		boolean railHand = packet.getHand() == Hand.MAIN_HAND ? holding : holdingOff;

		if (fromRail.getValue() && railHand) {
			rail = mc.world.getBlockState(pos).isReplaceable() ? pos : pos.offset(hit.getSide());
			ticks = 0;
			timer.reset();
			return;
		}

		if (!fromCart.getValue() || ground != null || rail != null) return;
		if (!mc.player.getStackInHand(packet.getHand()).isOf(Items.TNT_MINECART)) return;
		if (hit.getSide() != Direction.UP || mc.world.getBlockState(pos).getBlock() instanceof AbstractRailBlock) return;

		ground = pos.up();
		ticks = 0;
		timer.reset();
	}

	private void use(Hand hand, BlockHitResult hit) {
		if (mc.interactionManager.interactBlock(mc.player, hand, hit).isAccepted())
			mc.player.swingHand(hand);
	}

	private boolean carted() {
		return !mc.world.getEntitiesByClass(TntMinecartEntity.class, new Box(rail), cart -> true).isEmpty();
	}

	private int find(Item item) {
		for (int i = 0; i < 9; i++) {
			if (mc.player.getInventory().getStack(i).isOf(item))
				return i;
		}
		return -1;
	}

	private boolean isRail(Item item) {
		return item == Items.RAIL || item == Items.POWERED_RAIL || item == Items.DETECTOR_RAIL || item == Items.ACTIVATOR_RAIL;
	}
}
