package dlindustries.uk.daduncs.module.modules.render;

import dlindustries.uk.daduncs.event.events.GameRenderListener;
import dlindustries.uk.daduncs.event.events.PacketReceiveListener;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.colour;
import dlindustries.uk.daduncs.module.setting.mode;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.render.BloomBurst;
import dlindustries.uk.daduncs.render.esp.OutlineRenderer;
import dlindustries.uk.daduncs.utils.EncryptedString;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class PopFx extends Module implements PacketReceiveListener, GameRenderListener {

	public enum Fill {
		SOLID, FROSTED, FLUID
	}

	private static final float RISE = 1.1f;
	private static final float DRIFT = 0.3f;
	private static final float SCALE = 1.2f;

	private final colour color = new colour(EncryptedString.of("Color"), 0xFF9B6DFF);
	private final mode<Fill> fillMode = new mode<>(EncryptedString.of("Fill Mode"), Fill.SOLID, Fill.class);
	private final number fill = new number(EncryptedString.of("Fill"), 0, 1, 0.3, 0.05);
	private final number width = new number(EncryptedString.of("Width"), 1, 6, 6, 0.5);
	private final number glow = new number(EncryptedString.of("Glow"), 0, 24, 12, 1);
	private final number lifetime = new number(EncryptedString.of("Lifetime"), 0.5, 3, 1.4, 0.1);
	private final number size = new number(EncryptedString.of("Size"), 0.5, 2, 2, 0.1);
	private final bool particles = new bool(EncryptedString.of("Particles"), true);
	private final number particleCount = new number(EncryptedString.of("Particle Count"), 4, 60, 20, 1).setVisibility(particles::getValue);
	private final number particleLifetime = new number(EncryptedString.of("Particle Lifetime"), 0.5, 6, 2, 0.1).setVisibility(particles::getValue);
	private final colour particleColor = new colour(EncryptedString.of("Particle Color"), 0xFF9B6DFF).setVisibility(particles::getValue);

	private final OutlineRenderer renderer = new OutlineRenderer();
	private final ItemRenderState totem = new ItemRenderState();
	private final ItemStack stack = new ItemStack(Items.TOTEM_OF_UNDYING);
	private final BloomBurst bloom = new BloomBurst();
	private final List<Pop> pops = new ArrayList<>();
	private final Random random = new Random();

	private boolean active;

	public PopFx() {
		super(EncryptedString.of("PopFx"),
				EncryptedString.of("self explanatory."),
				-1,
				Category.RENDER);
		addSettings(color, fillMode, fill, width, glow, lifetime, size, particles, particleCount, particleLifetime, particleColor);
	}

	@Override
	public void onEnable() {
		eventManager.add(PacketReceiveListener.class, this);
		eventManager.add(GameRenderListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(PacketReceiveListener.class, this);
		eventManager.remove(GameRenderListener.class, this);
		pops.clear();
		bloom.clear();
		active = false;
		super.onDisable();
	}

	@Override
	public void onPacketReceive(PacketReceiveEvent event) {
		if (!(event.packet instanceof EntityStatusS2CPacket packet) || packet.getStatus() != 35) return;

		mc.execute(() -> spawn(packet));
	}

	private void spawn(EntityStatusS2CPacket packet) {
		if (mc.world == null || mc.player == null) return;

		Entity entity = packet.getEntity(mc.world);
		if (!(entity instanceof PlayerEntity player) || player == mc.player) return;

		double angle = random.nextDouble() * Math.PI * 2.0;
		Vec3d spot = new Vec3d(player.getX(), player.getY() + player.getHeight() * 0.6, player.getZ());
		Vec3d drift = new Vec3d(Math.cos(angle) * DRIFT, 0, Math.sin(angle) * DRIFT);

		pops.add(new Pop(spot, drift, System.currentTimeMillis()));

		if (particles.getValue())
			bloom.burst(player, particleCount.getValueInt(), 8.0, particleLifetime.getValue());
	}

	@Override
	public void onGameRender(GameRenderEvent event) {
		if (bloom.isEmpty()) return;

		Camera camera = mc.gameRenderer.getCamera();
		if (camera == null) return;

		event.matrices.push();
		event.matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
		event.matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180f));

		bloom.draw(event.matrices, camera, 0.95f, particleColor.getValue());

		event.matrices.pop();
	}

	public void beginFrame() {
		active = !pops.isEmpty();
		if (active) renderer.beginFrame();
	}

	public void submit(WorldRenderState world) {
		if (!active || mc.world == null) return;

		long now = System.currentTimeMillis();
		long duration = (long) (lifetime.getValue() * 1000.0);
		pops.removeIf(pop -> now - pop.born() >= duration);

		mc.getItemModelManager().clearAndUpdate(totem, stack, ItemDisplayContext.FIXED, mc.world, null, 0);
		float facing = 180f - mc.gameRenderer.getCamera().getYaw();

		for (Pop pop : pops) {
			float t = (now - pop.born()) / (float) duration;
			float lift = easeOut(t);
			float spin = 360f * easeOut(Math.min(1f, t / 0.7f));
			float scale = grow(t) * size.getValueFloat() * SCALE;

			Vec3d spot = pop.spot().add(pop.drift().multiply(lift)).add(0, lift * RISE, 0);
			renderer.addItem(totem, world, spot, facing + spin, scale, color.getValue());
		}

		renderer.drawShapes();
	}

	public void composite() {
		if (!active) return;

		renderer.composite(width.getValueFloat(), glow.getValueFloat(), fill.getValueFloat(), fillMode.getModeIndex());
	}

	private static float easeOut(float t) {
		float left = 1f - t;
		return 1f - left * left * left;
	}

	private static float grow(float t) {
		if (t < 0.18f) return smooth(t / 0.18f) * 1.15f;
		if (t < 0.32f) return 1.15f - smooth((t - 0.18f) / 0.14f) * 0.15f;
		if (t < 0.75f) return 1f;
		return 1f - smooth((t - 0.75f) / 0.25f);
	}

	private static float smooth(float k) {
		return k * k * (3f - 2f * k);
	}

	private record Pop(Vec3d spot, Vec3d drift, long born) {
	}
}
