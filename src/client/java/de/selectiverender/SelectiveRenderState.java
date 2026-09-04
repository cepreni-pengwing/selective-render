package de.selectiverender;

import net.minecraft.client.MinecraftClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.Heightmap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Direction;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SelectiveRenderState {
    public static final int VIRTUAL_LIGHT_RADIUS = 14;
    private static BlockPos first;
    private static BlockPos second;
    private static BlockRegion selection;
    private static volatile VisibilitySnapshot visibility = VisibilitySnapshot.EMPTY;
    private static final VisibleOccluderCache visibleOccluderCache = new VisibleOccluderCache(16384);
    private static final ThreadLocal<SectionClassificationCache> sectionClassificationCache =
            ThreadLocal.withInitial(SectionClassificationCache::new);
    private static final ThreadLocal<LightInfluenceCache> lightInfluenceCache =
            ThreadLocal.withInitial(LightInfluenceCache::new);

    private SelectiveRenderState() { }

    public static void setFirst(BlockPos position) { first = position; }
    public static void setSecond(BlockPos position) { second = position; }
    public static BlockPos first() { return first; }
    public static BlockPos second() { return second; }
    public static BlockRegion selection() { return selection; }
    public static List<BlockRegion> activeRegions() { return visibility.activeRegions(); }
    public static List<BlockRegion> hiddenRegions() { return visibility.hiddenRegions(); }
    public static List<BlockRegion> visibleOverrides() { return visibility.visibleOverrides(); }
    public static List<BlockRegion> traversalRegions() { return visibility.traversalRegions(); }
    public static TraversalSectionIndex traversalSectionIndex() { return visibility.traversalSectionIndex(); }
    public static boolean enabled() { return visibility.enabled(); }
    public static boolean hideEnabled() { return visibility.hideEnabled(); }
    public static boolean plotModeActive() { return visibility.plotModeActive(); }
    public static boolean plotRenderingEnabled() { return visibility.plotRenderingEnabled(); }
    public static List<BlockRegion> plotRegions() { return visibility.plotRegions(); }
    public static int visibilityGeneration() { return visibility.generation(); }
    public static boolean filteringActive() {
        VisibilitySnapshot snapshot = visibility;
        return snapshot.enabled() || snapshot.hideEnabled();
    }

    public static void setSavedState(Collection<BlockRegion> regions, boolean newEnabled,
                                     Collection<BlockRegion> hidden, boolean newHideEnabled,
                                     Collection<BlockRegion> overrides) {
        visibleOccluderCache.clear();
        VisibilitySnapshot current = visibility;
        visibility = current.withSavedState(List.copyOf(regions), newEnabled,
                List.copyOf(hidden), newHideEnabled, List.copyOf(overrides), nextGeneration(current));
    }

    public static boolean saveSelection() {
        if (first == null || second == null) return false;
        selection = BlockRegion.between(first, second);
        return true;
    }

    public static boolean toggle() {
        VisibilitySnapshot current = visibility;
        if (current.plotModeActive()) return togglePlotRendering();
        if (current.configuredRegions().isEmpty()) return false;
        visibility = current.toggleConfiguredState(nextGeneration(current));
        visibleOccluderCache.clear();
        refreshRenderer();
        return true;
    }

    public static void activatePlotMode(Collection<BlockRegion> regions) {
        activatePlotMode(regions, true);
    }

    public static void activatePlotMode(Collection<BlockRegion> regions, boolean renderingEnabled) {
        List<BlockRegion> next = List.copyOf(regions);
        if (next.isEmpty()) throw new IllegalArgumentException("Plot regions cannot be empty");
        visibleOccluderCache.clear();
        VisibilitySnapshot current = visibility;
        visibility = current.withPlotState(next, true, renderingEnabled, nextGeneration(current));
        refreshRenderer();
    }

    public static void updatePlotMode(Collection<BlockRegion> regions, boolean renderingEnabled,
                                      Collection<BlockRegion> changedRegions) {
        List<BlockRegion> next = List.copyOf(regions);
        if (next.isEmpty()) throw new IllegalArgumentException("Plot regions cannot be empty");
        VisibilitySnapshot current = visibility;
        if (!current.plotModeActive()) {
            activatePlotMode(next, renderingEnabled);
            return;
        }
        visibleOccluderCache.clear();
        visibility = current.withPlotState(next, true, renderingEnabled, nextGeneration(current));
        if (PlotSelectionPolicy.needsMeshUpdate(current.plotRenderingEnabled(), renderingEnabled)) {
            refreshVisibilityRegions(changedRegions);
        }
    }

    public static boolean togglePlotRendering() {
        VisibilitySnapshot current = visibility;
        if (!current.plotModeActive() || current.plotRegions().isEmpty()) return false;
        boolean wasEnabled = current.plotRenderingEnabled();
        visibleOccluderCache.clear();
        visibility = current.withPlotState(current.plotRegions(), true,
                !current.plotRenderingEnabled(), nextGeneration(current));
        if (wasEnabled) refreshRenderer(); else refreshVisibilityRegions(current.plotRegions());
        return true;
    }

    public static boolean disablePlotMode() {
        VisibilitySnapshot current = visibility;
        if (!current.plotModeActive()) return false;
        visibleOccluderCache.clear();
        visibility = current.withPlotState(List.of(), false, false, nextGeneration(current));
        refreshRenderer();
        return true;
    }

    public static void resetPlotMode() {
        visibleOccluderCache.clear();
        VisibilitySnapshot current = visibility;
        visibility = current.withPlotState(List.of(), false, false, nextGeneration(current));
    }

    public static boolean shouldRenderSection(int sectionX, int sectionY, int sectionZ) {
        return sectionVisibility(sectionX, sectionY, sectionZ) != SectionVisibility.HIDDEN;
    }

    public static SectionVisibility sectionVisibility(int sectionX, int sectionY, int sectionZ) {
        VisibilitySnapshot snapshot = visibility;
        return sectionVisibility(snapshot, sectionX, sectionY, sectionZ);
    }

    private static SectionVisibility sectionVisibility(VisibilitySnapshot snapshot,
                                                        int sectionX, int sectionY, int sectionZ) {
        boolean whitelistEnabled = snapshot.enabled();
        boolean hiddenEnabled = snapshot.hideEnabled();
        if (!whitelistEnabled && !hiddenEnabled) return SectionVisibility.FULL_VISIBLE;
        return sectionClassificationCache.get().get(snapshot.generation(),
                sectionX, sectionY, sectionZ, whitelistEnabled, snapshot.activeRegionIndex(),
                hiddenEnabled ? snapshot.hiddenRegionIndex() : RegionIndex.empty(),
                whitelistEnabled ? snapshot.overrideRegionIndex() : RegionIndex.empty());
    }

    public static boolean shouldRender(BlockPos position) {
        return shouldRender(position.getX(), position.getY(), position.getZ());
    }

    public static boolean shouldRender(double x, double y, double z) {
        return shouldRender(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
    }

    public static boolean shouldRender(int blockX, int blockY, int blockZ) {
        VisibilitySnapshot snapshot = visibility;
        if (!snapshot.enabled() && !snapshot.hideEnabled()) return true;
        return shouldRender(snapshot, blockX, blockY, blockZ);
    }

    private static boolean shouldRender(VisibilitySnapshot snapshot,
                                        int blockX, int blockY, int blockZ) {
        SectionVisibility section = sectionVisibility(snapshot,
                blockX >> 4, blockY >> 4, blockZ >> 4);
        if (section == SectionVisibility.FULL_VISIBLE) return true;
        if (section == SectionVisibility.HIDDEN) return false;
        return RegionVisibility.block(snapshot.enabled(), snapshot.activeRegionIndex(),
                snapshot.hideEnabled() ? snapshot.hiddenRegionIndex() : RegionIndex.empty(),
                snapshot.enabled() ? snapshot.overrideRegionIndex() : RegionIndex.empty(),
                blockX, blockY, blockZ);
    }

    public static boolean mayNeedVirtualSkyLight(int blockX, int blockZ, int radius) {
        VisibilitySnapshot snapshot = visibility;
        return lightInfluenceCache.get().get(snapshot.generation(),
                blockX >> 4, blockZ >> 4, radius,
                snapshot.enabled() ? snapshot.visibleRegions() : List.of(),
                snapshot.hideEnabled() ? snapshot.hiddenRegions() : List.of());
    }

    public static int visibleColumnTop(int blockX, int blockZ, int worldTop) {
        VisibilitySnapshot snapshot = visibility;
        if (!snapshot.enabled()) return worldTop;
        int top = Integer.MIN_VALUE;
        for (BlockRegion region : snapshot.visibleRegions()) {
            if (blockX >= region.minX() && blockX <= region.maxX()
                    && blockZ >= region.minZ() && blockZ <= region.maxZ()) {
                top = Math.max(top, region.maxY());
            }
        }
        return Math.min(top, worldTop);
    }

    public static int visibleColumnBottom(int blockX, int blockZ, int worldBottom) {
        VisibilitySnapshot snapshot = visibility;
        if (!snapshot.enabled()) return worldBottom;
        int bottom = Integer.MAX_VALUE;
        for (BlockRegion region : snapshot.visibleRegions()) {
            if (blockX >= region.minX() && blockX <= region.maxX()
                    && blockZ >= region.minZ() && blockZ <= region.maxZ()) {
                bottom = Math.min(bottom, region.minY());
            }
        }
        return Math.max(bottom, worldBottom);
    }

    public static boolean containsActive(BlockPos position) {
        return visibility.activeRegionIndex().contains(
                position.getX(), position.getY(), position.getZ());
    }

    public static boolean containsHidden(BlockPos position) {
        return visibility.hiddenRegionIndex().contains(
                position.getX(), position.getY(), position.getZ());
    }

    public static boolean isActivelyHidden(BlockPos position) {
        VisibilitySnapshot snapshot = visibility;
        return snapshot.hideEnabled() && snapshot.hiddenRegionIndex().contains(
                position.getX(), position.getY(), position.getZ());
    }

    public static boolean isActivelyHidden(Entity entity) {
        VisibilitySnapshot snapshot = visibility;
        return !(entity instanceof PlayerEntity) && snapshot.hideEnabled()
                && snapshot.hiddenRegionIndex().contains(MathHelper.floor(entity.getX()),
                MathHelper.floor(entity.getY()), MathHelper.floor(entity.getZ()));
    }

    public static boolean shouldRender(Entity entity) {
        if (!(entity instanceof PlayerEntity) && !filteringActive()) return true;
        SelectiveRenderSettings.PlayerVisibility playerVisibility =
                SelectiveRenderSettings.playerVisibility();
        if (entity instanceof PlayerEntity) {
            if (playerVisibility == SelectiveRenderSettings.PlayerVisibility.EVERYWHERE) return true;
            if (playerVisibility == SelectiveRenderSettings.PlayerVisibility.NONE) return false;
        }
        boolean inside = shouldRender(entity.getX(), entity.getY(), entity.getZ());
        if (!(entity instanceof PlayerEntity)) return inside;
        return switch (playerVisibility) {
            case INSIDE -> inside;
            case OUTSIDE -> !inside;
            default -> true;
        };
    }

    public static boolean shouldInteract(BlockPos position) {
        SelectiveRenderSettings.InteractionMode mode = SelectiveRenderSettings.interactionMode();
        if (!interactionFilteringActive(mode)) return true;
        return InteractionPolicy.allows(mode,
                interactionInside(position.getX(), position.getY(), position.getZ()));
    }

    public static boolean shouldInteract(Entity entity) {
        SelectiveRenderSettings.InteractionMode mode = SelectiveRenderSettings.interactionMode();
        if (!interactionFilteringActive(mode)) return true;
        return InteractionPolicy.allows(mode, interactionInside(MathHelper.floor(entity.getX()),
                MathHelper.floor(entity.getY()), MathHelper.floor(entity.getZ())));
    }

    private static boolean interactionFilteringActive(SelectiveRenderSettings.InteractionMode mode) {
        VisibilitySnapshot snapshot = visibility;
        return InteractionPolicy.active(mode, snapshot.enabled() || snapshot.hideEnabled(),
                SelectiveRenderSettings.filterInteractionsWhenInactive(),
                !snapshot.activeRegions().isEmpty());
    }

    private static boolean interactionInside(int blockX, int blockY, int blockZ) {
        VisibilitySnapshot snapshot = visibility;
        if (snapshot.enabled() || snapshot.hideEnabled()) {
            return shouldRender(snapshot, blockX, blockY, blockZ);
        }
        return snapshot.activeRegionIndex().contains(blockX, blockY, blockZ);
    }

    public static boolean isBoundaryFace(BlockPos position, Direction direction) {
        return shouldRender(position) && !shouldRender(position.offset(direction));
    }

    public static SelectiveRenderSettings.BoundaryMode boundaryModeForFace(
            BlockPos position, Direction direction) {
        BlockPos neighbor = position.offset(direction);
        if (isActivelyHidden(neighbor)) return SelectiveRenderSettings.BoundaryMode.NORMAL;
        return SelectiveRenderSettings.boundaryMode();
    }

    public static List<BlockRegion> borderRegions() {
        VisibilitySnapshot snapshot = visibility;
        java.util.ArrayList<BlockRegion> regions = new java.util.ArrayList<>(snapshot.traversalRegions());
        if (snapshot.hideEnabled()) regions.addAll(snapshot.hiddenRegions());
        return List.copyOf(regions);
    }

    public static int highestVisibleOccluder(ClientWorld world, int blockX, int blockZ) {
        VisibilitySnapshot snapshot = visibility;
        if (!snapshot.enabled() && !snapshot.hideEnabled()) return world.getTopY() - 1;
        return visibleOccluderCache.get(snapshot.generation(), blockX, blockZ, (x, z) -> {
            int worldSurface = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
            int top = visibleColumnTop(snapshot, x, z,
                    Math.min(world.getTopY() - 1, worldSurface));
            int bottom = visibleColumnBottom(snapshot, x, z, world.getBottomY());
            if (top == Integer.MIN_VALUE || bottom == Integer.MAX_VALUE || bottom > top) {
                return Integer.MIN_VALUE;
            }
            BlockPos.Mutable cursor = new BlockPos.Mutable(x, top, z);
            for (int y = top; y >= bottom; y--) {
                cursor.setY(y);
                if (!shouldRender(snapshot, x, y, z)) continue;
                BlockState state = world.getBlockState(cursor);
                if (state.getOpacity(world, cursor) > 0) return y;
            }
            return Integer.MIN_VALUE;
        });
    }

    public static void invalidateVisibleOccluder(int blockX, int blockZ) {
        visibleOccluderCache.invalidate(blockX, blockZ);
    }

    public static void invalidateVirtualSkyLight(int blockX, int blockY, int blockZ) {
        if (!filteringActive()) return;
        VirtualSkyLightSampler.invalidateBlock(blockX, blockY, blockZ);
    }

    private static int visibleColumnTop(VisibilitySnapshot snapshot,
                                        int blockX, int blockZ, int worldTop) {
        if (!snapshot.enabled()) return worldTop;
        int top = Integer.MIN_VALUE;
        for (BlockRegion region : snapshot.visibleRegions()) {
            if (blockX >= region.minX() && blockX <= region.maxX()
                    && blockZ >= region.minZ() && blockZ <= region.maxZ()) {
                top = Math.max(top, region.maxY());
            }
        }
        return Math.min(top, worldTop);
    }

    private static int visibleColumnBottom(VisibilitySnapshot snapshot,
                                           int blockX, int blockZ, int worldBottom) {
        if (!snapshot.enabled()) return worldBottom;
        int bottom = Integer.MAX_VALUE;
        for (BlockRegion region : snapshot.visibleRegions()) {
            if (blockX >= region.minX() && blockX <= region.maxX()
                    && blockZ >= region.minZ() && blockZ <= region.maxZ()) {
                bottom = Math.min(bottom, region.minY());
            }
        }
        return Math.max(bottom, worldBottom);
    }

    public static void invalidateLightCacheChunk(int chunkX, int chunkZ) {
        if (!filteringActive()) return;
        visibleOccluderCache.removeChunk(chunkX, chunkZ);
        VirtualSkyLightSampler.invalidateChunk(chunkX, chunkZ);
    }

    public static void resetForDisconnect() {
        first = null;
        second = null;
        selection = null;
        visibleOccluderCache.clear();
        VirtualSkyLightSampler.invalidate();
        VisibilitySnapshot current = visibility;
        visibility = VisibilitySnapshot.create(List.of(), false, List.of(), false,
                List.of(), List.of(), false, false, nextGeneration(current));
    }

    public static void refreshRenderer() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.worldRenderer != null && client.world != null) {
            client.worldRenderer.reload();
        }
    }

    public static void refreshRegions(Collection<BlockRegion> regions) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.worldRenderer == null || client.world == null || regions.isEmpty()) return;

        BlockPos camera = client.gameRenderer.getCamera().getBlockPos();
        int viewDistance = client.options.getViewDistance().getValue() + 1;
        int minSectionX = Math.floorDiv(camera.getX(), 16) - viewDistance;
        int maxSectionX = Math.floorDiv(camera.getX(), 16) + viewDistance;
        int minSectionZ = Math.floorDiv(camera.getZ(), 16) - viewDistance;
        int maxSectionZ = Math.floorDiv(camera.getZ(), 16) + viewDistance;
        int minSectionY = Math.floorDiv(client.world.getBottomY(), 16);
        int maxSectionY = Math.floorDiv(client.world.getTopY() - 1, 16);
        Set<SectionCoordinate> affected = new HashSet<>();

        for (BlockRegion region : regions) {
            int fromX = Math.max(minSectionX, Math.floorDiv(LightRebuildRange.expandMin(region.minX(), VIRTUAL_LIGHT_RADIUS), 16));
            int toX = Math.min(maxSectionX, Math.floorDiv(LightRebuildRange.expandMax(region.maxX(), VIRTUAL_LIGHT_RADIUS), 16));
            int fromY = Math.max(minSectionY, Math.floorDiv(LightRebuildRange.expandMin(region.minY(), VIRTUAL_LIGHT_RADIUS), 16));
            int toY = Math.min(maxSectionY, Math.floorDiv(LightRebuildRange.expandMax(region.maxY(), VIRTUAL_LIGHT_RADIUS), 16));
            int fromZ = Math.max(minSectionZ, Math.floorDiv(LightRebuildRange.expandMin(region.minZ(), VIRTUAL_LIGHT_RADIUS), 16));
            int toZ = Math.min(maxSectionZ, Math.floorDiv(LightRebuildRange.expandMax(region.maxZ(), VIRTUAL_LIGHT_RADIUS), 16));
            for (int sectionX = fromX; sectionX <= toX; sectionX++) {
                for (int sectionY = fromY; sectionY <= toY; sectionY++) {
                    for (int sectionZ = fromZ; sectionZ <= toZ; sectionZ++) {
                        affected.add(new SectionCoordinate(sectionX, sectionY, sectionZ));
                    }
                }
            }
        }

        int loadedEstimate = (viewDistance * 2 + 1) * (viewDistance * 2 + 1)
                * (maxSectionY - minSectionY + 1);
        if (RenderReloadPolicy.requiresFullReload(affected.size(), loadedEstimate,
                SelectiveRenderSettings.fullReloadThreshold())) {
            refreshRenderer();
            return;
        }

        affected.forEach(section -> client.worldRenderer.scheduleBlockRender(
                section.x(), section.y(), section.z()));
        client.worldRenderer.scheduleTerrainUpdate();
    }

    public static void refreshOptionalVisuals() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!FlywheelCompat.refresh(client.world)) refreshRenderer();
    }

    public static void refreshVisibilityRegions(Collection<BlockRegion> regions) {
        refreshRegions(regions);
        if (!FlywheelCompat.refresh(MinecraftClient.getInstance().world)) refreshRenderer();
    }

    private static int nextGeneration(VisibilitySnapshot current) {
        return current.generation() == Integer.MAX_VALUE ? 1 : current.generation() + 1;
    }

    private static boolean intersectsExpandedChunk(BlockRegion region,
                                                   int sectionX, int sectionZ, int radius) {
        long minX = (long) sectionX << 4;
        long minZ = (long) sectionZ << 4;
        return (long) region.maxX() + radius >= minX
                && (long) region.minX() - radius <= minX + 15
                && (long) region.maxZ() + radius >= minZ
                && (long) region.minZ() - radius <= minZ + 15;
    }

    private static final class SectionClassificationCache {
        private static final int SIZE = 64;
        private final int[] generations = new int[SIZE];
        private final int[] sectionXs = new int[SIZE];
        private final int[] sectionYs = new int[SIZE];
        private final int[] sectionZs = new int[SIZE];
        private final SectionVisibility[] values = new SectionVisibility[SIZE];

        private SectionVisibility get(int generation, int sectionX, int sectionY, int sectionZ,
                                      boolean whitelistEnabled, RegionIndex includedRegions,
                                      RegionIndex hidden, RegionIndex overrides) {
            int index = mix(sectionX, sectionY, sectionZ) & (SIZE - 1);
            if (generations[index] == generation
                    && sectionXs[index] == sectionX
                    && sectionYs[index] == sectionY
                    && sectionZs[index] == sectionZ) return values[index];

            SectionVisibility value = RegionVisibility.classifySection(whitelistEnabled,
                    includedRegions, hidden, overrides, sectionX, sectionY, sectionZ);
            sectionXs[index] = sectionX;
            sectionYs[index] = sectionY;
            sectionZs[index] = sectionZ;
            values[index] = value;
            generations[index] = generation;
            return value;
        }

        private static int mix(int x, int y, int z) {
            int hash = x * 0x8da6b343;
            hash ^= y * 0xd8163841;
            hash ^= z * 0xcb1ab31f;
            return hash ^ (hash >>> 16);
        }
    }

    private static final class LightInfluenceCache {
        private static final int SIZE = 64;
        private final int[] generations = new int[SIZE];
        private final int[] sectionXs = new int[SIZE];
        private final int[] sectionZs = new int[SIZE];
        private final boolean[] values = new boolean[SIZE];

        private boolean get(int generation, int sectionX, int sectionZ, int radius,
                            List<BlockRegion> includedRegions, List<BlockRegion> hidden) {
            int index = SectionClassificationCache.mix(sectionX, 0, sectionZ) & (SIZE - 1);
            if (generations[index] == generation
                    && sectionXs[index] == sectionX
                    && sectionZs[index] == sectionZ) return values[index];

            boolean value = intersectsExpandedChunk(includedRegions, sectionX, sectionZ, radius)
                    || intersectsExpandedChunk(hidden, sectionX, sectionZ, radius);
            sectionXs[index] = sectionX;
            sectionZs[index] = sectionZ;
            values[index] = value;
            generations[index] = generation;
            return value;
        }

        private static boolean intersectsExpandedChunk(List<BlockRegion> regions,
                                                       int sectionX, int sectionZ, int radius) {
            for (BlockRegion region : regions) {
                if (SelectiveRenderState.intersectsExpandedChunk(
                        region, sectionX, sectionZ, radius)) return true;
            }
            return false;
        }
    }

    private record SectionCoordinate(int x, int y, int z) { }
}
