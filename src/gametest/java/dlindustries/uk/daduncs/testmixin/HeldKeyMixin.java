package dlindustries.uk.daduncs.testmixin;

import dlindustries.uk.daduncs.CrystalInputTest;
import dlindustries.uk.daduncs.utils.KeyUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = KeyUtils.class, remap = false)
public class HeldKeyMixin {
    @Inject(method = "isKeyPressed", at = @At("HEAD"), cancellable = true)
    private static void supplyHeldKey(int key, CallbackInfoReturnable<Boolean> result) {
        if (key >= 0 && key == CrystalInputTest.heldKey) result.setReturnValue(true);
    }
}
