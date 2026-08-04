package de.plotrender.mixin.sodium;

import de.plotrender.PlotRenderState;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium normally walks its section graph using visibility data from already
 * built sections. Excluded sections deliberately have no rebuild work, so they
 * must not be allowed to occlusion-block traversal from a camera outside the
 * selected plot to sections inside it.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
abstract class RenderSectionManagerMixin {
    @Inject(method = "shouldUseOcclusionCulling", at = @At("HEAD"), cancellable = true)
    private void plotrender$keepWhitelistReachable(Camera camera, boolean spectator,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (PlotRenderState.enabled()) {
            cir.setReturnValue(false);
        }
    }
}
