package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldRenderer.class)
abstract class WorldRendererMixin {
    @Inject(method = "isRenderingReady", at = @At("HEAD"), cancellable = true)
    private void selectiverender$skipFilteredTerrainReadiness(BlockPos pos,
                                                              CallbackInfoReturnable<Boolean> cir) {
        if (SelectiveRenderState.enabled()) cir.setReturnValue(true);
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
