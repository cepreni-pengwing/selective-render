package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import de.selectiverender.PerformanceDiagnostics;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.BooleanOp;
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
        long updateStarted = PerformanceDiagnostics.startTimer();
        BlockState oldVisible = SelectiveRenderState.shouldRender(oldState,
                pos.getX(), pos.getY(), pos.getZ()) ? oldState : Blocks.AIR.defaultBlockState();
        BlockState newVisible = SelectiveRenderState.shouldRender(newState,
                pos.getX(), pos.getY(), pos.getZ()) ? newState : Blocks.AIR.defaultBlockState();
        boolean oldShaped = oldState.canOcclude() && oldState.useShapeForLightOcclusion();
        boolean newShaped = newState.canOcclude() && newState.useShapeForLightOcclusion();
        boolean changed = oldState.getLightDampening() != newState.getLightDampening()
                || oldVisible.getLightDampening() != newVisible.getLightDampening()
                || (oldVisible == oldState) != (newVisible == newState)
                || oldShaped != newShaped;
        if (!changed && (oldShaped || newShaped)) {
            changed = Shapes.joinIsNotEmpty(oldState.getOcclusionShape(),
                    newState.getOcclusionShape(), BooleanOp.NOT_SAME);
        }
        if (changed) {
            PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.OPTICAL_UPDATE, 1);
            SelectiveRenderState.invalidateVirtualSkyLight(pos.getX(), pos.getY(), pos.getZ());
            SelectiveRenderState.invalidateVisibleOccluder(pos.getX(), pos.getZ());
        } else {
            PerformanceDiagnostics.count(PerformanceDiagnostics.Metric.LIGHT_EQUIVALENT_UPDATE, 1);
        }
        PerformanceDiagnostics.blockUpdate(updateStarted, changed,
                pos.getX(), pos.getY(), pos.getZ(), oldState, newState);
    }

    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void selectiverender$filterEntity(Entity entity, Frustum frustum,
            double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (!SelectiveRenderState.shouldRender(entity)) cir.setReturnValue(false);
    }
}
