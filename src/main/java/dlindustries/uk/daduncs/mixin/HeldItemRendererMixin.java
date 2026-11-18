package dlindustries.uk.daduncs.mixin;

import dlindustries.uk.daduncs.module.modules.render.NoBounce;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
    @Inject(method = "resetEquipProgress", at = @At("HEAD"), cancellable = true)
    private void keepCrystalRaised(Hand hand, CallbackInfo ci) {
        var player = MinecraftClient.getInstance().player;
        if (player != null && player.getStackInHand(hand).isOf(Items.END_CRYSTAL) && NoBounce.active()) {
            ci.cancel();
        }
    }
}
