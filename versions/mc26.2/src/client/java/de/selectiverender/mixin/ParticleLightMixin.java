package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
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
    private void selectiverender$useCurrentVanillaLight(float tickDelta,
                                                        CallbackInfoReturnable<Integer> cir) {
        if (!SelectiveRenderState.filteringActive()) return;
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!SelectiveRenderState.shouldRender(pos)) return;
        if (!level.hasChunkAt(pos)) return;

        BlockState state = level.getBlockState(pos);
        if (state.emissiveRendering()) {
            cir.setReturnValue(LightCoordsUtil.FULL_BRIGHT);
            return;
        }
        int block = Math.max(level.getBrightness(LightLayer.BLOCK, pos),
                state.getLightEmission());
        int sky = level.getBrightness(LightLayer.SKY, pos);
        cir.setReturnValue(LightCoordsUtil.pack(block, sky));
    }
}
