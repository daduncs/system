package dlindustries.uk.daduncs.module.modules.render;

import dlindustries.uk.daduncs.event.events.GameRenderListener;
import dlindustries.uk.daduncs.event.events.PacketSendListener;
import dlindustries.uk.daduncs.font.Fonts;
import dlindustries.uk.daduncs.font.WorldFont;
import dlindustries.uk.daduncs.gui.Theme;
import dlindustries.uk.daduncs.mixin.PlayerInteractEntityC2SPacketAccessor;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.colour;
import dlindustries.uk.daduncs.module.setting.mode;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.render.BloomBurst;
import dlindustries.uk.daduncs.utils.EncryptedString;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class HitFx extends Module implements PacketSendListener, GameRenderListener {

	public enum Mode {
		Particles, SuperHero
	}

	public enum Speed {
		Fast, Slow
	}

	private static final String[] WORDS = {"BOOM!", "POW!", "ZAP!", "KABOOM!", "WHAM!", "SMASH!", "BAM!", "CRASH!"};
	private static final int[] COLORS = {0xFFFF3B30, 0xFFFFCC00, 0xFF34C759, 0xFF007AFF, 0xFFAF52DE, 0xFFFF9500, 0xFFFF2D55};
	private static final float OUTLINE = 6f;

	private final mode<Mode> mode = new mode<>(EncryptedString.of("Mode"), Mode.Particles, Mode.class);
	private final number count = new number(EncryptedString.of("Particle Count"), 4, 60, 14, 1).setVisibility(this::particles);
	private final mode<Speed> speed = new mode<>(EncryptedString.of("Particle Mode"), Speed.Fast, Speed.class).setVisibility(this::particles);
	private final number lifetime = new number(EncryptedString.of("Particle Lifetime"), 0.5, 6, 3, 0.1).setVisibility(this::particles);
	private final colour particleColor = new colour(EncryptedString.of("Particle Color"), 0xFF9B6DFF).setVisibility(this::particles);
	private final number textSize = new number(EncryptedString.of("Text Size"), 0.15, 1.5, 0.45, 0.05).setVisibility(this::hero);
	private final number textLifetime = new number(EncryptedString.of("Text Lifetime"), 0.3, 3, 1, 0.1).setVisibility(this::hero);

	private final BloomBurst bloom = new BloomBurst();
	private final List<Pop> pops = new ArrayList<>();
	private final Random random = new Random();

	public HitFx() {
		super(EncryptedString.of("HitFx"),
				EncryptedString.of("Shows effects when u hit someone"),
				-1,
				Category.RENDER);
		addSettings(mode, count, speed, lifetime, particleColor, textSize, textLifetime);
	}

	@Override
	public void onEnable() {
		eventManager.add(PacketSendListener.class, this);
		eventManager.add(GameRenderListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(PacketSendListener.class, this);
		eventManager.remove(GameRenderListener.class, this);
		bloom.clear();
		pops.clear();
		super.onDisable();
	}

	private boolean particles() {
		return mode.isMode(Mode.Particles);
	}

	private boolean hero() {
		return mode.isMode(Mode.SuperHero);
	}

	@Override
	public void onPacketSend(PacketSendEvent event) {
		if (mc.world == null || !(event.packet instanceof PlayerInteractEntityC2SPacket packet)) return;
		if (!isAttack(packet)) return;

		Entity entity = mc.world.getEntityById(((PlayerInteractEntityC2SPacketAccessor) packet).getEntityId());
		if (!(entity instanceof LivingEntity target)) return;

		if (particles()) bloom.burst(target, count.getValueInt(), speed.isMode(Speed.Slow) ? 4.0 : 8.0, lifetime.getValue());
		else pop(target);
	}

	private static boolean isAttack(PlayerInteractEntityC2SPacket packet) {
		boolean[] attack = {false};

		packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
			@Override
			public void interact(Hand hand) {
			}

			@Override
			public void interactAt(Hand hand, Vec3d pos) {
			}

			@Override
			public void attack() {
				attack[0] = true;
			}
		});

		return attack[0];
	}

	private void pop(LivingEntity target) {
		Vec3d spot = new Vec3d(target.getX(), target.getY() + target.getHeight() * 0.5 + (random.nextDouble() - 0.5) * 0.2, target.getZ());
		String word = WORDS[random.nextInt(WORDS.length)];
		int color = COLORS[random.nextInt(COLORS.length)];
		float tilt = (random.nextFloat() - 0.5f) * 16f;

		pops.add(new Pop(spot, word, color, tilt, System.currentTimeMillis(), (long) (textLifetime.getValue() * 1000.0)));
	}

	@Override
	public void onGameRender(GameRenderEvent event) {
		if (mc.player == null || mc.world == null) return;

		if (bloom.isEmpty() && pops.isEmpty()) return;

		long now = System.currentTimeMillis();
		pops.removeIf(pop -> now - pop.born() >= pop.life());

		Camera camera = mc.gameRenderer.getCamera();
		if (camera == null) return;

		event.matrices.push();
		event.matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
		event.matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180f));

		bloom.draw(event.matrices, camera, speed.isMode(Speed.Slow) ? 0.1f : 0.95f, particleColor.getValue());
		drawPops(event.matrices, camera, now);

		event.matrices.pop();
	}

	private void drawPops(MatrixStack matrices, Camera camera, long now) {
		Vec3d eye = camera.getCameraPos();
		float height = WorldFont.height(Fonts.SUPERHERO);

		for (Pop pop : pops) {
			float t = MathHelper.clamp((now - pop.born()) / (float) pop.life(), 0f, 1f);
			float size = grow(t) * textSize.getValueFloat();
			if (size <= 0.001f) continue;

			Vec3d toCamera = eye.subtract(pop.spot());
			Vec3d at = toCamera.lengthSquared() < 1e-6 ? pop.spot() : pop.spot().add(toCamera.normalize().multiply(0.6));
			float scale = size / height;
			float gone = t < 0.75f ? 0f : (t - 0.75f) / 0.25f;
			float alpha = Math.min(MathHelper.clamp(t / 0.05f, 0f, 1f), 1f - gone * gone);

			matrices.push();
			matrices.translate(at.x - eye.x, at.y - eye.y, at.z - eye.z);
			matrices.multiply(camera.getRotation());
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(pop.tilt()));
			matrices.scale(scale, scale, scale);
			matrices.translate(-WorldFont.width(Fonts.SUPERHERO, pop.word()) / 2f, -height / 2f, 0f);

			WorldFont.outlined(Fonts.SUPERHERO, matrices, pop.word(), Theme.fade(pop.color(), alpha), OUTLINE);

			matrices.pop();
		}
	}

	private static float grow(float t) {
		if (t < 0.18f) return smooth(t / 0.18f) * 1.18f;
		if (t < 0.35f) return 1.18f - smooth((t - 0.18f) / 0.17f) * 0.18f;
		if (t < 0.82f) return 1f;
		return 1f - smooth((t - 0.82f) / 0.18f);
	}

	private static float smooth(float k) {
		return k * k * (3f - 2f * k);
	}

	private record Pop(Vec3d spot, String word, int color, float tilt, long born, long life) {
	}
}
