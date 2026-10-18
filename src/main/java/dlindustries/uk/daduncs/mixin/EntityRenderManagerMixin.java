package dlindustries.uk.daduncs.mixin;

import dlindustries.uk.daduncs.module.modules.render.Nametags;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderManager.class)
public class EntityRenderManagerMixin {

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void hideServerTag(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (Nametags.hidesServerTag(entity)) cir.setReturnValue(false);
	}
}
