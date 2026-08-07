package de.selectiverender.mixin.sodium;

import de.selectiverender.SelectiveRenderState;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = BlockRenderer.class, remap = false)
abstract class BlockRendererMixin {
    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true)
    private void selectiverender$filterBlock(BlockRenderContext context,
                                             ChunkBuildBuffers buffers, CallbackInfo ci) {
        if (!SelectiveRenderState.shouldRender(context.pos())) ci.cancel();
    }
}
