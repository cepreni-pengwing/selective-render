package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelExtractor.class)
abstract class WorldRendererMixin {
    @Inject(method = "setBlockDirty", at = @At("HEAD"))
    private void selectiverender$invalidateLightColumn(BlockPos pos,
            BlockState oldState, BlockState newState, CallbackInfo ci) {
        if (!SelectiveRenderState.filteringActive()) return;
        if (oldState != newState) {
            SelectiveRenderState.invalidateVirtualSkyLight(pos.getX(), pos.getY(), pos.getZ());
        }
        if (oldState.getLightDampening() != newState.getLightDampening()) {
            SelectiveRenderState.invalidateVisibleOccluder(pos.getX(), pos.getZ());
        }
    }

    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void selectiverender$filterEntity(Entity entity, Frustum frustum,
            double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (!SelectiveRenderState.shouldRender(entity)) cir.setReturnValue(false);
    }
}
