package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
abstract class WorldRendererMixin {
    @Inject(method = "addBuiltChunk", at = @At("HEAD"), cancellable = true)
    private void selectiverender$filterTerrain(ChunkBuilder.BuiltChunk chunk, CallbackInfo ci) {
        if (!SelectiveRenderState.shouldRenderChunk(
                chunk.getOrigin().getX() >> 4, chunk.getOrigin().getZ() >> 4)) ci.cancel();
    }

    @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true)
    private void selectiverender$filterEntity(Entity entity, double cameraX, double cameraY, double cameraZ,
                                         float tickDelta, MatrixStack matrices,
                                         VertexConsumerProvider consumers, CallbackInfo ci) {
        if (!SelectiveRenderState.shouldRender(entity)) ci.cancel();
    }
}
