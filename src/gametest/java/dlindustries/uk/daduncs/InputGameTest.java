package dlindustries.uk.daduncs;

import dlindustries.uk.daduncs.event.events.PacketSendListener;
import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.mixin.KeyBindingAccessor;
import dlindustries.uk.daduncs.mixin.MinecraftClientAccessor;
import dlindustries.uk.daduncs.module.modules.crystal.AutoInventoryTotem;
import dlindustries.uk.daduncs.module.modules.misc.FastPlace;
import dlindustries.uk.daduncs.module.modules.sword.TriggerBot;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.utils.rotation.SilentRot;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;

public final class InputGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksDownload();
            var modules = System.INSTANCE.getModuleManager();
            var fastPlace = modules.getModule(FastPlace.class);
            var trigger = modules.getModule(TriggerBot.class);
            var events = System.INSTANCE.getEventManager();
            var actions = new ArrayList<PlayerActionC2SPacket.Action>();
            PacketSendListener packets = event -> {
                if (event.packet instanceof PlayerActionC2SPacket action) actions.add(action.getAction());
            };
            TickListener beforeInput = () -> {
                var key = (KeyBindingAccessor) MinecraftClient.getInstance().options.swapHandsKey;
                if (key.getTimesPressed() > 0 && (!AntibanbyDaduncs.hasPendingInventoryKey() || AntibanbyDaduncs.canAttackNow())) {
                    throw new AssertionError("Queued inventory input must precede module attacks");
                }
            };
            try {
                context.runOnClient(client -> {
                    fastPlace.setEnabled(true);
                    if (modules.hasGameplayModulesEnabled()) throw new AssertionError("FastPlace enabled the gameplay filter");
                    checkCooldown(client, fastPlace, Items.STONE, Items.AIR, 0);
                    checkCooldown(client, fastPlace, Items.AIR, Items.STONE, 0);
                    checkCooldown(client, fastPlace, Items.APPLE, Items.APPLE, 4);
                    checkCooldown(client, fastPlace, Items.GLOWSTONE, Items.STONE, 4);
                    checkCooldown(client, fastPlace, Items.STONE, Items.RESPAWN_ANCHOR, 4);

                    trigger.setEnabled(true);
                    if (!modules.hasGameplayModulesEnabled()) throw new AssertionError("TriggerBot needs coordinated input");
                    events.add(PacketSendListener.class, packets);
                    events.add(TickListener.class, beforeInput);
                    queuePress(client.options.swapHandsKey);
                    queuePress(client.options.swapHandsKey);
                    queuePress(client.options.dropKey);
                });
                context.waitTick();
                context.runOnClient(client -> {
                    var swap = PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND;
                    if (!actions.equals(List.of(swap, swap, PlayerActionC2SPacket.Action.DROP_ITEM))) {
                        throw new AssertionError("Unexpected inventory key order " + actions);
                    }
                });
            } finally {
                context.runOnClient(client -> {
                    events.remove(TickListener.class, beforeInput);
                    events.remove(PacketSendListener.class, packets);
                    trigger.setEnabled(false);
                    fastPlace.setEnabled(false);
                });
            }
            checkAutoOpen(context, world);
            checkRepeatedUse(context, world);
            CrystalInputTest.run(context, world);
            checkCamera(context);
        }
        context.waitTick();
        context.runOnClient(client -> {
            if (SilentRot.on()) throw new AssertionError("Rotation survived leaving the world");
        });
    }

    private static void checkAutoOpen(ClientGameTestContext context, TestSingleplayerContext world) {
        var totem = System.INSTANCE.getModuleManager().getModule(AutoInventoryTotem.class);
        var autoOpen = (bool) totem.getSettings().stream()
                .filter(setting -> setting.getName().toString().equals("Auto Open")).findFirst().orElseThrow();
        var swaps = new ArrayList<PlayerActionC2SPacket.Action>();
        PacketSendListener listener = event -> {
            if (event.packet instanceof PlayerActionC2SPacket action
                    && action.getAction() == PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND) swaps.add(action.getAction());
        };
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerManager().getPlayerList().getFirst();
            player.changeGameMode(GameMode.SURVIVAL);
            player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            player.getInventory().setStack(9, Items.TOTEM_OF_UNDYING.getDefaultStack());
            player.playerScreenHandler.sendContentUpdates();
        });
        context.waitFor(client -> client.player.getInventory().getStack(9).isOf(Items.TOTEM_OF_UNDYING)
                && !client.player.isInCreativeMode());
        try {
            context.runOnClient(client -> {
                autoOpen.setValue(true);
                totem.setEnabled(true);
                System.INSTANCE.getEventManager().add(PacketSendListener.class, listener);
                queuePress(client.options.swapHandsKey);
                queuePress(client.options.swapHandsKey);
            });
            context.waitFor(client -> client.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)
                    && client.currentScreen == null);
            context.runOnClient(client -> {
                if (swaps.size() != 2) throw new AssertionError("Auto Open consumed a queued swap-hands press");
            });
            world.getServer().runOnServer(server -> {
                if (!server.getPlayerManager().getPlayerList().getFirst().getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                    throw new AssertionError("Server did not receive the offhand inventory swap");
                }
            });
        } finally {
            context.runOnClient(client -> {
                System.INSTANCE.getEventManager().remove(PacketSendListener.class, listener);
                totem.setEnabled(false);
                autoOpen.setValue(false);
            });
        }
    }

    private static void checkRepeatedUse(ClientGameTestContext context, TestSingleplayerContext world) {
        var trigger = System.INSTANCE.getModuleManager().getModule(TriggerBot.class);
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerManager().getPlayerList().getFirst();
            player.setStackInHand(Hand.MAIN_HAND, Items.STICK.getDefaultStack());
            player.setStackInHand(Hand.OFF_HAND, Items.STICK.getDefaultStack());
            player.playerScreenHandler.sendContentUpdates();
        });
        context.waitFor(client -> client.player.getMainHandStack().isOf(Items.STICK) && client.player.getOffHandStack().isOf(Items.STICK));
        var hands = new ArrayList<Hand>();
        PacketSendListener packets = event -> {
            if (event.packet instanceof PlayerInteractItemC2SPacket use) hands.add(use.getHand());
        };
        try {
            context.runOnClient(client -> {
                client.player.setPitch(-90);
                System.INSTANCE.getEventManager().add(PacketSendListener.class, packets);
            });
            for (boolean coordinated : List.of(false, true)) {
                context.runOnClient(client -> {
                    if (coordinated) trigger.setEnabled(true);
                    hands.clear();
                    queuePress(client.options.useKey);
                    queuePress(client.options.useKey);
                });
                context.waitTick();
                context.runOnClient(client -> {
                    if (!hands.equals(List.of(Hand.MAIN_HAND, Hand.OFF_HAND, Hand.MAIN_HAND, Hand.OFF_HAND))) {
                        throw new AssertionError("Repeated Use with coordinated input " + coordinated + " gave " + hands);
                    }
                });
            }
        } finally {
            context.runOnClient(client -> {
                System.INSTANCE.getEventManager().remove(PacketSendListener.class, packets);
                trigger.setEnabled(false);
            });
        }
    }

    private static void checkCamera(ClientGameTestContext context) {
        var visibleYaw = context.computeOnClient(client -> client.player.getYaw());
        var visiblePitch = context.computeOnClient(client -> client.player.getPitch());
        Object first = new Object();
        Object second = new Object();
        context.runOnClient(client -> {
            SilentRot.set(visibleYaw + 45, 30, first);
            SilentRot.set(visibleYaw + 90, 60, second);
            SilentRot.stop(second);
        });
        context.waitTick();
        context.runOnClient(client -> {
            if (client.player.getYaw() != visibleYaw || client.player.getPitch() != visiblePitch) {
                throw new AssertionError("The movement tick changed the visible view");
            }
            if (!AntibanbyDaduncs.hasSentRotation() || AntibanbyDaduncs.getLastSentYaw() != visibleYaw + 45
                    || AntibanbyDaduncs.getLastSentPitch() != 30) throw new AssertionError("First rotation lost ownership");
            SilentRot.stop(first);
        });
        context.waitTick();
        context.runOnClient(client -> {
            if (AntibanbyDaduncs.getLastSentYaw() != visibleYaw || AntibanbyDaduncs.getLastSentPitch() != visiblePitch) {
                throw new AssertionError("Movement did not return to the visible direction");
            }
            SilentRot.set(visibleYaw + 30, 0, second);
        });
    }

    private static void checkCooldown(MinecraftClient client, FastPlace module, Item main, Item off, int expected) {
        client.player.setStackInHand(Hand.MAIN_HAND, main.getDefaultStack());
        client.player.setStackInHand(Hand.OFF_HAND, off.getDefaultStack());
        var input = (MinecraftClientAccessor) client;
        input.setItemUseCooldown(4);
        module.apply();
        if (input.getItemUseCooldown() != expected) throw new AssertionError("FastPlace with " + main + " and " + off);
    }

    private static void queuePress(KeyBinding binding) {
        KeyBinding.onKeyPressed(((KeyBindingAccessor) binding).getBoundKey());
    }
}
