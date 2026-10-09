package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import de.selectiverender.VirtualSkyLightSampler;
import de.selectiverender.LightingDiagnostics;
import de.selectiverender.PerformanceDiagnostics;
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
            int vanillaSky = cir.getReturnValueI();
            long started = PerformanceDiagnostics.startTimer();
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
            PerformanceDiagnostics.finish(PerformanceDiagnostics.Metric.ENTITY_LIGHT,
                    started, 1, pos.getX(), pos.getY(), pos.getZ());
        }
    }
}
