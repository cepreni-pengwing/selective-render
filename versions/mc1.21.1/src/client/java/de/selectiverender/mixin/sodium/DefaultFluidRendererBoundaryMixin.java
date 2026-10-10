package de.selectiverender.mixin.sodium;

import de.selectiverender.FluidBoundaryPolicy;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer",
        remap = false)
abstract class DefaultFluidRendererBoundaryMixin {
    @Inject(method = "isSideExposed", at = @At("RETURN"), cancellable = true)
    private void selectiverender$cullWaterBoundarySide(BlockRenderView world,
            int x, int y, int z, Direction face, float height,
            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        BlockPos position = new BlockPos(x, y, z);
        FluidState fluidState = world.getFluidState(position);
        if (FluidBoundaryPolicy.shouldCull(world, position, fluidState, face)) {
            cir.setReturnValue(false);
        }
    }
}
