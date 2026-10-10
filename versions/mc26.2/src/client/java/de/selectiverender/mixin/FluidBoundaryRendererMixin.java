package de.selectiverender.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import de.selectiverender.FluidBoundaryPolicy;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(FluidRenderer.class)
abstract class FluidBoundaryRendererMixin {
    @ModifyArgs(method = "tesselate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/FluidRenderer;shouldRenderFace(Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/material/FluidState;)Z"))
    private void selectiverender$cullWaterBoundarySide(Args args,
            @Local(argsOnly = true) BlockAndTintGetter world,
            @Local(argsOnly = true) BlockPos position,
            @Local(argsOnly = true) FluidState fluidState) {
        Direction face = args.get(2);
        if (FluidBoundaryPolicy.shouldCull(world, position, fluidState, face)) {
            args.set(3, fluidState);
        }
    }

    @ModifyExpressionValue(method = "tesselate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/FluidRenderer;isNeighborSameFluid(Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/world/level/material/FluidState;)Z"))
    private boolean selectiverender$cullWaterBoundaryTop(boolean sameFluid,
            @Local(argsOnly = true) BlockAndTintGetter world,
            @Local(argsOnly = true) BlockPos position,
            @Local(argsOnly = true) FluidState fluidState) {
        return sameFluid || FluidBoundaryPolicy.shouldCull(world, position, fluidState, Direction.UP);
    }
}
