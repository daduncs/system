package dlindustries.uk.daduncs.render;

import dlindustries.uk.daduncs.gui.Theme;
import dlindustries.uk.daduncs.utils.RenderUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static dlindustries.uk.daduncs.System.mc;

public final class BloomBurst {

	private static final Identifier BLOOM = Identifier.of("system", "textures/bloom.png");
	private static final float GRAVITY = 5f;
	private static final float SIZE = 0.2f;

	private final List<Particle> particles = new ArrayList<>();
	private final Random random = new Random();

	private long lastFrame;

	public boolean isEmpty() {
		return particles.isEmpty();
	}

	public void clear() {
		particles.clear();
		lastFrame = 0L;
	}

	public void burst(LivingEntity target, int count, double force, double lifetime) {
		double centerY = target.getY() + target.getHeight() * 0.55;

		for (int i = 0; i < count; i++) {
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
			particle.life = (long) (lifetime * 1000.0 * (0.7 + random.nextDouble() * 0.5));
			particle.born = java.lang.System.currentTimeMillis();
			particle.shade = random.nextFloat();

			particles.add(particle);
		}
	}

	public void draw(MatrixStack matrices, Camera camera, float bounce, int from, int to) {
		if (particles.isEmpty()) {
			lastFrame = 0L;
			return;
		}

		long now = java.lang.System.currentTimeMillis();
		float dt = lastFrame == 0L ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;

		particles.removeIf(particle -> move(particle, dt, bounce, now));
		if (particles.isEmpty()) return;

		Vec3d eye = camera.getCameraPos();

		RenderUtils.drawLayer(RenderLayers.entityTranslucentEmissive(BLOOM), buffer -> {
			for (Particle particle : particles) {
				float alpha = fade(particle, now);
				if (alpha <= 0.005f) continue;

				matrices.push();
				matrices.translate(particle.x - eye.x, particle.y - eye.y, particle.z - eye.z);
				matrices.multiply(camera.getRotation());

				Matrix4f matrix = matrices.peek().getPositionMatrix();
				float half = particle.size / 2f;
				int color = Theme.fade(from == to ? from : Theme.flow(from, to, particle.shade), alpha);

				corner(buffer, matrix, -half, half, 0f, 0f, color);
				corner(buffer, matrix, -half, -half, 0f, 1f, color);
				corner(buffer, matrix, half, -half, 1f, 1f, color);
				corner(buffer, matrix, half, half, 1f, 0f, color);

				matrices.pop();
			}
		});
	}

	private boolean move(Particle particle, float dt, float bounce, long now) {
		if (now - particle.born >= particle.life || mc.world == null) return true;

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

	private static void corner(VertexConsumer buffer, Matrix4f matrix, float x, float y, float u, float v, int color) {
		buffer.vertex(matrix, x, y, 0f)
				.color(color)
				.texture(u, v)
				.overlay(OverlayTexture.DEFAULT_UV)
				.light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
				.normal(0f, 1f, 0f);
	}

	private static float fade(Particle particle, long now) {
		float age = (now - particle.born) / (float) particle.life;
		if (age >= 1f) return 0f;

		float in = MathHelper.clamp(age / 0.08f, 0f, 1f);
		float out = MathHelper.clamp((1f - age) / 0.35f, 0f, 1f);
		float k = Math.min(in, out);
		return k * k * (3f - 2f * k);
	}

	private static final class Particle {
		double x, y, z;
		double vx, vy, vz;
		float size;
		float shade;
		long life;
		long born;
		int bounces;
		boolean settled;
	}
}
