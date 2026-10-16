package dlindustries.uk.daduncs.module.modules.render;

import dlindustries.uk.daduncs.System;
import dlindustries.uk.daduncs.event.events.HudListener;
import dlindustries.uk.daduncs.event.events.PacketReceiveListener;
import dlindustries.uk.daduncs.gui.Theme;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.render.Render2D;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.ProjectionUtils;
import dlindustries.uk.daduncs.utils.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Nametags extends Module implements HudListener, PacketReceiveListener {

	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final int HEIGHT = 14;
	private static final int PAD = 4;
	private static final int GAP = 4;
	private static final int ICON = 10;

	private final bool health = new bool(EncryptedString.of("Health"), true);
	private final bool pops = new bool(EncryptedString.of("Totem Pops"), true);
	private final bool armor = new bool(EncryptedString.of("Armor"), true);
	private final number size = new number(EncryptedString.of("Size"), 50, 200, 100, 5);

	private final Map<Integer, Integer> popped = new HashMap<>();
	private final ItemStack totem = new ItemStack(Items.TOTEM_OF_UNDYING);

	private ClientWorld world;

	public Nametags() {
		super(EncryptedString.of("Nametags"),
				EncryptedString.of("self explanatory."),
				-1,
				Category.RENDER);
		addSettings(health, pops, armor, size);
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

			Vec3d head = player.getLerpedPos(event.delta).add(0, player.getHeight() + 0.3, 0);
			ProjectionUtils.ProjectedPoint point = ProjectionUtils.project(head);
			if (point == null) continue;

			draw(event.context, player, point.x(), point.y());
		}
	}

	private void draw(DrawContext context, PlayerEntity player, float x, float y) {
		String name = player.getName().getString();
		String hp = String.valueOf(Math.round(player.getHealth() + player.getAbsorptionAmount()));
		int count = popped.getOrDefault(player.getId(), 0);
		String popText = String.valueOf(count);
		boolean showPops = pops.getValue() && count > 0;

		int width = PAD + TextRenderer.smallBoldWidth(name) + PAD;
		if (health.getValue()) width += TextRenderer.smallWidth(hp) + GAP;
		if (showPops) width += GAP + ICON + 1 + TextRenderer.smallWidth(popText);

		Matrix3x2fStack matrices = context.getMatrices();
		matrices.pushMatrix();
		matrices.translate(x, y);
		matrices.scale(size.getValueFloat() / 100f, size.getValueFloat() / 100f);

		int left = -width / 2;
		int top = -HEIGHT;
		int middle = top + HEIGHT / 2 - 1;

		Render2D.drawBlurRoundRect(context, left, top, width, HEIGHT, 0f, 1f, Theme.BLUR, Theme.surface, 1f);
		Render2D.drawRoundOutline(context, left, top, width, HEIGHT, 0f, 1f, Theme.accent);

		int cursor = left + PAD;

		matrices.pushMatrix();
		matrices.translate(0f, -0.5f);

		if (health.getValue()) {
			TextRenderer.drawSmall(hp, context, cursor, middle, Theme.accent);
			cursor += TextRenderer.smallWidth(hp) + GAP;
		}

		TextRenderer.drawSmallBold(name, context, cursor, middle, Theme.text);
		cursor += TextRenderer.smallBoldWidth(name);

		if (showPops) {
			cursor += GAP;
			drawIcon(context, totem, cursor, middle - ICON / 2);
			cursor += ICON + 1;
			TextRenderer.drawSmall(popText, context, cursor, middle, Theme.text);
		}

		matrices.popMatrix();

		if (armor.getValue()) drawArmor(context, player, top - 2);

		matrices.popMatrix();
	}

	private void drawIcon(DrawContext context, ItemStack stack, int x, int y) {
		Matrix3x2fStack matrices = context.getMatrices();
		matrices.pushMatrix();
		matrices.translate(x, y);
		matrices.scale(ICON / 16f, ICON / 16f);
		context.drawItem(stack, 0, 0);
		matrices.popMatrix();
	}

	private void drawArmor(DrawContext context, PlayerEntity player, int bottom) {
		List<ItemStack> worn = new ArrayList<>();

		for (EquipmentSlot slot : ARMOR) {
			ItemStack stack = player.getEquippedStack(slot);
			if (!stack.isEmpty()) worn.add(stack);
		}

		int x = -worn.size() * 17 / 2;

		for (ItemStack stack : worn) {
			context.drawItem(stack, x, bottom - 16);
			context.drawStackOverlay(mc.textRenderer, stack, x, bottom - 16);
			x += 17;
		}
	}
}
