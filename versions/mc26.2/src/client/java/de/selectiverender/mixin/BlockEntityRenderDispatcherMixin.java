package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "tryExtractRenderState",
            at = @At("HEAD"), cancellable = true)
    private void selectiverender$filterBlockEntity(BlockEntity blockEntity, float tickDelta,
            ModelFeatureRenderer.CrumblingOverlay overlay, boolean renderOutline,
            CallbackInfoReturnable<BlockEntityRenderState> cir) {
        if (!SelectiveRenderState.shouldRender(blockEntity.getBlockPos())) cir.setReturnValue(null);
    }

}
