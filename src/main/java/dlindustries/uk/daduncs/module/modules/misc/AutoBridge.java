package dlindustries.uk.daduncs.module.modules.misc;

import dlindustries.uk.daduncs.event.events.HudListener;
import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.mixin.KeyBindingAccessor;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.mode;
import dlindustries.uk.daduncs.module.setting.range;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.InventoryUtils;
import dlindustries.uk.daduncs.utils.KeyUtils;
import dlindustries.uk.daduncs.utils.RotationUtils;
import dlindustries.uk.daduncs.utils.WorldUtils;
import dlindustries.uk.daduncs.utils.rotation.Rotation;
import dlindustries.uk.daduncs.utils.rotation.SilentRot;
import net.minecraft.block.FallingBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Random;

public final class AutoBridge extends Module implements TickListener, HudListener {

	public enum Mode {
		Manual, Auto
	}

	private static final Direction[] SIDES = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

	private final mode<Mode> mode = new mode<>(EncryptedString.of("Mode"), Mode.Manual, Mode.class);
	private final range silentSpeed = new range(EncryptedString.of("Silent Speed"), 0, 600, 1, 300, 400).setVisibility(() -> mode.isMode(Mode.Auto));

	private final double[] gcd = new double[2];
	private final Random random = new Random();

	private boolean sneaking;
	private boolean rotating;
	private float yaw;
	private float pitch;
	private float wantYaw;
	private float wantPitch;
	private boolean aiming;
	private long frame;
	private int idle;
	private int wait;
	private int prevSlot = -1;

	public AutoBridge() {
		super(EncryptedString.of("AutoBridge"), EncryptedString.of("Helps you bridge."), -1, Category.MISC);
		setTag(EncryptedString.of("Bannable"));
		addSettings(mode, silentSpeed);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		eventManager.add(HudListener.class, this);
		super.onEnable();
		rotating = false;
		aiming = false;
		frame = 0;
		prevSlot = -1;
		wait = 0;
		idle = 0;
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		eventManager.remove(HudListener.class, this);
		sneak(false);
		SilentRot.stop(this);
		rotating = false;
		aiming = false;

		if (prevSlot != -1 && mc.player != null) InventoryUtils.setInvSlot(prevSlot);
		prevSlot = -1;
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.world == null || mc.currentScreen != null) {
			sneak(false);
			return;
		}

