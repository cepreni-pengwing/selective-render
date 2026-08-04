package de.plotrender.mixin.sodium;

import de.plotrender.PlotRenderState;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Filters Sodium's terrain list before draw commands and rebuild queues are generated. */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.lists.VisibleChunkCollector", remap = false)
abstract class VisibleChunkCollectorMixin {
    @Inject(method = "visit", at = @At("HEAD"), cancellable = true)
    private void plotrender$filterSection(RenderSection section, boolean visible, CallbackInfo ci) {
        if (!PlotRenderState.shouldRenderChunk(section.getChunkX(), section.getChunkZ())) ci.cancel();
    }
}
