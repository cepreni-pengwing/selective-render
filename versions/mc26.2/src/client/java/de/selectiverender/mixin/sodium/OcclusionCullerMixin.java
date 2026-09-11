package de.selectiverender.mixin.sodium;

import de.selectiverender.SelectiveRenderState;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = OcclusionCuller.class, remap = false)
abstract class OcclusionCullerMixin {
    // Keep Sodium's cancellation, storage read phases and all three visitor channels intact.
    // Real-world occluders outside a whitelist must not hide retained terrain. Inactive SR
    // preserves Sodium's original traversal without building an SR-side section collection.
    // The 1.20.1 direct-section optimization requires a separate 0.9 traversal implementation.
    @ModifyVariable(method = "findVisible", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean selectiverender$ignoreFilteredOccluders(boolean useOcclusionCulling) {
        return useOcclusionCulling && !(SelectiveRenderState.enabled() && SelectiveRenderState.filteringActive());
    }
}
