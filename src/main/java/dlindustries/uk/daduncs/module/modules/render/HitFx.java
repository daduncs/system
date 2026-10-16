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
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.RenderUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

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

	private static final Identifier BLOOM = Identifier.of("system", "textures/bloom.png");
	private static final String[] WORDS = {"BOOM!", "POW!", "ZAP!", "KABOOM!", "WHAM!", "SMASH!", "BAM!", "CRASH!"};
	private static final int[] COLORS = {0xFFFF3B30, 0xFFFFCC00, 0xFF34C759, 0xFF007AFF, 0xFFAF52DE, 0xFFFF9500, 0xFFFF2D55};
	private static final float GRAVITY = 5f;
	private static final float SIZE = 0.2f;
	private static final float OUTLINE = 6f;

	private final mode<Mode> mode = new mode<>(EncryptedString.of("Mode"), Mode.Particles, Mode.class);
	private final number count = new number(EncryptedString.of("Particle Count"), 4, 60, 14, 1).setVisibility(this::particles);
	private final mode<Speed> speed = new mode<>(EncryptedString.of("Particle Mode"), Speed.Fast, Speed.class).setVisibility(this::particles);
	private final number lifetime = new number(EncryptedString.of("Particle Lifetime"), 0.5, 6, 3, 0.1).setVisibility(this::particles);
	private final colour particleColor = new colour(EncryptedString.of("Particle Color"), 0xFF9B6DFF).setVisibility(this::particles);
	private final number textSize = new number(EncryptedString.of("Text Size"), 0.15, 1.5, 0.45, 0.05).setVisibility(this::hero);
	private final number textLifetime = new number(EncryptedString.of("Text Lifetime"), 0.3, 3, 1, 0.1).setVisibility(this::hero);

	private final List<Particle> particles = new ArrayList<>();
	private final List<Pop> pops = new ArrayList<>();
	private final Random random = new Random();

	private long lastFrame;

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
		particles.clear();
		pops.clear();
		lastFrame = 0L;
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

		if (particles()) burst(target);
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

	private void burst(LivingEntity target) {
		double force = speed.isMode(Speed.Slow) ? 4.0 : 8.0;
		double centerY = target.getY() + target.getHeight() * 0.55;

		for (int i = 0; i < count.getValueInt(); i++) {
			double yaw = random.nextDouble() * Math.PI * 2.0;
			double pitch = (random.nextDouble() - 0.5) * 0.1;
			double push = force * (0.65 + random.nextDouble() * 0.85);

			Particle particle = new Particle();
			particle.x = target.getX() + (random.nextDouble() - 0.5) * target.getWidth() * 0.7;
			particle.y = centerY + (random.nextDouble() - 0.5) * target.getHeight() * 0.4;
			particle.z = target.getZ() + (random.nextDouble() - 0.5) * target.getWidth() * 0.7;
			particle.vx = Math.cos(yaw) * Math.cos(pitch) * push;
			particle.vy = Math.sin(pitch) * push;
			particle.vz = Math.sin(yaw) * Math.cos(pitch) * push;
			particle.size = SIZE * (0.7f + random.nextFloat() * 0.6f);
			particle.life = (long) (lifetime.getValue() * 1000.0 * (0.7 + random.nextDouble() * 0.5));
			particle.born = System.currentTimeMillis();

			particles.add(particle);
		}
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

		if (particles.isEmpty() && pops.isEmpty()) {
			lastFrame = 0L;
			return;
		}

		long now = System.currentTimeMillis();
		float dt = lastFrame == 0L ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;

		float bounce = speed.isMode(Speed.Slow) ? 0.1f : 0.95f;
		particles.removeIf(particle -> move(particle, dt, bounce, now));
		pops.removeIf(pop -> now - pop.born() >= pop.life());

		Camera camera = mc.gameRenderer.getCamera();
		if (camera == null) return;

		event.matrices.push();
		event.matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
		event.matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180f));

		drawBloom(event.matrices, camera, now);
		drawPops(event.matrices, camera, now);

		event.matrices.pop();
	}

	private boolean move(Particle particle, float dt, float bounce, long now) {
		if (now - particle.born >= particle.life) return true;

		if (!particle.settled) {
			particle.vy -= GRAVITY * dt;
			float drag = (float) Math.pow(0.92, dt * 30.0);
			particle.vx *= drag;
			particle.vz *= drag;
		}

		double x = particle.x + particle.vx * dt;
		double y = particle.y + particle.vy * dt;
		double z = particle.z + particle.vz * dt;

		if (particle.vy < 0) {
			BlockPos under = BlockPos.ofFloored(x, y - 0.02, z);
			BlockState state = mc.world.getBlockState(under);
			double top = under.getY() + 1.0;

			if (!state.isAir() && state.isSolidBlock(mc.world, under) && y <= top + 0.001) {
				y = top + 0.001;
				particle.vy = -particle.vy * bounce;
				particle.vx *= bounce * 0.85;
				particle.vz *= bounce * 0.85;
				particle.bounces++;

				if (Math.abs(particle.vy) < 0.4 && particle.bounces > 1) {
					particle.vy = 0;
					particle.settled = true;
				}
			}
		}

		particle.x = x;
		particle.y = y;
		particle.z = z;
		return false;
	}

	private void drawBloom(MatrixStack matrices, Camera camera, long now) {
		if (particles.isEmpty()) return;

		Vec3d eye = camera.getCameraPos();
		int tint = particleColor.getValue();

		RenderUtils.drawLayer(RenderLayers.entityTranslucentEmissive(BLOOM), buffer -> {
			for (Particle particle : particles) {
				float alpha = fade(particle, now);
				if (alpha <= 0.005f) continue;

				matrices.push();
				matrices.translate(particle.x - eye.x, particle.y - eye.y, particle.z - eye.z);
				matrices.multiply(camera.getRotation());

				Matrix4f matrix = matrices.peek().getPositionMatrix();
				float half = particle.size / 2f;
				int color = Theme.fade(tint, alpha);

				corner(buffer, matrix, -half, half, 0f, 0f, color);
				corner(buffer, matrix, -half, -half, 0f, 1f, color);
				corner(buffer, matrix, half, -half, 1f, 1f, color);
				corner(buffer, matrix, half, half, 1f, 0f, color);

				matrices.pop();
			}
		});
	}

	private static void corner(VertexConsumer buffer, Matrix4f matrix, float x, float y, float u, float v, int color) {
		buffer.vertex(matrix, x, y, 0f)
				.color(color)
				.texture(u, v)
				.overlay(OverlayTexture.DEFAULT_UV)
				.light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
				.normal(0f, 1f, 0f);
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

	private static float fade(Particle particle, long now) {
		float age = (now - particle.born) / (float) particle.life;
		if (age >= 1f) return 0f;

		float in = MathHelper.clamp(age / 0.08f, 0f, 1f);
		float out = MathHelper.clamp((1f - age) / 0.35f, 0f, 1f);
		return smooth(Math.min(in, out));
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

	private static final class Particle {
		double x, y, z;
		double vx, vy, vz;
		float size;
		long life;
		long born;
		int bounces;
		boolean settled;
	}

	private record Pop(Vec3d spot, String word, int color, float tilt, long born, long life) {
	}
}
