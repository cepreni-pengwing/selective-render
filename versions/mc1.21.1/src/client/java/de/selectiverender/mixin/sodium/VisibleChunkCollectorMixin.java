package de.selectiverender.mixin.sodium;

import de.selectiverender.SelectiveRenderState;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.lists.SectionCollector", remap = false)
abstract class VisibleChunkCollectorMixin {
    @Inject(method = "visit", at = @At("HEAD"), cancellable = true)
    private void selectiverender$filterSection(RenderSection section, CallbackInfo ci) {
        if (!SelectiveRenderState.shouldRenderSection(
                section.getChunkX(), section.getChunkY(), section.getChunkZ())) ci.cancel();
    }
}
