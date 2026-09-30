package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import de.selectiverender.VirtualSkyLightSampler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Particle.class)
abstract class ParticleLightMixin {
    @Shadow protected ClientLevel level;
    @Shadow protected double x;
    @Shadow protected double y;
    @Shadow protected double z;

    @Inject(method = "getLightCoords", at = @At("RETURN"), cancellable = true)
    private void selectiverender$applyNearbyVirtualLight(float tickDelta,
                                                         CallbackInfoReturnable<Integer> cir) {
        if (!SelectiveRenderState.filteringActive()) return;
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!SelectiveRenderState.shouldRender(pos)) return;

        int packed = cir.getReturnValueI();
        int sky = LightCoordsUtil.sky(packed);
        int virtual = VirtualSkyLightSampler.sample(level, pos);
        if (virtual <= sky) {
            for (Direction direction : Direction.values()) {
                int nearby = VirtualSkyLightSampler.sample(level, pos.relative(direction));
                if (nearby > virtual) virtual = nearby;
            }
        }
        if (virtual > sky) {
            cir.setReturnValue(LightCoordsUtil.pack(LightCoordsUtil.block(packed), virtual));
        }
    }
}
