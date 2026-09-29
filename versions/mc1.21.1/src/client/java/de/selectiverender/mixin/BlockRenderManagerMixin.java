package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockRenderManager.class)
abstract class BlockRenderManagerMixin {
    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
    private void selectiverender$filterBlock(BlockState state, BlockPos pos, BlockRenderView world,
                                             MatrixStack matrices, VertexConsumer vertexConsumer,
                                             boolean cull, Random random, CallbackInfo ci) {
        if (!SelectiveRenderState.shouldRender(pos)) ci.cancel();
    }

    @Inject(method = "renderFluid", at = @At("HEAD"), cancellable = true)
    private void selectiverender$filterFluid(BlockPos pos, BlockRenderView world,
                                             VertexConsumer vertexConsumer, BlockState blockState,
                                             FluidState fluidState, CallbackInfo ci) {
        if (!SelectiveRenderState.shouldRender(pos)) ci.cancel();
    }
}
