package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import de.selectiverender.PerformanceDiagnostics;
import de.selectiverender.VirtualSkyLightSampler;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldRenderer.class)
abstract class WorldRendererMixin {
    @Inject(method = "getLightmapCoordinates(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;)I",
            at = @At("RETURN"), cancellable = true)
    private static void selectiverender$applyVirtualLight(BlockRenderView world, BlockPos pos,
                                                           CallbackInfoReturnable<Integer> cir) {
        int light = cir.getReturnValueI();
        if (LightmapTextureManager.getSkyLightCoordinates(light) >= 15
                || !(world instanceof ClientWorld clientWorld)
                || (!SelectiveRenderState.enabled() && !SelectiveRenderState.hideEnabled())
                || !SelectiveRenderState.shouldRender(pos)) return;
        long started = PerformanceDiagnostics.startTimer();
        int virtualLight = VirtualSkyLightSampler.sample(clientWorld, pos);
        PerformanceDiagnostics.finish(PerformanceDiagnostics.Metric.BLOCK_ENTITY_LIGHT,
                started, 1, pos.getX(), pos.getY(), pos.getZ());
        if (virtualLight < 0) return;
        cir.setReturnValue(LightmapTextureManager.pack(
                Math.max(LightmapTextureManager.getSkyLightCoordinates(light), virtualLight),
                LightmapTextureManager.getBlockLightCoordinates(light)));
    }

    @Inject(method = "updateBlock", at = @At("HEAD"))
    private void selectiverender$invalidateLightColumn(BlockView world, BlockPos pos,
                                                        BlockState oldState, BlockState newState,
                                                        int flags, CallbackInfo ci) {
        if (!SelectiveRenderState.filteringActive()) return;
        long updateStarted = PerformanceDiagnostics.startTimer();
        // Compare the states that virtual light actually sees, including ID/tag filters.
        BlockState oldVisible = SelectiveRenderState.shouldRender(oldState,
                pos.getX(), pos.getY(), pos.getZ()) ? oldState : Blocks.AIR.getDefaultState();
        BlockState newVisible = SelectiveRenderState.shouldRender(newState,
                pos.getX(), pos.getY(), pos.getZ()) ? newState : Blocks.AIR.getDefaultState();
        boolean oldShaped = oldState.isOpaque() && oldState.hasSidedTransparency();
        boolean newShaped = newState.isOpaque() && newState.hasSidedTransparency();
        // Raw light changes matter too: a previously cached vanilla fast path
        // must stop being used when a new roof is placed outside the region.
        boolean changed = oldState.getOpacity(world, pos) != newState.getOpacity(world, pos)
                || oldVisible.getOpacity(world, pos) != newVisible.getOpacity(world, pos)
                || (oldVisible == oldState) != (newVisible == newState)
                || oldShaped != newShaped;
        if (!changed && (oldShaped || newShaped)) {
            // Context-dependent shapes must remain conservative. Static shapes can
            // reuse light across material changes, but never across slab/shape changes.
            changed = oldState.getBlock().hasDynamicBounds()
                    || newState.getBlock().hasDynamicBounds()
                    || VoxelShapes.matchesAnywhere(oldState.getCullingShape(world, pos),
                            newState.getCullingShape(world, pos), BooleanBiFunction.NOT_SAME);
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

    @Inject(method = "isRenderingReady", at = @At("HEAD"), cancellable = true)
    private void selectiverender$skipFilteredTerrainReadiness(BlockPos position,
                                                              CallbackInfoReturnable<Boolean> cir) {
        if (!SelectiveRenderState.shouldRenderSection(
                position.getX() >> 4,
                position.getY() >> 4,
                position.getZ() >> 4)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "addBuiltChunk", at = @At("HEAD"), cancellable = true)
    private void selectiverender$filterTerrain(ChunkBuilder.BuiltChunk chunk, CallbackInfo ci) {
        if (!SelectiveRenderState.shouldRenderSection(
                chunk.getOrigin().getX() >> 4,
                chunk.getOrigin().getY() >> 4,
                chunk.getOrigin().getZ() >> 4)) ci.cancel();
    }

    @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true)
    private void selectiverender$filterEntity(Entity entity, double cameraX, double cameraY, double cameraZ,
                                         float tickDelta, MatrixStack matrices,
                                         VertexConsumerProvider consumers, CallbackInfo ci) {
        if (!SelectiveRenderState.shouldRender(entity)) ci.cancel();
    }
}
