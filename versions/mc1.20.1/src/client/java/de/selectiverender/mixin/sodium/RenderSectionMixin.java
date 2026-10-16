package de.selectiverender.mixin.sodium;

import de.selectiverender.SelectiveRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.RenderSection", remap = false)
abstract class RenderSectionMixin {
    @Inject(method = "getVisibilityData", at = @At("HEAD"), cancellable = true)
    private void selectiverender$makeFilteredSectionTransparent(CallbackInfoReturnable<Long> cir) {
        me.jellysquid.mods.sodium.client.render.chunk.RenderSection section =
                (me.jellysquid.mods.sodium.client.render.chunk.RenderSection) (Object) this;
        if (!SelectiveRenderState.shouldRenderSection(
                section.getChunkX(), section.getChunkY(), section.getChunkZ())) {
            cir.setReturnValue(-1L);
        }
    }
}
