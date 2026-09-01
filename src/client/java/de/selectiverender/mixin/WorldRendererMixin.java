package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import de.selectiverender.VirtualSkyLightSampler;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.block.BlockState;
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
        int virtualLight = VirtualSkyLightSampler.sample(clientWorld, pos);
        if (virtualLight < 0) return;
        cir.setReturnValue(LightmapTextureManager.pack(
                Math.max(LightmapTextureManager.getSkyLightCoordinates(light), virtualLight),
                LightmapTextureManager.getBlockLightCoordinates(light)));
    }

    @Inject(method = "updateBlock", at = @At("HEAD"))
    private void selectiverender$invalidateLightColumn(BlockView world, BlockPos pos,
                                                        BlockState oldState, BlockState newState,
                                                        int flags, CallbackInfo ci) {
        if (oldState != newState) {
            SelectiveRenderState.invalidateVirtualSkyLight(pos.getX(), pos.getY(), pos.getZ());
        }
        if (oldState.getOpacity(world, pos) != newState.getOpacity(world, pos)) {
            SelectiveRenderState.invalidateVisibleOccluder(pos.getX(), pos.getZ());
        }
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
