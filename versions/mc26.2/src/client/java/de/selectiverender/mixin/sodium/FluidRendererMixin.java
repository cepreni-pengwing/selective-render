package de.selectiverender.mixin.sodium;

import de.selectiverender.SelectiveRenderState;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.caffeinemc.mods.sodium.fabric.render.FluidRendererImpl;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = FluidRendererImpl.class, remap = false)
abstract class FluidRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void selectiverender$filterFluid(LevelSlice world, net.minecraft.world.level.block.state.BlockState state, FluidState fluidState,
                                             BlockPos pos, BlockPos offset,
                                             net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector collector,
                                             ChunkBuildBuffers buffers, CallbackInfo ci) {
        if (!SelectiveRenderState.shouldRender(pos)) ci.cancel();
    }
}
