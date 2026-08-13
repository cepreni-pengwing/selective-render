package de.selectiverender.mixin.indium;

import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "link.infra.indium.renderer.render.TerrainRenderContext", remap = false)
abstract class TerrainRenderContextMixin {
    @Inject(method = "tessellateBlock", at = @At("HEAD"), require = 0)
    private void selectiverender$beginBlock(BlockRenderContext context, CallbackInfo ci) {
        IndiumRenderContext.begin(context.pos());
    }

    @Inject(method = "tessellateBlock", at = @At("RETURN"), require = 0)
    private void selectiverender$endBlock(BlockRenderContext context, CallbackInfo ci) {
        IndiumRenderContext.end();
    }
}