		if (mode.isMode(Mode.Manual)) manual();
		else auto();
	}

	private void manual() {
		boolean bridging = mc.options.useKey.isPressed() && mc.player.isOnGround()
				&& mc.player.getMainHandStack().getItem() instanceof BlockItem;

		sneak(bridging && overAir(2));
	}

	@Override
	public void onRenderHud(HudEvent event) {
		long now = java.lang.System.nanoTime();
		float delta = frame == 0 ? 1f / 60f : Math.min(0.1f, (now - frame) / 1_000_000_000f);
		frame = now;

		if (!rotating || mc.player == null || !mode.isMode(Mode.Auto)) return;

		float toYaw = aiming ? wantYaw : mc.player.getYaw();
		float toPitch = aiming ? wantPitch : mc.player.getPitch();

		float dy = MathHelper.wrapDegrees(toYaw - yaw);
		float dp = toPitch - pitch;
		float ease = 1f - (float) Math.exp(-delta * 16f);
		float cap = silentSpeed.getRandomValueFloat() * 2.5f * delta;

		Rotation smooth = RotationUtils.applyGCD(yaw, pitch,
				yaw + MathHelper.clamp(dy * ease, -cap, cap),
				pitch + MathHelper.clamp(dp * ease, -cap * 0.7f, cap * 0.7f), gcd, false);

		yaw = (float) smooth.yaw();
		pitch = MathHelper.clamp((float) smooth.pitch(), -90f, 90f);
		SilentRot.set(yaw, pitch, this);

		if (!aiming && Math.abs(dy) < 1.5f && Math.abs(dp) < 1.5f) {
			SilentRot.stop(this);
			rotating = false;
		}
	}

	private void auto() {
		if (wait > 0) wait--;

		int block = blockSlot();
		BlockHitResult face = block == -1 ? null : target();

		sneak(block != -1 && mc.player.isOnGround() && overAir(2.5));

		if (face == null) {
			if (++idle > 6) {
				aiming = false;
				if (!rotating) swapBack();
			}
			return;
		}

		idle = 0;
		aim(RotationUtils.getDirection(mc.player.getEyePos(), face.getPos()));

		if (mc.player.getInventory().getSelectedSlot() != block) {
			if (prevSlot == -1) prevSlot = mc.player.getInventory().getSelectedSlot();
			if (!AntibanbyDaduncs.isActionSent()) InventoryUtils.setInvSlot(block);
			return;
		}

		if (wait > 0 || AntibanbyDaduncs.isActionSent() || AntibanbyDaduncs.isSlotPending()) return;

		BlockHitResult ray = ray();
		if (ray == null || !ray.getBlockPos().equals(face.getBlockPos()) || ray.getSide() != face.getSide()) return;

		if (mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, ray).isAccepted()) {
			mc.player.swingHand(Hand.MAIN_HAND);
			wait = random.nextInt(2);
		}
	}

	private BlockHitResult target() {
		if (mc.player.fallDistance > 1f) return null;

		Vec3d vel = mc.player.getVelocity();
		double y = mc.player.isOnGround() ? mc.player.getY() - 0.5 : mc.player.getY() - 1;

		for (int ahead = 0; ahead <= 8; ahead += 2) {
			if (ahead > 0 && Math.hypot(vel.x, vel.z) < 0.02) break;

			BlockPos spot = BlockPos.ofFloored(mc.player.getX() + vel.x * ahead, y, mc.player.getZ() + vel.z * ahead);
			if (!mc.world.getBlockState(spot).isReplaceable()) continue;
			if (new Box(spot).intersects(mc.player.getBoundingBox())) continue;

			BlockHitResult face = support(spot);
			if (face == null) face = connect(spot);
			if (face != null) return face;
		}

		return null;
	}

	private BlockHitResult connect(BlockPos spot) {
		Vec3d eye = mc.player.getEyePos();
		BlockHitResult best = null;

		for (Direction side : Direction.Type.HORIZONTAL) {
			BlockPos next = spot.offset(side);
			if (!mc.world.getBlockState(next).isReplaceable()) continue;
			if (new Box(next).intersects(mc.player.getBoundingBox())) continue;

			BlockHitResult face = support(next);
			if (face != null && (best == null || eye.distanceTo(face.getPos()) < eye.distanceTo(best.getPos()))) best = face;
		}

		return best;
	}

	private BlockHitResult support(BlockPos spot) {
		Vec3d eye = mc.player.getEyePos();
		BlockHitResult best = null;
		double closest = mc.player.getBlockInteractionRange();

		for (Direction side : SIDES) {
			BlockPos against = spot.offset(side);
			if (mc.world.getBlockState(against).isReplaceable()) continue;

			Direction face = side.getOpposite();
			Vec3d point = new Vec3d(
					face.getAxis() == Direction.Axis.X ? against.getX() + 0.5 + face.getOffsetX() * 0.5 : MathHelper.clamp(eye.x, against.getX() + 0.15, against.getX() + 0.85),
					face.getAxis() == Direction.Axis.Y ? against.getY() + 0.5 + face.getOffsetY() * 0.5 : MathHelper.clamp(eye.y, against.getY() + 0.15, against.getY() + 0.85),
					face.getAxis() == Direction.Axis.Z ? against.getZ() + 0.5 + face.getOffsetZ() * 0.5 : MathHelper.clamp(eye.z, against.getZ() + 0.15, against.getZ() + 0.85));

			if (eye.subtract(point).dotProduct(Vec3d.of(face.getVector())) <= 0) continue;

			double distance = eye.distanceTo(point);
			if (distance > closest) continue;

			closest = distance;
			best = new BlockHitResult(point, face, against, false);
		}

		return best;
	}

	private BlockHitResult ray() {
		Vec3d eye = mc.player.getEyePos();
		Vec3d end = eye.add(WorldUtils.getPlayerLookVec(yaw, pitch).multiply(mc.player.getBlockInteractionRange()));
		BlockHitResult hit = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));

		return hit.getType() == HitResult.Type.BLOCK ? hit : null;
	}

	private void aim(Rotation want) {
		if (!rotating) {
			yaw = mc.player.getYaw();
			pitch = mc.player.getPitch();
			gcd[0] = 0;
			gcd[1] = 0;
			rotating = true;
		}

		wantYaw = (float) want.yaw();
		wantPitch = (float) want.pitch();
		aiming = true;
	}

	private void swapBack() {
		if (prevSlot == -1 || AntibanbyDaduncs.isActionSent()) return;

		InventoryUtils.setInvSlot(prevSlot);
		prevSlot = -1;
	}

	private boolean overAir(double ahead) {
		Vec3d vel = mc.player.getVelocity();
		BlockPos under = BlockPos.ofFloored(mc.player.getX() + vel.x * ahead, mc.player.getY() - 0.5, mc.player.getZ() + vel.z * ahead);

		return mc.world.getBlockState(under).isReplaceable();
	}

	private void sneak(boolean on) {
		if (on) {
			mc.options.sneakKey.setPressed(true);
			sneaking = true;
		} else if (sneaking) {
			mc.options.sneakKey.setPressed(KeyUtils.isKeyPressed(((KeyBindingAccessor) mc.options.sneakKey).getBoundKey().getCode()));
			sneaking = false;
		}
	}

	private int blockSlot() {
		ItemStack held = mc.player.getMainHandStack();
		if (solid(held)) return mc.player.getInventory().getSelectedSlot();

		for (int i = 0; i < 9; i++)
			if (solid(mc.player.getInventory().getStack(i))) return i;

		return -1;
	}

	private boolean solid(ItemStack stack) {
		if (!(stack.getItem() instanceof BlockItem item)) return false;
		if (item.getBlock() instanceof FallingBlock) return false;

		return item.getBlock().getDefaultState().isFullCube(mc.world, BlockPos.ORIGIN);
	}
}
