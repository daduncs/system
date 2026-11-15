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

package dlindustries.uk.daduncs.module.modules.crystal;

import dlindustries.uk.daduncs.event.events.ItemUseListener;
import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.bind;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.*;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public final class AnchorMacro extends Module implements TickListener, ItemUseListener {
	private final bool whileUse = new bool(EncryptedString.of("While Use"), true).setDescription(EncryptedString.of("If it should trigger while eating/using shield"));
	private final bool friendSafe = new bool(EncryptedString.of("Friend Safe"), true)
			.setDescription(EncryptedString.of("Doesn't anchor next to a friend"));
	private final bool stopOnKill = new bool(EncryptedString.of("Stop on Kill"), false).setDescription(EncryptedString.of("Doesn't anchor if body nearby"));
	private final bool clickSimulation = new bool(EncryptedString.of("Click Simulation"), false).setDescription(EncryptedString.of("Makes the CPS hud think you're legit"));

	private final number switchDelay = new number(EncryptedString.of("Switch Delay"), 0, 1000, 70, 10);
	private final number switchChance = new number(EncryptedString.of("Switch Chance"), 0, 100, 100, 1);
	private final number placeChance = new number(EncryptedString.of("Place Chance"), 0, 100, 100, 1).setDescription(EncryptedString.of("Randomization"));

	private final number glowstoneDelay = new number(EncryptedString.of("Glowstone Delay"), 0, 1000, 30, 10);
	private final number glowstoneChance = new number(EncryptedString.of("Glowstone Chance"), 0, 100, 100, 1);

	private final number explodeDelay = new number(EncryptedString.of("Explode Delay"), 0, 1000, 70, 10);
	private final number explodeChance = new number(EncryptedString.of("Explode Chance"), 0, 100, 100, 1);
	private final number explodeSlot = new number(EncryptedString.of("Explode Slot"), 1, 9, 9, 1);

	private final bool antiDamageTick = new bool(EncryptedString.of("Anti Damage Tick"), true)
			.setDescription(EncryptedString.of("Waits for enemy damage immunity window before exploding airplaced anchors"));
	private static final int HURT_TIME_THRESHOLD = 0;

	private final bool onlyOwn = new bool(EncryptedString.of("Only Own"), false);
	private final bool onlyCharge = new bool(EncryptedString.of("Only Charge"), false);

	private final bind activateKey = new bind(EncryptedString.of("Airplace key hold"), GLFW.GLFW_KEY_UNKNOWN, false)
			.setDescription(EncryptedString.of("Recommended to set as anchor bind. While key held during normal anchoring will airplace instead."))
			.setTag(EncryptedString.of("(READ DESC)"))
			.setShowDescription(true);

	private final TimerUtils switchTimer = new TimerUtils();
	private double switchTargetDelay = 0;

	private final TimerUtils glowstoneTimer = new TimerUtils();
	private double glowstoneTargetDelay = 0;

	private final TimerUtils explodeTimer = new TimerUtils();
	private double explodeTargetDelay = 0;

	private final Set<BlockPos> ownedAnchors = new HashSet<>();
	private final Set<BlockPos> airplacedAnchors = new HashSet<>();
	private final Map<BlockPos, Long> airplacedTimestamps = new HashMap<>();
	private final Set<BlockPos> airplaceDamageWindowCleared = new HashSet<>();
	private BlockPos lastExplodedPos = null;

	public AnchorMacro() {
		super(EncryptedString.of("Anchor Macro"),
				EncryptedString.of("Automatically blows up respawn anchors for you"),
				-1,
				Category.CRYSTAL);
		addSettings(whileUse, friendSafe, stopOnKill, clickSimulation, placeChance, switchDelay, switchChance, glowstoneDelay, glowstoneChance, explodeDelay, explodeChance, explodeSlot, antiDamageTick, onlyOwn, onlyCharge, activateKey);
	}

	private double calculateDelay(number delaySetting) {
		double delay = delaySetting.getValue();
		if (ClickGUI.randomisedTiming.getValue()) {
			int var = ClickGUI.VARIANCE;
			if (var > 0) {
				int offset = (int) (Math.random() * var);
				delay += offset;
			}
		}
		return delay;
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		eventManager.add(ItemUseListener.class, this);

		switchTimer.reset();
		switchTargetDelay = calculateDelay(switchDelay);

		glowstoneTimer.reset();
		glowstoneTargetDelay = calculateDelay(glowstoneDelay);

		explodeTimer.reset();
		explodeTargetDelay = calculateDelay(explodeDelay);

		ownedAnchors.clear();
		airplacedAnchors.clear();
		airplacedTimestamps.clear();
		airplaceDamageWindowCleared.clear();
		lastExplodedPos = null;

		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		eventManager.remove(ItemUseListener.class, this);
		ownedAnchors.clear();
		airplacedAnchors.clear();
		airplacedTimestamps.clear();
		airplaceDamageWindowCleared.clear();
		lastExplodedPos = null;
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.world == null || mc.currentScreen != null)
			return;

		if (mc.player.isUsingItem())
			return;

		if (((mc.player.getMainHandStack().getItem().getComponents().contains(DataComponentTypes.FOOD) || mc.player.getMainHandStack().getItem() instanceof ShieldItem || mc.player.getOffHandStack().getItem() instanceof ShieldItem || mc.player.getOffHandStack().getItem().getComponents().contains(DataComponentTypes.FOOD)) && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS) && whileUse.getValue())
			return;

		if(stopOnKill.getValue() && WorldUtils.isDeadBodyNearby())
			return;

		if (friendSafe.getValue() && friendNear())
			return;

		int randomInt = MathUtils.randomInt(1, 100);

		if (KeyUtils.isKeyPressed(GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
			if (mc.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult) {
				mc.options.useKey.setPressed(false);
				return;
			}

			if (mc.crosshairTarget instanceof BlockHitResult hit) {
				BlockPos targetPos = hit.getBlockPos();

				if (lastExplodedPos != null) {
					if ((targetPos.equals(lastExplodedPos.down()) && hit.getSide() == Direction.UP) || targetPos.equals(lastExplodedPos)) {
						if (BlockUtils.isBlock(lastExplodedPos, Blocks.RESPAWN_ANCHOR) || airplacedAnchors.contains(lastExplodedPos)) {
							mc.options.useKey.setPressed(false);
							if (!BlockUtils.isBlock(targetPos, Blocks.RESPAWN_ANCHOR)) {
								return;
							}
						}
					}
				}

				if (BlockUtils.isBlock(targetPos, Blocks.RESPAWN_ANCHOR)) {
					if (onlyOwn.getValue()) {
						boolean isOwned = ownedAnchors.contains(targetPos);
						boolean isLastExploded = (lastExplodedPos != null && targetPos.equals(lastExplodedPos));
						if (!isOwned && !isLastExploded) {
							return;
						}
					}

					mc.options.useKey.setPressed(false);

					if (AntibanbyDaduncs.isActionSent() || AntibanbyDaduncs.isSlotPending()) {
						return;
					}

					if (BlockUtils.isAnchorNotCharged(targetPos)) {
						randomInt = MathUtils.randomInt(1, 100);

						if (randomInt <= placeChance.getValueInt()) {
							if (!mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
								if (!switchTimer.hasReached(switchTargetDelay)) {
									return;
								}

								randomInt = MathUtils.randomInt(1, 100);

								if(randomInt <= switchChance.getValueInt()) {
									switchTimer.reset();
									switchTargetDelay = calculateDelay(switchDelay);
									dlindustries.uk.daduncs.managers.Recorder tm = dlindustries.uk.daduncs.managers.Recorder.getInstance();
									if (tm != null) {
										tm.recordModuleEvent("AnchorMacro", "SWITCH_GLOWSTONE", "Selecting GLOWSTONE from hotbar", null);
									}
									InventoryUtils.selectItemFromHotbar(Items.GLOWSTONE);
								}
							}

							if (mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
								if (!glowstoneTimer.hasReached(glowstoneTargetDelay)) {
									return;
								}

								randomInt = MathUtils.randomInt(1, 100);

								if(randomInt <= glowstoneChance.getValueInt()) {
									glowstoneTimer.reset();
									glowstoneTargetDelay = calculateDelay(glowstoneDelay);

									if (clickSimulation.getValue())
										MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);

									dlindustries.uk.daduncs.managers.Recorder tm = dlindustries.uk.daduncs.managers.Recorder.getInstance();
									if (tm != null) {
										tm.recordModuleEvent("AnchorMacro", "PLACE_GLOWSTONE", "Placing glowstone at " + hit.getBlockPos().toShortString(), null);
									}

									WorldUtils.placeBlock(hit, true);
									return;
								}
							}
						}
					}

					if (BlockUtils.isAnchorCharged(targetPos)) {
						int configuredSlot = explodeSlot.getValueInt() - 1;

						boolean isAirplaced = airplacedAnchors.contains(targetPos) || isFloatingInAir(targetPos);
						boolean activateHeld = (activateKey.getKey() != -1 && KeyUtils.isKeyPressed(activateKey.getKey()));
						int targetSlot = configuredSlot;

						DoubleAnchor doubleAnchor = dlindustries.uk.daduncs.System.INSTANCE.getModuleManager().getModule(DoubleAnchor.class);
						boolean doubleAnchorEnabled = doubleAnchor != null && doubleAnchor.isEnabled();

						if (activateHeld && doubleAnchorEnabled && !isAirplaced) {
							int respawnHotbarSlot = -1;
							for (int i = 0; i < 9; i++) {
								if (mc.player.getInventory().getStack(i).isOf(Items.RESPAWN_ANCHOR)) {
									respawnHotbarSlot = i;
									break;
								}
							}
							if (respawnHotbarSlot != -1) {
								targetSlot = respawnHotbarSlot;
							}
						}

						if (mc.player.getInventory().getSelectedSlot() != targetSlot) {
							if (!switchTimer.hasReached(switchTargetDelay)) {
								return;
							}

							if(randomInt <= switchChance.getValueInt()) {
								switchTimer.reset();
								switchTargetDelay = calculateDelay(switchDelay);
								dlindustries.uk.daduncs.managers.Recorder tm = dlindustries.uk.daduncs.managers.Recorder.getInstance();
								if (tm != null) {
									tm.recordModuleEvent("AnchorMacro", "SWITCH_EXPLODE_SLOT", "Setting selectedSlot to " + targetSlot + " (current=" + mc.player.getInventory().getSelectedSlot() + ")", null);
								}
								InventoryUtils.setInvSlot(targetSlot);
							}
						}

						if (mc.player.getInventory().getSelectedSlot() == targetSlot) {
							if (shouldWaitForHurtTime(targetPos, isAirplaced)) {
								return;
							}

							if (!explodeTimer.hasReached(explodeTargetDelay)) {
								return;
							}

							randomInt = MathUtils.randomInt(1, 100);

							if(randomInt <= explodeChance.getValueInt()) {
								explodeTimer.reset();
								explodeTargetDelay = calculateDelay(explodeDelay);

								if (!onlyCharge.getValue()) {
									if (clickSimulation.getValue() && !isAirplaced)
										MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);

									dlindustries.uk.daduncs.managers.Recorder tm = dlindustries.uk.daduncs.managers.Recorder.getInstance();
									boolean shouldAirplace = activateHeld && doubleAnchorEnabled && !isAirplaced && mc.player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR);

									if (shouldAirplace) {
										if (tm != null) {
											tm.recordModuleEvent("AnchorMacro", "AIRPLACE_DOUBLE_ANCHOR", "DoubleAnchor.doAirplace at " + hit.getBlockPos().toShortString(), null);
										}
										if (DoubleAnchor.doAirplace(hit)) {
											lastExplodedPos = targetPos;
											ownedAnchors.add(targetPos);
											airplacedAnchors.add(targetPos);
											airplacedTimestamps.put(targetPos, java.lang.System.currentTimeMillis());
											airplaceDamageWindowCleared.remove(targetPos);
										}
									} else {
										if (tm != null) {
											tm.recordModuleEvent("AnchorMacro", "EXPLODE_ANCHOR", "WorldUtils.placeBlock at " + hit.getBlockPos().toShortString() + " with slot=" + targetSlot, null);
										}
										WorldUtils.placeBlock(hit, true);
										lastExplodedPos = targetPos;
										ownedAnchors.remove(targetPos);
										airplacedAnchors.remove(targetPos);
										airplacedTimestamps.remove(targetPos);
										airplaceDamageWindowCleared.remove(targetPos);
									}
								}
							}
						}
					}
				}
			}
		}
	}

	public java.util.Map<String, Object> recorder() {
		java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
		map.put("switchTimerElapsedMs", switchTimer.getElapsedTime());
		map.put("switchTargetDelay", switchTargetDelay);
		map.put("glowstoneTimerElapsedMs", glowstoneTimer.getElapsedTime());
		map.put("glowstoneTargetDelay", glowstoneTargetDelay);
		map.put("explodeTimerElapsedMs", explodeTimer.getElapsedTime());
		map.put("explodeTargetDelay", explodeTargetDelay);
		map.put("explodeSlotSetting", explodeSlot.getValueInt());
		map.put("antiDamageTick", antiDamageTick.getValue());
		map.put("hurtTimeThreshold", HURT_TIME_THRESHOLD);
		map.put("onlyOwn", onlyOwn.getValue());
		map.put("onlyCharge", onlyCharge.getValue());
		map.put("ownedAnchorsCount", ownedAnchors.size());
		map.put("airplacedAnchorsCount", airplacedAnchors.size());
		map.put("airplacedTimestampsCount", airplacedTimestamps.size());
		map.put("airplaceDamageWindowClearedCount", airplaceDamageWindowCleared.size());
		map.put("lastExplodedPos", lastExplodedPos != null ? lastExplodedPos.toShortString() : "null");
		return map;
	}

	@Override
	public void onItemUse(ItemUseEvent event) {
		if (mc.crosshairTarget instanceof BlockHitResult hitResult && hitResult.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = hitResult.getBlockPos();

			if (mc.player.getMainHandStack().getItem() == Items.RESPAWN_ANCHOR) {
				Direction dir = hitResult.getSide();
				BlockPos placedPos;

				if (!mc.world.getBlockState(pos).isReplaceable()) {
					placedPos = pos.offset(dir);
				} else {
					placedPos = pos;
				}

				ownedAnchors.add(placedPos);
				airplacedAnchors.remove(placedPos);
				airplacedTimestamps.remove(placedPos);
				airplaceDamageWindowCleared.remove(placedPos);
			}

			if(BlockUtils.isAnchorCharged(pos)) {
				ownedAnchors.remove(pos);
				airplacedAnchors.remove(pos);
				airplacedTimestamps.remove(pos);
				airplaceDamageWindowCleared.remove(pos);
			}
		}
	}

	private boolean friendNear() {
		if (mc.crosshairTarget == null || mc.world == null)
			return false;

		for (net.minecraft.entity.player.PlayerEntity player : mc.world.getPlayers()) {
			if (player == mc.player || !dlindustries.uk.daduncs.System.INSTANCE.getFriendManager().isFriend(player))
				continue;

			if (player.getEntityPos().distanceTo(mc.crosshairTarget.getPos()) < 6)
				return true;
		}

		return false;
	}

	private boolean isFloatingInAir(BlockPos pos) {
		return mc.world != null
				&& mc.world.getBlockState(pos.down()).isAir()
				&& mc.world.getBlockState(pos.north()).isAir()
				&& mc.world.getBlockState(pos.south()).isAir()
				&& mc.world.getBlockState(pos.east()).isAir()
				&& mc.world.getBlockState(pos.west()).isAir();
	}

	private PlayerEntity getTargetEnemy(BlockPos anchorPos) {
		if (mc.world == null) return null;
		Vec3d anchorCenter = anchorPos.toCenterPos();
		PlayerEntity bestTarget = null;
		double bestDistSq = Double.MAX_VALUE;

		for (PlayerEntity player : mc.world.getPlayers()) {
			if (player == mc.player || !player.isAlive()) continue;
			if (player.isCreative() || player.isSpectator()) continue;
			if (dlindustries.uk.daduncs.System.INSTANCE.getFriendManager().isFriend(player)) continue;

			double distSq = player.squaredDistanceTo(anchorCenter);
			if (distSq <= 36.0) {
				if (distSq < bestDistSq) {
					bestDistSq = distSq;
					bestTarget = player;
				}
			}
		}
		return bestTarget;
	}

	private boolean shouldWaitForHurtTime(BlockPos targetPos, boolean isAirplaced) {
		if (!antiDamageTick.getValue()) {
			return false;
		}

		if (isAirplaced) {
			if (airplaceDamageWindowCleared.contains(targetPos)) {
				return false;
			}

			long now = java.lang.System.currentTimeMillis();
			Long airplaceTime = airplacedTimestamps.get(targetPos);
			if (airplaceTime == null) {
				airplacedTimestamps.put(targetPos, now);
				airplaceTime = now;
			}

			if ((now - airplaceTime) >= 500L) {
				airplaceDamageWindowCleared.add(targetPos);
				return false;
			}

			PlayerEntity target = getTargetEnemy(targetPos);
			if (target == null) {
				airplaceDamageWindowCleared.add(targetPos);
				return false;
			}

			if (target.hurtTime <= HURT_TIME_THRESHOLD) {
				airplaceDamageWindowCleared.add(targetPos);
				return false;
			}

			return true;
		}

		return false;
	}
}