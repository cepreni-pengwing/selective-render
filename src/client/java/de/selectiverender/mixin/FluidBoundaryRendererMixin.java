package de.selectiverender.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import de.selectiverender.FluidBoundaryPolicy;
import net.minecraft.client.render.block.FluidRenderer;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidRenderer.class)
abstract class FluidBoundaryRendererMixin {
    @Inject(method = "shouldRenderSide", at = @At("RETURN"), cancellable = true)
    private static void selectiverender$cullWaterBoundarySide(
            BlockRenderView world, BlockPos position, FluidState fluidState, BlockState blockState,
            Direction face, FluidState neighborFluid, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && FluidBoundaryPolicy.shouldCull(world, position, fluidState, face)) {
            cir.setReturnValue(false);
        }
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/block/FluidRenderer;isSameFluid(Lnet/minecraft/fluid/FluidState;Lnet/minecraft/fluid/FluidState;)Z"))
    private boolean selectiverender$cullWaterBoundaryTop(boolean sameFluid,
            @Local(argsOnly = true) BlockRenderView world,
            @Local(argsOnly = true) BlockPos position,
            @Local(argsOnly = true) FluidState fluidState) {
        return sameFluid || FluidBoundaryPolicy.shouldCull(world, position, fluidState, Direction.UP);
    }
}
