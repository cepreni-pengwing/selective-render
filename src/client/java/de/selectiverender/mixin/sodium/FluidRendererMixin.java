package de.selectiverender.mixin.sodium;

import de.selectiverender.SelectiveRenderState;
import de.selectiverender.FluidBoundaryPolicy;
import net.minecraft.block.BlockState;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.FluidRenderer;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = FluidRenderer.class, remap = false)
abstract class FluidRendererMixin {
    @Inject(method = "isSideExposed", at = @At("RETURN"), cancellable = true)
    private void selectiverender$cullFluidBoundarySide(BlockRenderView world,
            int x, int y, int z, Direction face, float height,
            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        BlockPos position = new BlockPos(x, y, z);
        FluidState fluidState = world.getFluidState(position);
        if (FluidBoundaryPolicy.shouldCull(world, position, fluidState, face)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void selectiverender$filterFluid(WorldSlice world, FluidState fluidState,
                                             BlockPos pos, BlockPos offset,
                                             ChunkBuildBuffers buffers, CallbackInfo ci) {
        BlockState state = world.getBlockState(pos);
        if (!SelectiveRenderState.shouldRender(state, pos.getX(), pos.getY(), pos.getZ())) ci.cancel();
    }
}
