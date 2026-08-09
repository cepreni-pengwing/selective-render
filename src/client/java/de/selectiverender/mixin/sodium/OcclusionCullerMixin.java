package de.selectiverender.mixin.sodium;

import de.selectiverender.SelectiveRenderState;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.occlusion.OcclusionCuller", remap = false)
abstract class OcclusionCullerMixin {
    @Shadow @Final private Long2ReferenceMap<RenderSection> sections;

    @Shadow
    private static boolean isWithinRenderDistance(CameraTransform camera, RenderSection section,
                                                  float searchDistance) {
        throw new AssertionError();
    }

    @Inject(method = "findVisible", at = @At("HEAD"), cancellable = true)
    private void selectiverender$collectRegionSectionsDirectly(OcclusionCuller.Visitor visitor,
                                                                Viewport viewport,
                                                                float searchDistance,
                                                                boolean useOcclusionCulling,
                                                                int frame,
                                                                CallbackInfo ci) {
        if (!SelectiveRenderState.enabled()) return;

        CameraTransform camera = viewport.getTransform();
        for (RenderSection section : sections.values()) {
            if (!SelectiveRenderState.shouldRenderSection(
                    section.getChunkX(), section.getChunkY(), section.getChunkZ())) continue;
            if (!isWithinRenderDistance(camera, section, searchDistance)) continue;

            section.setLastVisibleFrame(frame);
            section.setIncomingDirections(0);
            visitor.visit(section, OcclusionCuller.isWithinFrustum(viewport, section));
        }
        ci.cancel();
    }
}
