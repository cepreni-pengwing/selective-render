package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import de.selectiverender.VirtualSkyLightSampler;
import de.selectiverender.LightingDiagnostics;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin<T extends Entity> {
    @Inject(method = "getSkyLightLevel", at = @At("RETURN"), cancellable = true)
    private void selectiverender$applyVirtualEntitySkyLight(T entity, BlockPos pos,
                                                            CallbackInfoReturnable<Integer> cir) {
        if ((!SelectiveRenderState.enabled() && !SelectiveRenderState.hideEnabled())
                || cir.getReturnValueI() >= 15) {
            return;
        }
        if (entity.level() instanceof net.minecraft.client.multiplayer.ClientLevel clientWorld) {
            int vanillaSky = cir.getReturnValueI();
            int virtualLight = VirtualSkyLightSampler.sample(clientWorld, pos);
            int centerVirtual = virtualLight;
            boolean directSky = SelectiveRenderState.shouldSeedVirtualSkyColumn(true)
                    && SelectiveRenderState.highestVisibleOccluder(
                    clientWorld, pos.getX(), pos.getZ()) <= pos.getY();
            if (directSky) {
                virtualLight = 15;
            }
            int finalSky = virtualLight < 0 ? vanillaSky : Math.max(vanillaSky, virtualLight);
            LightingDiagnostics.record(entity.getClass().getSimpleName(), pos, vanillaSky,
                    centerVirtual, virtualLight, directSky, finalSky);
            cir.setReturnValue(finalSky);
        }
    }
}
