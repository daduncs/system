/*
 * This file is part of System Client (https://github.com/daduncs/system)
 * Website: https://download.daduncs.cloud/
 *
 * Copyright (c) 2024 - 2026 DLindustries and project contributors.
 *
 * System Client is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * System Client is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with System Client. If not, see <https://www.gnu.org/licenses/>.
 */

package dlindustries.uk.daduncs.module.modules.render;

import dlindustries.uk.daduncs.event.events.HudListener;
import dlindustries.uk.daduncs.gui.Anim;
import dlindustries.uk.daduncs.gui.HudEditor;
import dlindustries.uk.daduncs.gui.Theme;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.render.Render2D;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.ProjectionUtils;
import dlindustries.uk.daduncs.utils.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class TargetHud extends Module implements HudListener {

	private static final int MIN_WIDTH = 140;
	private static final int HEIGHT = 44;
	private static final int FACE = 30;
	private static final long GRACE = 400;
	private static final int HURT = 0xFFFF5A5A;
	private static final Identifier BLOOM = Identifier.of("system", "textures/bloom.png");
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private final bool tint = new bool(EncryptedString.of("Tint"), true)
			.setDescription(EncryptedString.of("Face goes a bit red when they get hit"));
	private final bool particles = new bool(EncryptedString.of("Particles"), true)
			.setDescription(EncryptedString.of("Little sparks fly off the face on hit"));
	private final bool outline = new bool(EncryptedString.of("Outline"), false);
	private final number size = new number(EncryptedString.of("Size"), 50, 200, 100, 5);
	private final bool healthGradient = new bool(EncryptedString.of("Health Gradient"), true);
	private final bool stick = new bool(EncryptedString.of("Stick To Player"), false);
	private final bool fading = new bool(EncryptedString.of("Fade"), false);
	private final number fadeSize = new number(EncryptedString.of("Fade Size"), 4, 40, 18, 1).setVisibility(fading::getValue);
	private final bool fadeGradient = new bool(EncryptedString.of("Fade Gradient"), true).setVisibility(fading::getValue);
	private final bool hideFriends = new bool(EncryptedString.of("Hide Friends"), true)
			.setDescription(EncryptedString.of("Doesn't show the hud for friends"));

	private final bool keepOpen = new bool(EncryptedString.of("Keep Open"), false)
			.setDescription(EncryptedString.of("Stays after the target is gone"));

	private final number timeout = new number(EncryptedString.of("Timeout"), 0, 10, 2, 0.5);

	private final Anim health = new Anim(0f, 5f);
	private final Anim show = new Anim(0f, 13f, 16f);
	private final List<Spark> sparks = new ArrayList<>();
	private final Random random = new Random();

	private float floatX;
	private float floatY;
	private boolean placed;
	private boolean arrived;
	private float pinX;
	private float pinY;
	private long seen;

	private LivingEntity target;
	private LivingEntity drawn;
	private LivingEntity fighting;
	private int lastSwing;
	private int lastHurt;

	public TargetHud() {
		super(EncryptedString.of("Target Hud"),
				EncryptedString.of("Shows who you are fighting"),
				-1,
				Category.RENDER);
		addSettings(tint, particles, keepOpen, timeout, outline, size, healthGradient, stick, hideFriends, fading, fadeSize, fadeGradient);
		HudEditor.register(this);
	}

	@Override
	public void onEnable() {
		eventManager.add(HudListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(HudListener.class, this);
		target = null;
		drawn = null;
		show.snap(0f);
		sparks.clear();
		super.onDisable();
	}

	@Override
	public void onRenderHud(HudEvent event) {
		if (!isEnabled() || mc.player == null || mc.getNetworkHandler() == null)
			return;

		target = pick();

		if (target != null && target != drawn) {
			drawn = target;
			lastHurt = target.hurtTime;
			sparks.clear();
		}

		DrawContext context = event.context;
		float delta = mc.getRenderTickCounter().getDynamicDeltaTicks() / 20f;

		show.to(target != null ? 1f : 0f);
		show.update(delta);

		if (show.get() <= 0.02f || drawn == null) {
			placed = false;
			return;
		}

		float open = show.get();
		float grow = ease(Math.min(1f, open / 0.8f));
		float fade = Math.min(1f, open * 3f);
		float content = ease(Math.max(0f, (open - 0.35f) / 0.65f));

		float total = drawn.getHealth() + drawn.getAbsorptionAmount();
		health.to(Math.min(1f, total / drawn.getMaxHealth()));
		health.update(delta);

		String name = drawn.getName().getString();
		String hp = drawn.isInvisible() ? "??" : String.format("%.1f", total) + " HP";

		int full = Math.max(MIN_WIDTH, 44 + TextRenderer.smallBoldWidth(name) + 10 + TextRenderer.smallWidth(hp) + 9);

		int x = HudEditor.x(this, 8);
		int y = HudEditor.y(this, 220);
		float scale = size.getValueFloat() / 100f;

		float aimX = x;
		float aimY = y;
		boolean stuck = false;

		if (stick.getValue() && !HudEditor.editing()) {
			ProjectionUtils.ProjectedPoint point = ProjectionUtils.project(drawn.getLerpedPos(event.delta).add(0, drawn.getHeight() * 0.6, 0));
			long now = java.lang.System.currentTimeMillis();

			if (point != null && point.x() > -full * scale && point.y() > -HEIGHT * scale
					&& point.x() < mc.getWindow().getScaledWidth() && point.y() < mc.getWindow().getScaledHeight() + HEIGHT * scale) {
				pinX = point.x();
				pinY = point.y() - HEIGHT * scale / 2f;
				seen = now;
			}

			if (now - seen < GRACE) {
				aimX = pinX;
				aimY = pinY;
				stuck = true;
			}
		}

		if (!placed || !stick.getValue() || HudEditor.editing() || stuck && arrived) {
			floatX = aimX;
			floatY = aimY;
			placed = true;
		} else {
			float follow = 1f - (float) Math.exp(-delta * 10f);
			floatX += (aimX - floatX) * follow;
			floatY += (aimY - floatY) * follow;
		}

		arrived = stuck && Math.abs(aimX - floatX) < 1f && Math.abs(aimY - floatY) < 1f;

		int panelX = Math.round(floatX);
		int panelY = Math.round(floatY);

		HudEditor.push(context, scale, panelX, panelY);
		HudEditor.bounds(this, x, y, full, HEIGHT);

		int width = Math.round(HEIGHT + (full - HEIGHT) * grow);
		int height = HEIGHT;

		if (fading.getValue())
			Render2D.drawFade(context, panelX, panelY, width, height, 12, fadeSize.getValueFloat(), fadeGradient.getValue(), fade);

		if (ClickGUI.hudGlass.getValue())
			Render2D.drawBubble(context, panelX, panelY, width, height, 12, Theme.SHEEN, 7f, 0.35f, 0x66101010, fade);
		else Render2D.drawGlassRoundRect(context, panelX, panelY, width, height, 12, Theme.SHEEN, Theme.BLUR, Theme.surface, fade);

		if (outline.getValue())
			Render2D.drawRoundOutline(context, panelX, panelY, width, height, 12, 1, Theme.fade(Theme.accent, fade));

		float pop = 0.6f + 0.4f * ease(Math.min(1f, open / 0.6f));
		face(context, panelX + 7, panelY + 7, pop, fade);
		sparks(context, panelX + 7 + FACE / 2f, panelY + 7 + FACE / 2f, delta, fade);

		if (content <= 0.01f) {
			HudEditor.pop(context);
			return;
		}

		fade = content;
		int left = panelX + 44 - Math.round((1f - content) * 6f);
		int right = panelX + width - 9;

		context.enableScissor(panelX, panelY, panelX + width, panelY + height);

		TextRenderer.drawSmall(hp, context, right - TextRenderer.smallWidth(hp), panelY + 12, Theme.fade(Theme.accent, fade));

		context.enableScissor(left, panelY, right - TextRenderer.smallWidth(hp) - 6, panelY + height);
		TextRenderer.drawSmallBold(name, context, left, panelY + 12, Theme.fade(Theme.text, fade));
		context.disableScissor();

		for (int i = 0; i < ARMOR.length; i++) {
			ItemStack stack = drawn.getEquippedStack(ARMOR[i]);
			int slotX = left + i * 10;

			if (stack.isEmpty()) Render2D.drawRoundRect(context, slotX + 2, panelY + 22, 4, 4, 1.5f, Theme.fade(Theme.off, fade));
			else item(context, stack, slotX, panelY + 20);
		}

		item(context, drawn.getMainHandStack(), right - 18, panelY + 20);
		item(context, drawn.getOffHandStack(), right - 8, panelY + 20);

		Render2D.drawRoundRect(context, left, panelY + 32, right - left, 4, 2, Theme.fade(Theme.off, fade));
		if (health.get() > 0) {
			if (healthGradient.getValue())
				Render2D.drawGradientRect(context, left, panelY + 32, (right - left) * health.get(), 4, 2, Theme.fade(Theme.flow(0f), fade),
						Theme.fade(Theme.flow(health.get()), fade));
			else Render2D.drawRoundRect(context, left, panelY + 32, (right - left) * health.get(), 4, 2, Theme.fade(Theme.accent, fade));
		}

		context.disableScissor();
		HudEditor.pop(context);
	}

	private LivingEntity pick() {
		LivingEntity attacking = mc.player.getAttacking();

		if (mc.player.getLastAttackTime() != lastSwing) {
			lastSwing = mc.player.getLastAttackTime();

			if (attacking != null && attacking != mc.player)
				fighting = attacking;
			else if (!(mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof EndCrystalEntity))
				fighting = null;
		}

		if (fighting != null && fighting.isAlive() && !fighting.isRemoved()) {
			boolean fresh = keepOpen.getValue() || mc.player.age - mc.player.getLastAttackTime() <= timeout.getValue() * 20;
			boolean friend = fighting instanceof PlayerEntity player && hideFriends.getValue()
					&& dlindustries.uk.daduncs.System.INSTANCE.getFriendManager().isFriend(player);

			if (fresh && !friend)
				return fighting;
		}

		return HudEditor.editing() ? mc.player : null;
	}

	private static float ease(float t) {
		float inverse = 1f - Math.max(0f, Math.min(1f, t));
		return 1f - inverse * inverse * inverse * inverse;
	}

	private void face(DrawContext context, int x, int y, float pop, float fade) {
		float hurt = tint.getValue() ? Math.min(1f, drawn.hurtTime / 10f) * 0.45f : 0f;
		int color = Theme.fade(Theme.lerp(0xFFFFFFFF, HURT, hurt), fade);
		float size = FACE * pop;
		float faceX = x + (FACE - size) / 2f;
		float faceY = y + (FACE - size) / 2f;

		if (drawn instanceof AbstractClientPlayerEntity player) {
			Render2D.drawHead(context, faceX, faceY, size, 7f * pop, player.getSkin().body().texturePath(), color);
			return;
		}

		Render2D.drawRoundRect(context, faceX, faceY, size, size, 7f * pop, Theme.fade(Theme.off, fade * 0.6f));

		int left = Math.round(faceX);
		int top = Math.round(faceY);
		int right = Math.round(faceX + size);
		int bottom = Math.round(faceY + size);
		float span = Math.max(0.4f, Math.min(1.2f, Math.min(drawn.getWidth(), drawn.getHeight())));
		float aim = (drawn.getStandingEyeHeight() - drawn.getHeight() / 2f) / Math.max(0.01f, drawn.getScale());

		context.enableScissor(left, top, right, bottom);
		InventoryScreen.drawEntity(context, left, top, right, bottom, Math.round(size * 0.85f / span), aim,
				(left + right) / 2f, (top + bottom) / 2f, drawn);
		context.disableScissor();

		if (hurt > 0.01f)
			Render2D.drawRoundRect(context, faceX, faceY, size, size, 7f * pop, Theme.fade(HURT, fade * hurt * 0.6f));
	}

	private void sparks(DrawContext context, float centerX, float centerY, float delta, float fade) {
		if (drawn.hurtTime > lastHurt && particles.getValue()) {
			for (int i = 0; i < 7 + random.nextInt(4); i++)
				sparks.add(new Spark(random.nextFloat() * (float) Math.PI * 2f, 14f + random.nextFloat() * 12f, 2.8f + random.nextFloat() * 2.4f, 0.7f + random.nextFloat() * 0.4f));
		}

		lastHurt = drawn.hurtTime;

		Iterator<Spark> iterator = sparks.iterator();
		while (iterator.hasNext()) {
			Spark spark = iterator.next();
			spark.age += delta;

			float progress = spark.age / spark.life;
			if (progress >= 1f) {
				iterator.remove();
				continue;
			}

			float out = 1f - (1f - progress) * (1f - progress) * (1f - progress);
			float alpha = (1f - progress) * (1f - progress) * fade;
			float sparkX = centerX + (float) Math.cos(spark.angle) * spark.distance * out - spark.size / 2f;
			float sparkY = centerY + (float) Math.sin(spark.angle) * spark.distance * out - spark.size / 2f;

			Render2D.drawRoundImage(context, sparkX, sparkY, spark.size, spark.size, 0f, BLOOM, Theme.fade(Theme.lerp(0xFFFFFFFF, HURT, 0.7f), alpha));
		}
	}

	private void item(DrawContext context, ItemStack stack, int x, int y) {
		if (stack.isEmpty())
			return;

		Matrix3x2fStack matrices = context.getMatrices();
		matrices.pushMatrix();
		matrices.translate(x, y);
		matrices.scale(0.5f, 0.5f);
		context.drawItem(stack, 0, 0);
		matrices.popMatrix();
	}

	private static final class Spark {
		private final float angle;
		private final float distance;
		private final float size;
		private final float life;
		private float age;

		private Spark(float angle, float distance, float size, float life) {
			this.angle = angle;
			this.distance = distance;
			this.size = size;
			this.life = life;
		}
	}
}
