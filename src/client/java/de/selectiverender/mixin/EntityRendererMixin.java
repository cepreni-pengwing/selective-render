package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import de.selectiverender.VirtualSkyLightSampler;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin<T extends Entity> {
    @Inject(method = "getSkyLight", at = @At("RETURN"), cancellable = true)
    private void selectiverender$applyVirtualEntitySkyLight(T entity, BlockPos pos,
                                                            CallbackInfoReturnable<Integer> cir) {
        if ((!SelectiveRenderState.enabled() && !SelectiveRenderState.hideEnabled())
                || cir.getReturnValueI() >= 15) {
            return;
        }
        if (entity.getWorld() instanceof net.minecraft.client.world.ClientWorld clientWorld) {
            int virtualLight = VirtualSkyLightSampler.sample(clientWorld, pos);
            if (virtualLight >= 0) cir.setReturnValue(Math.max(cir.getReturnValueI(), virtualLight));
        }
    }
}
