package de.selectiverender.mixin.canvasblocks;

import de.selectiverender.SelectiveRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.canvasblocks.client.render.WorldPaintingRenderer", remap = false)
public abstract class WorldPaintingRendererMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/canvasblocks/painting/PaintingRecord;usesEntity()Z"), require = 0)
    private static boolean selectiverender$skipHiddenPainting(@Coerce Object candidate) {
        PaintingRecordAccess painting = (PaintingRecordAccess) candidate;
        return painting.selectiverender$usesEntity()
                || !SelectiveRenderState.shouldRender(painting.selectiverender$origin());
    }
}
