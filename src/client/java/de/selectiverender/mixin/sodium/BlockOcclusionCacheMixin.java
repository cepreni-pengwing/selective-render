package de.selectiverender.mixin.sodium;

import de.selectiverender.SelectiveRenderState;
import de.selectiverender.SelectiveRenderSettings;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = BlockOcclusionCache.class, remap = false)
abstract class BlockOcclusionCacheMixin {
    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private void selectiverender$exposeBoundaryFace(BlockState state, BlockView world,
                                                    BlockPos pos, Direction direction,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (SelectiveRenderState.shouldRender(pos)
                && !SelectiveRenderState.shouldRender(pos.offset(direction))) {
            cir.setReturnValue(SelectiveRenderSettings.boundaryMode()
                    != SelectiveRenderSettings.BoundaryMode.CULLED);
        }
    }
}
