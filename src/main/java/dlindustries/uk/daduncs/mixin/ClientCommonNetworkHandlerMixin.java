package dlindustries.uk.daduncs.mixin;

import dlindustries.uk.daduncs.System;
import dlindustries.uk.daduncs.module.modules.misc.PackSpoof;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonNetworkHandler.class)
public class ClientCommonNetworkHandlerMixin {
    @Shadow @Final protected ClientConnection connection;

    // Vanilla has moved onto the client thread and rejected invalid URLs at this point.
    @Inject(method = "onResourcePackSend", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/packet/s2c/common/ResourcePackSendS2CPacket;hash()Ljava/lang/String;"),
            cancellable = true)
    private void declineServerPack(ResourcePackSendS2CPacket packet, CallbackInfo ci) {
        if (System.INSTANCE == null || System.INSTANCE.getModuleManager() == null) return;
        var module = System.INSTANCE.getModuleManager().getModule(PackSpoof.class);
        if (module == null || !module.isEnabled()) return;
        if (packet.required()) {
            connection.disconnect(Text.translatable("multiplayer.requiredTexturePrompt.disconnect"));
        } else {
            connection.send(new ResourcePackStatusC2SPacket(packet.id(), ResourcePackStatusC2SPacket.Status.DECLINED));
        }
        ci.cancel();
    }
}
