package de.selectiverender.mixin.sodium;

import de.selectiverender.SelectiveRenderState;
import de.selectiverender.SelectiveRenderSettings;
import net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractBlockRenderContext.class, remap = false)
abstract class BlockOcclusionCacheMixin {
    @Shadow protected BlockPos pos;

    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private void selectiverender$exposeBoundaryFace(Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!SelectiveRenderState.filteringActive()) return;
        if (SelectiveRenderState.shouldRender(pos)
                && !SelectiveRenderState.shouldRender(pos.relative(direction))) {
            cir.setReturnValue(SelectiveRenderState.boundaryModeForFace(pos, direction)
                    != SelectiveRenderSettings.BoundaryMode.CULLED);
        }
    }
}
