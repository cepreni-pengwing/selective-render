package de.selectiverender.mixin.sodium;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.block.BlockState;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.FluidRenderer;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = FluidRenderer.class, remap = false)
abstract class FluidRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void selectiverender$filterFluid(LevelSlice world, FluidState fluidState,
                                             BlockPos pos, BlockPos offset,
                                             ChunkBuildBuffers buffers, CallbackInfo ci) {
        BlockState state = world.getBlockState(pos);
        if (!SelectiveRenderState.shouldRender(state, pos.getX(), pos.getY(), pos.getZ())) ci.cancel();
    }
}
