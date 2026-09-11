package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidRenderer.class)
abstract class BlockRenderManagerMixin {
    @Inject(method = "tesselate", at = @At("HEAD"), cancellable = true)
    private void selectiverender$filterFluid(BlockAndTintGetter world, BlockPos pos,
            FluidRenderer.Output output, BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        if (!SelectiveRenderState.shouldRender(pos)) ci.cancel();
    }
}
