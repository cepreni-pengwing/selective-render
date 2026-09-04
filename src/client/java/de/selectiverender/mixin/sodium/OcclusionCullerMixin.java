package de.selectiverender.mixin.sodium;

import de.selectiverender.BlockRegion;
import de.selectiverender.SelectiveRenderState;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.occlusion.OcclusionCuller", remap = false)
abstract class OcclusionCullerMixin {
    @Unique private static final int selectiverender$directIndexScanLimit = 4096;
    @Shadow @Final private Long2ReferenceMap<RenderSection> sections;
    @Unique private LongOpenHashSet selectiverender$visitedSections;
    @Unique private ObjectArrayList<RenderSection> selectiverender$orderedSections;
    @Unique private double selectiverender$cameraX;
    @Unique private double selectiverender$cameraY;
    @Unique private double selectiverender$cameraZ;
    @Unique private final Comparator<RenderSection> selectiverender$distanceComparator =
            Comparator.comparingDouble(this::selectiverender$distanceSquared);

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

        int cameraSectionX = camera.intX >> 4;
        int cameraSectionY = camera.intY >> 4;
        int cameraSectionZ = camera.intZ >> 4;
        if (SelectiveRenderState.shouldRenderSection(
                cameraSectionX, cameraSectionY, cameraSectionZ)) return;
        selectiverender$collectDirectly(visitor, viewport, searchDistance, frame, false);
        ci.cancel();
    }

    @Inject(method = "findVisible", at = @At("RETURN"))
    private void selectiverender$appendDisconnectedRegions(OcclusionCuller.Visitor visitor,
                                                            Viewport viewport,
                                                            float searchDistance,
                                                            boolean useOcclusionCulling,
                                                            int frame,
                                                            CallbackInfo ci) {
        if (!SelectiveRenderState.enabled()) return;
        CameraTransform camera = viewport.getTransform();
        if (!SelectiveRenderState.shouldRenderSection(
                camera.intX >> 4, camera.intY >> 4, camera.intZ >> 4)) return;
        selectiverender$collectDirectly(visitor, viewport, searchDistance, frame, true);
    }

    @Unique
    private void selectiverender$collectDirectly(OcclusionCuller.Visitor visitor,
                                                  Viewport viewport,
                                                  float searchDistance,
                                                  int frame,
                                                  boolean skipCameraRegion) {
        CameraTransform camera = viewport.getTransform();
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return;

        int radius = MathHelper.ceil(searchDistance / 16.0F) + 1;
        int cameraSectionX = camera.intX >> 4;
        int cameraSectionZ = camera.intZ >> 4;
        int minLoadedY = world.getBottomY() >> 4;
        int maxLoadedY = (world.getTopY() - 1) >> 4;
        if (selectiverender$orderedSections == null) {
            selectiverender$orderedSections = new ObjectArrayList<>();
        } else {
            selectiverender$orderedSections.clear();
        }
        selectiverender$cameraX = camera.x;
        selectiverender$cameraY = camera.y;
        selectiverender$cameraZ = camera.z;
        java.util.List<BlockRegion> traversalRegions = SelectiveRenderState.traversalRegions();
        de.selectiverender.TraversalSectionIndex traversalIndex =
                SelectiveRenderState.traversalSectionIndex();
        if (!skipCameraRegion && !traversalIndex.isEmpty()
                && traversalIndex.size() <= selectiverender$directIndexScanLimit) {
            for (int index = 0; index < traversalIndex.size(); index++) {
                long key = traversalIndex.keyAt(index);
                int sectionX = ChunkSectionPos.unpackX(key);
                int sectionY = ChunkSectionPos.unpackY(key);
                int sectionZ = ChunkSectionPos.unpackZ(key);
                if (Math.abs(sectionX - cameraSectionX) > radius
                        || Math.abs(sectionZ - cameraSectionZ) > radius
                        || sectionY < minLoadedY || sectionY > maxLoadedY) continue;
                selectiverender$addSection(key, sectionX, sectionY, sectionZ,
                        camera, viewport, searchDistance, frame);
            }
            selectiverender$visitOrdered(visitor, frame);
            return;
        }
        boolean deduplicate = traversalRegions.size() > 1;
        LongOpenHashSet visited = null;
        if (deduplicate) {
            if (selectiverender$visitedSections == null) selectiverender$visitedSections = new LongOpenHashSet();
            visited = selectiverender$visitedSections;
            visited.clear();
        }

        for (BlockRegion region : traversalRegions) {
            if (skipCameraRegion && region.contains(camera.intX, camera.intY, camera.intZ)) continue;
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
                        if (deduplicate && !visited.add(key)) continue;
                        selectiverender$addSection(key, sectionX, sectionY, sectionZ,
                                camera, viewport, searchDistance, frame);
                    }
                }
            }
        }

        selectiverender$visitOrdered(visitor, frame);
    }

    @Unique
    private void selectiverender$addSection(long key, int sectionX, int sectionY, int sectionZ,
                                             CameraTransform camera, Viewport viewport,
                                             float searchDistance, int frame) {
        if (!SelectiveRenderState.shouldRenderSection(sectionX, sectionY, sectionZ)) return;
        RenderSection section = sections.get(key);
        if (section == null || !isWithinRenderDistance(camera, section, searchDistance)
                || section.getLastVisibleFrame() == frame
                || !OcclusionCuller.isWithinFrustum(viewport, section)) return;
        selectiverender$orderedSections.add(section);
    }

    @Unique
    private void selectiverender$visitOrdered(OcclusionCuller.Visitor visitor, int frame) {
        if (selectiverender$orderedSections.size() > 1) {
            selectiverender$orderedSections.unstableSort(selectiverender$distanceComparator);
        }
        for (RenderSection section : selectiverender$orderedSections) {
            section.setLastVisibleFrame(frame);
            section.setIncomingDirections(0);
            visitor.visit(section, true);
        }
    }

    @Unique
    private double selectiverender$distanceSquared(RenderSection section) {
        double x = section.getCenterX() - selectiverender$cameraX;
        double y = section.getCenterY() - selectiverender$cameraY;
        double z = section.getCenterZ() - selectiverender$cameraZ;
        return x * x + y * y + z * z;
    }
}
