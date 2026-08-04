package de.plotrender.mixin;

import de.plotrender.PlotRenderState;
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
    /** Stops excluded sections before Vanilla builds its terrain render lists. */
    @Inject(method = "addBuiltChunk", at = @At("HEAD"), cancellable = true)
    private void plotrender$filterTerrain(ChunkBuilder.BuiltChunk chunk, CallbackInfo ci) {
        if (!PlotRenderState.shouldRender(chunk.getOrigin())) ci.cancel();
    }

    /** Players are deliberately exempt from the entity filter. */
    @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true)
    private void plotrender$filterEntity(Entity entity, double cameraX, double cameraY, double cameraZ,
                                         float tickDelta, MatrixStack matrices,
                                         VertexConsumerProvider consumers, CallbackInfo ci) {
        if (!PlotRenderState.shouldRender(entity)) ci.cancel();
    }
}
