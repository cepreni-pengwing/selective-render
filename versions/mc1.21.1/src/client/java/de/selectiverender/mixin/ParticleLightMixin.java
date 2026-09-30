package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.block.BlockState;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Particle.class)
abstract class ParticleLightMixin {
    @Shadow protected ClientWorld world;
    @Shadow protected double x;
    @Shadow protected double y;
    @Shadow protected double z;

    @Inject(method = "getBrightness", at = @At("RETURN"), cancellable = true)
    private void selectiverender$useCurrentVanillaLight(float tickDelta,
                                                        CallbackInfoReturnable<Integer> cir) {
        if (!SelectiveRenderState.filteringActive()) return;
        BlockPos pos = BlockPos.ofFloored(x, y, z);
        if (!SelectiveRenderState.shouldRender(pos)) return;
        if (!world.isChunkLoaded(pos)) return;

        BlockState state = world.getBlockState(pos);
        int block = Math.max(world.getLightLevel(LightType.BLOCK, pos), state.getLuminance());
        int sky = world.getLightLevel(LightType.SKY, pos);
        cir.setReturnValue(LightmapTextureManager.pack(sky, block));
    }
}
