package de.selectiverender.mixin;
import de.selectiverender.SelectiveRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(EntityRenderDispatcher.class)
abstract class EntityVisibilityMixin {
    @Inject(method="shouldRender",at=@At("HEAD"),cancellable=true)
    private void selectiverender$filter(Entity entity,Frustum frustum,double x,double y,double z,CallbackInfoReturnable<Boolean> cir) {
        if(!SelectiveRenderState.shouldRender(entity)) cir.setReturnValue(false);
    }
}
