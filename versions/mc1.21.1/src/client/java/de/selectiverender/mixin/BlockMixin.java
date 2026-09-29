package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import de.selectiverender.SelectiveRenderSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
abstract class BlockMixin {
    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private static void selectiverender$exposeBoundaryFace(BlockState state, BlockView world,
                                                           BlockPos pos, Direction direction,
                                                           BlockPos neighborPos,
                                                           CallbackInfoReturnable<Boolean> cir) {
        if (!SelectiveRenderState.filteringActive()) return;
        if (SelectiveRenderState.shouldRender(pos) && !SelectiveRenderState.shouldRender(neighborPos)) {
            cir.setReturnValue(SelectiveRenderState.boundaryModeForFace(pos, direction)
                    != SelectiveRenderSettings.BoundaryMode.CULLED);
        }
    }
}
