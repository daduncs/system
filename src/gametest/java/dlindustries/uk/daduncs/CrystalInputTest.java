package dlindustries.uk.daduncs;

import dlindustries.uk.daduncs.event.events.PacketSendListener;
import dlindustries.uk.daduncs.mixin.KeyBindingAccessor;
import dlindustries.uk.daduncs.mixin.PlayerInteractEntityC2SPacketAccessor;
import dlindustries.uk.daduncs.module.modules.crystal.*;
import dlindustries.uk.daduncs.module.setting.bind;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;

public final class CrystalInputTest {
    public static int heldKey = -1;

    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        BlockPos pos = ctx.computeOnClient(c -> c.player.getBlockPos().south(2));
        var modules = System.INSTANCE.getModuleManager();
        var shield = modules.getModule(UtilitySwapper.class);
        var crystal = modules.getModule(AutoCrystal.class);
        var anchor = modules.getModule(DoubleAnchor.class);
        var key = (bind) shield.getSettings().stream()
                .filter(s -> s.getName().toString().equals("Shield Activation")).findFirst().orElseThrow();
        int originalKey = key.getKey();
        List<String> trace = new ArrayList<>();
        List<Integer> sequences = new ArrayList<>();
        PacketSendListener recorder = event -> {
            var packet = event.packet;
            if (packet instanceof PlayerInteractBlockC2SPacket p) {
                sequences.add(p.getSequence());
                var h = p.getBlockHitResult();
                trace.add("block " + p.getHand() + " " + h.getBlockPos() + " " + h.getSide() + " " + h.getPos()
                        + " " + h.isInsideBlock() + " " + h.isAgainstWorldBorder());
            } else if (packet instanceof HandSwingC2SPacket p) trace.add("swing " + p.getHand());
            else if (packet instanceof PlayerInteractItemC2SPacket p) trace.add("item " + p.getHand());
            else if (packet instanceof PlayerInteractEntityC2SPacket p) {
                int id = ((PlayerInteractEntityC2SPacketAccessor)p).getEntityId();
                var entity = MinecraftClient.getInstance().world.getEntityById(id);
                p.handle(new PlayerInteractEntityC2SPacket.Handler() {
                    public void attack() { trace.add("attack " + (entity instanceof EndCrystalEntity)); }
                    public void interact(Hand hand) { trace.add("interact " + hand); }
                    public void interactAt(Hand hand, Vec3d point) { trace.add("interact at " + hand); }
                });
            }
        };
        ctx.runOnClient(c -> System.INSTANCE.getEventManager().add(PacketSendListener.class, recorder));
        try {
            setup(ctx, world, pos, Blocks.CHEST.getDefaultState(), Items.STICK, Items.SHIELD);
            ctx.runOnClient(c -> press(c.options.useKey));
            ctx.waitFor(c -> c.currentScreen instanceof net.minecraft.client.gui.screen.ingame.GenericContainerScreen);
            ctx.runOnClient(c -> c.player.closeHandledScreen());
            ctx.waitTicks(5);
            ctx.runOnClient(c -> {
                key.setKey(GLFW.GLFW_KEY_V);
                shield.setEnabled(true);
                heldKey = GLFW.GLFW_KEY_V;
            });
            ctx.waitFor(c -> c.currentScreen instanceof net.minecraft.client.gui.screen.ingame.GenericContainerScreen);
            ctx.runOnClient(c -> {
                if (c.player.isUsingItem()) throw new AssertionError("Shield overrode the chest");
                heldKey = -1;
                c.player.closeHandledScreen();
                c.player.setPitch(-90);
                heldKey = GLFW.GLFW_KEY_V;
            });
            ctx.waitFor(c -> c.player.isUsingItem() && c.player.getActiveHand() == Hand.OFF_HAND);
            ctx.runOnClient(c -> { heldKey = -1; shield.setEnabled(false); });
            ctx.waitFor(c -> !c.player.isUsingItem());

            setup(ctx, world, pos, Blocks.AIR.getDefaultState(), Items.SPYGLASS, Items.SHIELD);
            ctx.runOnClient(c -> {
                shield.setEnabled(true);
                heldKey = GLFW.GLFW_KEY_V;
            });
            ctx.waitFor(c -> c.player.isUsingItem() && c.player.getActiveItem().isOf(Items.SPYGLASS));
            ctx.waitTicks(5);
            ctx.runOnClient(c -> {
                if (!c.player.isUsingItem()) throw new AssertionError("Main-hand use ended while the bind was held");
                heldKey = -1;
                shield.setEnabled(false);
            });
            ctx.waitFor(c -> !c.player.isUsingItem());

            for (boolean doubleAnchor : List.of(false, true)) {
                List<String> reference = null;
                for (boolean assisted : List.of(false, true)) {
                    var block = doubleAnchor ? Blocks.RESPAWN_ANCHOR.getDefaultState().with(RespawnAnchorBlock.CHARGES, 1)
                            : Blocks.OBSIDIAN.getDefaultState();
                    setup(ctx, world, pos, block, doubleAnchor ? Items.RESPAWN_ANCHOR : Items.END_CRYSTAL, Items.AIR);
                    ctx.runOnClient(c -> {
                        trace.clear();
                        sequences.clear();
                        if (assisted) {
                            (doubleAnchor ? anchor : crystal).setEnabled(true);
                            heldKey = GLFW.GLFW_MOUSE_BUTTON_RIGHT;
                        } else {
                            press(c.options.useKey);
                            if (doubleAnchor) press(c.options.useKey);
                        }
                    });
                    ctx.waitTick();
                    if (doubleAnchor) {
                        ctx.runOnClient(c -> {
                            heldKey = -1;
                            if (sequences.size() != 2 || sequences.get(1) != sequences.get(0) + 1)
                                throw new AssertionError("Anchor sequence numbers " + sequences);
                            if (trace.stream().filter(s -> s.startsWith("block ")).count() != 2)
                                throw new AssertionError("Expected two anchor clicks in one tick: " + trace);
                        });
                        ctx.waitFor(c -> c.world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR)
                                && c.world.getBlockState(pos).get(RespawnAnchorBlock.CHARGES) == 0);
                    } else {
                        ctx.runOnClient(c -> aim(c, Vec3d.ofCenter(pos).add(0, 1, 0)));
                        ctx.waitFor(c -> c.crosshairTarget instanceof EntityHitResult h && h.getEntity() instanceof EndCrystalEntity);
                        ctx.runOnClient(c -> { if (!assisted) press(c.options.attackKey); });
                        ctx.waitFor(c -> trace.contains("attack true"));
                        ctx.runOnClient(c -> { heldKey = -1; if (assisted) crystal.setEnabled(false); });
                        ctx.waitFor(c -> c.world.getEntitiesByClass(EndCrystalEntity.class, new Box(pos).expand(2), e -> true).isEmpty());
                    }
                    world.getServer().runOnServer(server -> {
                        var level = server.getOverworld();
                        if (doubleAnchor && !level.getBlockState(pos).equals(Blocks.RESPAWN_ANCHOR.getDefaultState()))
                            throw new AssertionError("Server did not replace the charged anchor");
                        if (!doubleAnchor && !level.getEntitiesByClass(EndCrystalEntity.class, new Box(pos).expand(2), e -> true).isEmpty())
                            throw new AssertionError("Crystal still on server after attack");
                    });
                    // Include delayed client responses to the integrated server's action results.
                    ctx.waitTicks(10);
                    var result = ctx.computeOnClient(c -> List.copyOf(trace));
                    if (assisted && !result.equals(reference)) throw new AssertionError("Expected " + reference + " got " + result);
                    reference = result;
                    ctx.runOnClient(c -> { if (anchor.isEnabled()) anchor.setEnabled(false); });
                }
            }
        } finally {
            ctx.runOnClient(c -> {
                heldKey = -1;
                key.setKey(originalKey);
                for (var module : List.of(shield, crystal, anchor)) if (module.isEnabled()) module.setEnabled(false);
                System.INSTANCE.getEventManager().remove(PacketSendListener.class, recorder);
            });
        }
    }

    private static void setup(ClientGameTestContext ctx, TestSingleplayerContext world, BlockPos pos, BlockState state, Item main, Item off) {
        world.getServer().runOnServer(s -> {
            var p = s.getPlayerManager().getPlayerList().getFirst();
            p.changeGameMode(main == Items.STICK ? GameMode.SURVIVAL : GameMode.CREATIVE);
            for (var floor : BlockPos.iterate(pos.add(-3, -1, -4), pos.add(3, -1, 2))) {
                p.getEntityWorld().setBlockState(floor, Blocks.OBSIDIAN.getDefaultState());
            }
            p.teleport(p.getEntityWorld(), pos.getX() + 0.5, pos.getY(), pos.getZ() - 1.5, Set.of(), 0, 0, false);
            p.setVelocity(Vec3d.ZERO);
            p.getEntityWorld().setBlockState(pos, state);
            p.setStackInHand(Hand.MAIN_HAND, main.getDefaultStack());
            p.setStackInHand(Hand.OFF_HAND, off.getDefaultStack());
            p.playerScreenHandler.sendContentUpdates();
        });
        ctx.waitFor(c -> c.world.getBlockState(pos).equals(state) && c.player.getMainHandStack().isOf(main)
                && c.player.getOffHandStack().getItem() == off);
        if (state.isAir()) {
            ctx.runOnClient(c -> c.player.setPitch(-90));
            ctx.waitTicks(4);
            return;
        }
        ctx.waitFor(c -> c.player.getEntityPos().squaredDistanceTo(pos.getX() + 0.5, pos.getY(), pos.getZ() - 1.5) < 0.001);
        ctx.waitTicks(4);
        ctx.waitFor(c -> {
            aim(c, Vec3d.ofCenter(pos));
            return c.crosshairTarget instanceof BlockHitResult h && h.getType() == HitResult.Type.BLOCK && h.getBlockPos().equals(pos);
        });
        ctx.waitTick();
    }
    private static void aim(MinecraftClient c, Vec3d target) {
        var delta = target.subtract(c.player.getEyePos());
        c.player.setYaw((float)Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        c.player.setPitch((float)-Math.toDegrees(Math.atan2(delta.y, delta.horizontalLength())));
        c.player.lastYaw = c.player.getYaw();
        c.player.lastPitch = c.player.getPitch();
        c.gameRenderer.updateCrosshairTarget(1);
    }
    private static void press(KeyBinding key) { KeyBinding.onKeyPressed(((KeyBindingAccessor)key).getBoundKey()); }
}
