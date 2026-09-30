package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import de.selectiverender.VirtualSkyLightSampler;
import net.minecraft.block.BlockState;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
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
    private void selectiverender$applyVirtualParticleLight(float tickDelta,
                                                           CallbackInfoReturnable<Integer> cir) {
        if (!SelectiveRenderState.filteringActive()) return;
        BlockPos pos = BlockPos.ofFloored(x, y, z);
        if (!SelectiveRenderState.shouldRender(pos)) return;
        if (!world.isChunkLoaded(pos)) return;

        int packed = cir.getReturnValueI();
        int sky = LightmapTextureManager.getSkyLightCoordinates(packed);
        int virtual = VirtualSkyLightSampler.sample(world, pos);
        if (SelectiveRenderState.shouldSeedVirtualSkyColumn(true)
                && SelectiveRenderState.highestVisibleOccluder(
                world, pos.getX(), pos.getZ()) <= pos.getY()) {
            virtual = 15;
        }
        if (virtual <= sky) {
            for (Direction direction : Direction.values()) {
                virtual = Math.max(virtual,
                        VirtualSkyLightSampler.sample(world, pos.offset(direction)));
            }
        }

        BlockState state = world.getBlockState(pos);
        int block = Math.max(world.getLightLevel(LightType.BLOCK, pos), state.getLuminance());
        cir.setReturnValue(LightmapTextureManager.pack(Math.max(sky, virtual), block));
    }
}
