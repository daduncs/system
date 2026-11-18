package dlindustries.uk.daduncs.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dlindustries.uk.daduncs.managers.InputContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @WrapOperation(method = "onEntityAnimation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/LivingEntity;swingHand(Lnet/minecraft/util/Hand;)V"))
    private void applyServerSwing(LivingEntity entity, Hand hand, Operation<Void> original) {
        if (entity == MinecraftClient.getInstance().player)
            InputContext.serverSwing(hand, () -> original.call(entity, hand));
        else original.call(entity, hand);
    }
}
