package de.selectiverender.mixin.sodium;

import de.selectiverender.BlockRegion;
import de.selectiverender.SelectiveRenderState;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
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
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            ci.cancel();
            return;
        }

        int radius = MathHelper.ceil(searchDistance / 16.0F) + 1;
        int cameraSectionX = camera.intX >> 4;
        int cameraSectionZ = camera.intZ >> 4;
        int minLoadedY = world.getBottomY() >> 4;
        int maxLoadedY = (world.getTopY() - 1) >> 4;
        LongOpenHashSet visited = new LongOpenHashSet();

        for (BlockRegion region : SelectiveRenderState.traversalRegions()) {
            int minX = Math.max(Math.floorDiv(region.minX(), 16), cameraSectionX - radius);
            int maxX = Math.min(Math.floorDiv(region.maxX(), 16), cameraSectionX + radius);
            int minY = Math.max(Math.floorDiv(region.minY(), 16), minLoadedY);
            int maxY = Math.min(Math.floorDiv(region.maxY(), 16), maxLoadedY);
            int minZ = Math.max(Math.floorDiv(region.minZ(), 16), cameraSectionZ - radius);
            int maxZ = Math.min(Math.floorDiv(region.maxZ(), 16), cameraSectionZ + radius);

            for (int sectionX = minX; sectionX <= maxX; sectionX++) {
                for (int sectionY = minY; sectionY <= maxY; sectionY++) {
                    for (int sectionZ = minZ; sectionZ <= maxZ; sectionZ++) {
                        long key = ChunkSectionPos.asLong(sectionX, sectionY, sectionZ);
                        if (!visited.add(key)) continue;
                        if (!SelectiveRenderState.shouldRenderSection(sectionX, sectionY, sectionZ)) continue;

                        RenderSection section = sections.get(key);
                        if (section == null || !isWithinRenderDistance(camera, section, searchDistance)) continue;

                        section.setLastVisibleFrame(frame);
                        section.setIncomingDirections(0);
                        visitor.visit(section, OcclusionCuller.isWithinFrustum(viewport, section));
                    }
                }
            }
        }
        ci.cancel();
    }
}
