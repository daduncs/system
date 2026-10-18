package dlindustries.uk.daduncs.module.modules.render;

import dlindustries.uk.daduncs.System;
import dlindustries.uk.daduncs.event.events.HudListener;
import dlindustries.uk.daduncs.event.events.PacketReceiveListener;
import dlindustries.uk.daduncs.gui.Theme;
import dlindustries.uk.daduncs.mixin.LivingEntityAccessor;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.mode;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.render.Render2D;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.ProjectionUtils;
import dlindustries.uk.daduncs.utils.TextRenderer;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.TintedParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Nametags extends Module implements HudListener, PacketReceiveListener {

	public enum Mode {
		Default, Gay, Vanilla, Dsy3
	}

	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final int HEIGHT = 14;
	private static final int PAD = 4;
	private static final int GAP = 4;
	private static final int ICON = 10;
	private static final float ROUND = 5f;
	private static final int ITEM = 16;
	private static final int EFFECT = 9;

	private final mode<Mode> mode = new mode<>(EncryptedString.of("Mode"), Mode.Default, Mode.class);
	private final bool health = new bool(EncryptedString.of("Health"), true);
	private final bool pops = new bool(EncryptedString.of("Totem Pops"), true).setVisibility(() -> !mode.isMode(Mode.Dsy3));
	private final bool distance = new bool(EncryptedString.of("Distance"), true).setVisibility(() -> mode.isMode(Mode.Dsy3));
	private final bool ping = new bool(EncryptedString.of("Ping"), true).setVisibility(() -> mode.isMode(Mode.Dsy3));
	private final bool enchants = new bool(EncryptedString.of("Enchants"), true).setVisibility(() -> mode.isMode(Mode.Dsy3));
	private final bool effects = new bool(EncryptedString.of("Effects"), true).setVisibility(() -> mode.isMode(Mode.Dsy3));
	private final bool hideSneaking = new bool(EncryptedString.of("Hide Sneaking"), false).setVisibility(() -> mode.isMode(Mode.Dsy3));
	private final bool armor = new bool(EncryptedString.of("Armor"), true);
	private final bool held = new bool(EncryptedString.of("Held Items"), true);
	private final bool outline = new bool(EncryptedString.of("Outline"), true).setVisibility(() -> mode.isMode(Mode.Default) || mode.isMode(Mode.Gay));
	private final number size = new number(EncryptedString.of("Size"), 50, 200, 100, 5);
	private final bool stayFixed = new bool(EncryptedString.of("Stay Fixed"), false);

	private final Map<Integer, Integer> popped = new HashMap<>();
	private final ItemStack totem = new ItemStack(Items.TOTEM_OF_UNDYING);
	private final Map<Integer, RegistryEntry<StatusEffect>> byColor = new HashMap<>();
	private final Map<ParticleType<?>, RegistryEntry<StatusEffect>> byParticle = new HashMap<>();

	private ClientWorld world;

	public Nametags() {
		super(EncryptedString.of("Nametags"),
				EncryptedString.of("self explanatory."),
				-1,
				Category.RENDER);
		addSettings(mode, outline, health, pops, distance, ping, armor, held, enchants, effects, hideSneaking, size, stayFixed);
	}

	@Override
	public void onEnable() {
		eventManager.add(HudListener.class, this);
		eventManager.add(PacketReceiveListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(HudListener.class, this);
		eventManager.remove(PacketReceiveListener.class, this);
		popped.clear();
		super.onDisable();
	}

	public static boolean hidesVanilla() {
		Nametags module = System.INSTANCE.getModuleManager().getModule(Nametags.class);

		return module != null && module.isEnabled();
	}

	public static boolean hidesServerTag(Entity entity) {
		boolean tagLike = entity instanceof DisplayEntity.TextDisplayEntity
				|| entity instanceof ArmorStandEntity stand && stand.isInvisible() && stand.isCustomNameVisible();
		if (!tagLike || !hidesVanilla()) return false;

		Entity vehicle = entity.getRootVehicle();
		if (vehicle != entity) return vehicle instanceof PlayerEntity && vehicle != System.mc.player;

		for (PlayerEntity player : entity.getEntityWorld().getPlayers()) {
			if (player == System.mc.player) continue;

			double dx = entity.getX() - player.getX();
			double dz = entity.getZ() - player.getZ();
			double above = entity.getY() - (player.getY() + player.getHeight());
			if (dx * dx + dz * dz < 0.25 && above > -0.5 && above < 1.5) return true;
		}
		return false;
	}

	@Override
	public void onPacketReceive(PacketReceiveEvent event) {
		if (!(event.packet instanceof EntityStatusS2CPacket packet)) return;

		mc.execute(() -> count(packet));
	}

	private void count(EntityStatusS2CPacket packet) {
		if (mc.world == null) return;

		Entity entity = packet.getEntity(mc.world);
		if (!(entity instanceof PlayerEntity)) return;

		if (packet.getStatus() == 35) popped.merge(entity.getId(), 1, Integer::sum);
		else if (packet.getStatus() == 3) popped.remove(entity.getId());
	}

	@Override
	public void onRenderHud(HudEvent event) {
		if (mc.player == null || mc.world == null) return;

		if (mc.world != world) {
			popped.clear();
			world = mc.world;
		}

		List<PlayerEntity> players = new ArrayList<>(mc.world.getPlayers());
		players.remove(mc.player);
		players.sort(Comparator.comparingDouble(player -> -mc.player.squaredDistanceTo(player)));

		for (PlayerEntity player : players) {
			if (!player.isAlive()) continue;
			if (mode.isMode(Mode.Dsy3) && hideSneaking.getValue() && player.isSneaky()) continue;

			Vec3d head = player.getLerpedPos(event.delta).add(0, player.getHeight() + 0.3, 0);
			ProjectionUtils.ProjectedPoint point = ProjectionUtils.project(head);
			if (point == null) continue;

			float scale = size.getValueFloat() / 100f;
			if (stayFixed.getValue()) scale *= ProjectionUtils.shrink(head);

			if (mode.isMode(Mode.Dsy3)) detailed(event.context, player, point.x(), point.y(), scale);
			else draw(event.context, player, point.x(), point.y(), scale);
		}
	}

	private void draw(DrawContext context, PlayerEntity player, float x, float y, float scale) {
		boolean vanilla = mode.isMode(Mode.Vanilla);
		String name = player.getName().getString();
		String hp = String.valueOf(Math.round(player.getHealth() + player.getAbsorptionAmount()));
		int count = popped.getOrDefault(player.getId(), 0);
		String popText = String.valueOf(count);
		boolean showPops = pops.getValue() && count > 0;

		int pad = vanilla ? 2 : PAD;
		int height = vanilla ? 11 : HEIGHT;
		int width = pad + width(name, true) + pad;
		if (health.getValue()) width += width(hp, false) + GAP;
		if (showPops) width += GAP + ICON + 1 + width(popText, false);

		Matrix3x2fStack matrices = context.getMatrices();
		matrices.pushMatrix();
		matrices.translate(x, y);
		matrices.scale(scale, scale);

		int left = -width / 2;
		int top = -height;
		int middle = top + height / 2 - 1;

		background(context, left, top, width, height);

		int cursor = left + pad;

		matrices.pushMatrix();
		if (!vanilla) matrices.translate(0f, -0.5f);

		if (health.getValue()) {
			text(context, hp, cursor, middle, vanilla ? 0xFF55FF55 : Theme.accent, false);
			cursor += width(hp, false) + GAP;
		}

		text(context, name, cursor, middle, vanilla ? 0xFFFFFFFF : Theme.text, true);
		cursor += width(name, true);

		if (showPops) {
			cursor += GAP;
			drawIcon(context, totem, cursor, middle - ICON / 2);
			cursor += ICON + 1;
			text(context, popText, cursor, middle, vanilla ? 0xFFFFAA00 : Theme.text, false);
		}

		matrices.popMatrix();

		drawItems(context, player, top - 2);

		matrices.popMatrix();
	}

	private void background(DrawContext context, int left, int top, int width, int height) {
		switch (mode.getMode()) {
			case Default -> {
				Render2D.drawBlurRoundRect(context, left, top, width, height, 0f, 1f, Theme.BLUR, Theme.surface, 1f);

				if (outline.getValue())
					Render2D.drawRoundOutline(context, left, top, width, height, 0f, 1f, Theme.accent);
			}
			case Gay -> {
				if (ClickGUI.hudGlass.getValue())
					Render2D.drawBubble(context, left, top, width, height, ROUND, Theme.SHEEN, 7f, 0.35f, 0x66101010, 1f);
				else Render2D.drawBlurRoundRect(context, left, top, width, height, ROUND, 1f, Theme.BLUR, Theme.surface, 1f);

				if (outline.getValue())
					Render2D.drawRoundOutline(context, left, top, width, height, ROUND, 1f, Theme.accent);
			}
			case Vanilla, Dsy3 -> context.fill(left, top, left + width, top + height, 0x40000000);
		}
	}

	private int width(String text, boolean bold) {
		if (mode.isMode(Mode.Vanilla)) return mc.textRenderer.getWidth(text);

		return bold ? TextRenderer.smallBoldWidth(text) : TextRenderer.smallWidth(text);
	}

	private void text(DrawContext context, String text, int x, int middle, int color, boolean bold) {
		if (mode.isMode(Mode.Vanilla)) context.drawText(mc.textRenderer, text, x, middle - 3, color, true);
		else if (bold) TextRenderer.drawSmallBold(text, context, x, middle, color);
		else TextRenderer.drawSmall(text, context, x, middle, color);
	}

	private void drawIcon(DrawContext context, ItemStack stack, int x, int y) {
		Matrix3x2fStack matrices = context.getMatrices();
		matrices.pushMatrix();
		matrices.translate(x, y);
		matrices.scale(ICON / 16f, ICON / 16f);
		context.drawItem(stack, 0, 0);
		matrices.popMatrix();
	}

	private void drawItems(DrawContext context, PlayerEntity player, int bottom) {
		List<ItemStack> items = new ArrayList<>();

		if (held.getValue()) items.add(player.getMainHandStack());

		if (armor.getValue())
			for (EquipmentSlot slot : ARMOR)
				items.add(player.getEquippedStack(slot));

		if (held.getValue()) items.add(player.getOffHandStack());

		items.removeIf(ItemStack::isEmpty);

		int x = -items.size() * 17 / 2;

		for (ItemStack stack : items) {
			context.drawItem(stack, x, bottom - 16);
			context.drawStackOverlay(mc.textRenderer, stack, x, bottom - 16);
			x += 17;
		}
	}

	private void detailed(DrawContext context, PlayerEntity player, float x, float y, float scale) {
		List<String> parts = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();

		if (distance.getValue()) {
			parts.add(Math.round(mc.player.distanceTo(player)) + "m");
			colors.add(0xFFAAAAAA);
		}

		PlayerListEntry entry = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
		if (ping.getValue() && entry != null) {
			int latency = entry.getLatency();
			parts.add(latency + "ms");
			colors.add(latency < 80 ? 0xFF55FF55 : latency < 150 ? 0xFFFFFF55 : 0xFFFF5555);
		}

		parts.add(player.getName().getString());
		colors.add(0xFFFFFFFF);

		if (health.getValue()) {
			parts.add(String.format("%.1f", player.getHealth() + player.getAbsorptionAmount()));
			colors.add(0xFF55FF55);
		}

		int width = mc.textRenderer.getWidth(String.join(" ", parts));
		int space = mc.textRenderer.getWidth(" ");

		Matrix3x2fStack matrices = context.getMatrices();
		matrices.pushMatrix();
		matrices.translate(x, y);
		matrices.scale(scale, scale);

		context.fill(-width / 2 - 3, -12, width / 2 + 3, 0, 0x90000000);

		int cursor = -width / 2;
		for (int i = 0; i < parts.size(); i++) {
			context.drawText(mc.textRenderer, parts.get(i), cursor, -10, colors.get(i), true);
			cursor += mc.textRenderer.getWidth(parts.get(i)) + space;
		}

		float top = -12;
		List<ItemStack> items = items(player);
		if (!items.isEmpty()) top = enchanted(context, items, -12 - 2 - ITEM);

		List<RegistryEntry<StatusEffect>> shown = new ArrayList<>();
		List<String> times = new ArrayList<>();
		if (effects.getValue()) effects(player, shown, times);
		if (!shown.isEmpty()) potions(context, shown, times, Math.round(top) - 2 - EFFECT);

		matrices.popMatrix();
	}

	private List<ItemStack> items(PlayerEntity player) {
		List<ItemStack> items = new ArrayList<>();

		if (held.getValue()) items.add(player.getMainHandStack());

		if (armor.getValue())
			for (EquipmentSlot slot : ARMOR)
				items.add(player.getEquippedStack(slot));

		if (held.getValue()) items.add(player.getOffHandStack());

		items.removeIf(ItemStack::isEmpty);
		return items;
	}

	private float enchanted(DrawContext context, List<ItemStack> items, int top) {
		int x = -(items.size() * (ITEM + 2) - 2) / 2;
		float highest = top;

		for (ItemStack stack : items) {
			context.drawItem(stack, x, top);
			context.drawStackOverlay(mc.textRenderer, stack, x, top);

			if (enchants.getValue()) {
				float line = top - 5;

				for (Object2IntMap.Entry<RegistryEntry<Enchantment>> enchant : stack.getEnchantments().getEnchantmentEntries()) {
					String text = shortName(enchant.getKey(), enchant.getIntValue());
					int color = enchant.getKey().isIn(EnchantmentTags.CURSE) ? 0xFFFF5555 : 0xFFFFFFFF;

					half(context, text, x + (ITEM - mc.textRenderer.getWidth(text) / 2f) / 2f, line, color);
					highest = Math.min(highest, line);
					line -= 5;
				}
			}

			x += ITEM + 2;
		}

		return highest;
	}

	private void potions(DrawContext context, List<RegistryEntry<StatusEffect>> shown, List<String> times, int top) {
		int row = -2;
		for (String time : times) row += time.isEmpty() ? EFFECT + 2 : EFFECT + 1 + mc.textRenderer.getWidth(time) / 2 + 4;

		context.fill(-row / 2 - 2, top - 1, row / 2 + 2, top + EFFECT + 1, 0x90000000);

		int x = -row / 2;
		for (int i = 0; i < shown.size(); i++) {
			RegistryEntry<StatusEffect> effect = shown.get(i);
			String time = times.get(i);

			context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, InGameHud.getEffectTexture(effect), x, top, EFFECT, EFFECT);

			if (!time.isEmpty()) {
				int color = effect.value().getCategory() == StatusEffectCategory.HARMFUL ? 0xFFFF5555 : 0xFFFFFFFF;
				half(context, time, x + EFFECT + 1, top + 2.5f, color);
				x += EFFECT + 1 + mc.textRenderer.getWidth(time) / 2 + 4;
			} else x += EFFECT + 2;
		}
	}

	private void effects(PlayerEntity player, List<RegistryEntry<StatusEffect>> shown, List<String> times) {
		if (!player.getStatusEffects().isEmpty()) {
			for (StatusEffectInstance effect : player.getStatusEffects()) {
				shown.add(effect.getEffectType());
				times.add(time(effect));
			}
			return;
		}

		if (byColor.isEmpty()) {
			Registries.STATUS_EFFECT.streamEntries().forEach(effect -> {
				byColor.putIfAbsent(effect.value().getColor() & 0xFFFFFF, effect);

				ParticleType<?> type = effect.value().createParticle(new StatusEffectInstance(effect)).getType();
				if (type != ParticleTypes.ENTITY_EFFECT) byParticle.putIfAbsent(type, effect);
			});
		}

		Set<RegistryEntry<StatusEffect>> seen = new HashSet<>();
		for (ParticleEffect particle : player.getDataTracker().get(LivingEntityAccessor.getPotionSwirls())) {
			RegistryEntry<StatusEffect> effect;

			if (particle instanceof TintedParticleEffect tinted && tinted.getType() == ParticleTypes.ENTITY_EFFECT) {
				int rgb = Math.round(tinted.getRed() * 255) << 16 | Math.round(tinted.getGreen() * 255) << 8 | Math.round(tinted.getBlue() * 255);
				effect = byColor.get(rgb);
			} else effect = byParticle.get(particle.getType());

			if (effect != null && seen.add(effect)) {
				shown.add(effect);
				times.add("");
			}
		}
	}

	private String time(StatusEffectInstance effect) {
		String level = effect.getAmplifier() > 0 ? (effect.getAmplifier() + 1) + " " : "";
		if (effect.isInfinite()) return level + "**:**";

		int seconds = effect.getDuration() / 20;
		return level + seconds / 60 + ":" + String.format("%02d", seconds % 60);
	}

	private String shortName(RegistryEntry<Enchantment> enchant, int level) {
		String path = enchant.getKey().map(key -> key.getValue().getPath()).orElse("?");
		if (path.startsWith("curse_of_")) path = path.substring(9);
		else if (path.endsWith("_curse")) path = path.substring(0, path.length() - 6);

		String[] words = path.split("_");
		String name = words.length > 1 ? "" + words[0].charAt(0) + words[1].charAt(0) : path.substring(0, Math.min(2, path.length()));
		name = Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase();

		return enchant.value().getMaxLevel() == 1 ? name : name + level;
	}

	private void half(DrawContext context, String text, float x, float y, int color) {
		Matrix3x2fStack matrices = context.getMatrices();
		matrices.pushMatrix();
		matrices.translate(x, y);
		matrices.scale(0.5f, 0.5f);
		context.drawText(mc.textRenderer, text, 0, 0, color, true);
		matrices.popMatrix();
	}
}
