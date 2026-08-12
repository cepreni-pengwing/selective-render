package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RaycastContext.class)
abstract class RaycastContextMixin {
    @Inject(method = "getBlockShape", at = @At("HEAD"), cancellable = true)
    private void selectiverender$skipInvisibleBlock(BlockState state, BlockView world, BlockPos pos,
                                                     CallbackInfoReturnable<VoxelShape> cir) {
        if (!SelectiveRenderState.shouldRender(pos)) cir.setReturnValue(VoxelShapes.empty());
    }

    @Inject(method = "getFluidShape", at = @At("HEAD"), cancellable = true)
    private void selectiverender$skipInvisibleFluid(net.minecraft.fluid.FluidState state,
                                                     BlockView world, BlockPos pos,
                                                     CallbackInfoReturnable<VoxelShape> cir) {
        if (!SelectiveRenderState.shouldRender(pos)) cir.setReturnValue(VoxelShapes.empty());
    }
}
