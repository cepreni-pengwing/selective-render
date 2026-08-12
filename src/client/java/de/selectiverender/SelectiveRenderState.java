package de.selectiverender;

import net.minecraft.client.MinecraftClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.Heightmap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.Collection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SelectiveRenderState {
    private static BlockPos first;
    private static BlockPos second;
    private static BlockRegion selection;
    private static List<BlockRegion> activeRegions = List.of();
    private static List<BlockRegion> hiddenRegions = List.of();
    private static List<BlockRegion> visibleOverrides = List.of();
    private static boolean enabled;
    private static boolean hideEnabled;
    private static List<BlockRegion> plotRegions = List.of();
    private static boolean plotModeActive;
    private static boolean plotRenderingEnabled;
    private static List<BlockRegion> traversalRegions = List.of();
    private static List<BlockRegion> visibleRegions = List.of();
    private static RegionIndex activeRegionIndex = RegionIndex.empty();
    private static RegionIndex hiddenRegionIndex = RegionIndex.empty();
    private static RegionIndex overrideRegionIndex = RegionIndex.empty();
    private static final VisibleOccluderCache visibleOccluderCache = new VisibleOccluderCache(16384);
    private static final ThreadLocal<SectionClassificationCache> sectionClassificationCache =
            ThreadLocal.withInitial(SectionClassificationCache::new);
    private static final ThreadLocal<LightInfluenceCache> lightInfluenceCache =
            ThreadLocal.withInitial(LightInfluenceCache::new);
    private static volatile int visibilityGeneration = 1;

    private SelectiveRenderState() { }

    public static void setFirst(BlockPos position) { first = position; }
    public static void setSecond(BlockPos position) { second = position; }
    public static BlockPos first() { return first; }
    public static BlockPos second() { return second; }
    public static BlockRegion selection() { return selection; }
    public static List<BlockRegion> activeRegions() { return plotModeActive ? plotRegions : activeRegions; }
    public static List<BlockRegion> hiddenRegions() { return hiddenRegions; }
    public static List<BlockRegion> visibleOverrides() { return visibleOverrides; }
    public static List<BlockRegion> traversalRegions() { return traversalRegions; }
    public static boolean enabled() {
        return plotModeActive
                ? plotRenderingEnabled && !plotRegions.isEmpty()
                : enabled && !activeRegions.isEmpty();
    }
    public static boolean hideEnabled() { return hideEnabled && !hiddenRegions.isEmpty(); }
    public static boolean plotModeActive() { return plotModeActive; }
    public static boolean plotRenderingEnabled() { return plotModeActive && plotRenderingEnabled; }
    public static List<BlockRegion> plotRegions() { return plotRegions; }

    public static void setSavedState(Collection<BlockRegion> regions, boolean newEnabled,
                                     Collection<BlockRegion> hidden, boolean newHideEnabled,
                                     Collection<BlockRegion> overrides) {
        activeRegions = List.copyOf(regions);
        hiddenRegions = List.copyOf(hidden);
        visibleOverrides = List.copyOf(overrides);
        enabled = newEnabled;
        hideEnabled = newHideEnabled;
        visibleOccluderCache.clear();
        rebuildDerivedRegions();
    }

    public static boolean saveSelection() {
        if (first == null || second == null) return false;
        selection = BlockRegion.between(first, second);
        return true;
    }

    public static boolean toggle() {
        if (plotModeActive) return togglePlotRendering();
        if (activeRegions.isEmpty()) return false;
        enabled = !enabled;
        visibleOccluderCache.clear();
        rebuildDerivedRegions();
        refreshRenderer();
        return true;
    }

    public static void activatePlotMode(Collection<BlockRegion> regions) {
        List<BlockRegion> next = List.copyOf(regions);
        if (next.isEmpty()) throw new IllegalArgumentException("Plot regions cannot be empty");
        plotRegions = next;
        plotModeActive = true;
        plotRenderingEnabled = true;
        visibleOccluderCache.clear();
        rebuildDerivedRegions();
        refreshRenderer();
    }

    public static boolean togglePlotRendering() {
        if (!plotModeActive || plotRegions.isEmpty()) return false;
        boolean wasEnabled = plotRenderingEnabled;
        plotRenderingEnabled = !plotRenderingEnabled;
        visibleOccluderCache.clear();
        rebuildDerivedRegions();
        if (wasEnabled) refreshRenderer(); else refreshRegions(plotRegions);
        return true;
    }

    public static boolean disablePlotMode() {
        if (!plotModeActive) return false;
        plotModeActive = false;
        plotRenderingEnabled = false;
        plotRegions = List.of();
        visibleOccluderCache.clear();
        rebuildDerivedRegions();
        refreshRenderer();
        return true;
    }

    public static void resetPlotMode() {
        plotModeActive = false;
        plotRenderingEnabled = false;
        plotRegions = List.of();
        visibleOccluderCache.clear();
        rebuildDerivedRegions();
    }

    public static boolean shouldRenderSection(int sectionX, int sectionY, int sectionZ) {
        return sectionVisibility(sectionX, sectionY, sectionZ) != SectionVisibility.HIDDEN;
    }

    public static SectionVisibility sectionVisibility(int sectionX, int sectionY, int sectionZ) {
        boolean whitelistEnabled = enabled();
        boolean hiddenEnabled = hideEnabled();
        if (!whitelistEnabled && !hiddenEnabled) return SectionVisibility.UNCHANGED;
        return sectionClassificationCache.get().get(visibilityGeneration,
                sectionX, sectionY, sectionZ, whitelistEnabled, activeRegionIndex,
                hiddenEnabled ? hiddenRegionIndex : RegionIndex.empty(),
                whitelistEnabled ? overrideRegionIndex : RegionIndex.empty());
    }

    public static boolean shouldRender(BlockPos position) {
        return shouldRender(position.getX(), position.getY(), position.getZ());
    }

    public static boolean shouldRender(double x, double y, double z) {
        return shouldRender(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
    }

    public static boolean shouldRender(int blockX, int blockY, int blockZ) {
        SectionVisibility section = sectionVisibility(
                blockX >> 4, blockY >> 4, blockZ >> 4);
        if (section == SectionVisibility.UNCHANGED) return true;
        if (section == SectionVisibility.HIDDEN) return false;
        return RegionVisibility.block(enabled(), activeRegionIndex,
                hideEnabled() ? hiddenRegionIndex : RegionIndex.empty(),
                enabled() ? overrideRegionIndex : RegionIndex.empty(),
                blockX, blockY, blockZ);
    }

    public static boolean mayNeedVirtualSkyLight(int blockX, int blockZ, int radius) {
        return lightInfluenceCache.get().get(visibilityGeneration,
                blockX >> 4, blockZ >> 4, radius,
                enabled() ? visibleRegions : List.of(),
                hideEnabled() ? hiddenRegions : List.of());
    }

    public static int visibleColumnTop(int blockX, int blockZ, int worldTop) {
        if (!enabled()) return worldTop;
        int top = Integer.MIN_VALUE;
        for (BlockRegion region : visibleRegions) {
            if (blockX >= region.minX() && blockX <= region.maxX()
                    && blockZ >= region.minZ() && blockZ <= region.maxZ()) {
                top = Math.max(top, region.maxY());
            }
        }
        return Math.min(top, worldTop);
    }

    public static int visibleColumnBottom(int blockX, int blockZ, int worldBottom) {
        if (!enabled()) return worldBottom;
        int bottom = Integer.MAX_VALUE;
        for (BlockRegion region : visibleRegions) {
            if (blockX >= region.minX() && blockX <= region.maxX()
                    && blockZ >= region.minZ() && blockZ <= region.maxZ()) {
                bottom = Math.min(bottom, region.minY());
            }
        }
        return Math.max(bottom, worldBottom);
    }

    public static boolean containsActive(BlockPos position) {
        for (BlockRegion region : activeRegions()) {
            if (region.contains(position)) return true;
        }
        return false;
    }

    public static boolean containsHidden(BlockPos position) {
        for (BlockRegion region : hiddenRegions) {
            if (region.contains(position)) return true;
        }
        return false;
    }

    public static boolean isActivelyHidden(BlockPos position) {
        return hideEnabled() && containsHidden(position);
    }

    public static boolean isActivelyHidden(Entity entity) {
        return !(entity instanceof PlayerEntity) && hideEnabled()
                && hiddenRegions.stream().anyMatch(region -> region.contains(
                MathHelper.floor(entity.getX()), MathHelper.floor(entity.getY()), MathHelper.floor(entity.getZ())));
    }

    public static boolean shouldRender(Entity entity) {
        return entity instanceof PlayerEntity || shouldRender(entity.getX(), entity.getY(), entity.getZ());
    }

    public static int highestVisibleOccluder(ClientWorld world, int blockX, int blockZ) {
        if (!enabled() && !hideEnabled()) return world.getTopY() - 1;
        return visibleOccluderCache.get(blockX, blockZ, (x, z) -> {
            int worldSurface = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
            int top = visibleColumnTop(x, z, Math.min(world.getTopY() - 1, worldSurface));
            int bottom = visibleColumnBottom(x, z, world.getBottomY());
            if (top == Integer.MIN_VALUE || bottom == Integer.MAX_VALUE || bottom > top) {
                return Integer.MIN_VALUE;
            }
            BlockPos.Mutable cursor = new BlockPos.Mutable(x, top, z);
            for (int y = top; y >= bottom; y--) {
                cursor.setY(y);
                if (!shouldRender(cursor)) continue;
                BlockState state = world.getBlockState(cursor);
                if (state.getOpacity(world, cursor) > 0) return y;
            }
            return Integer.MIN_VALUE;
        });
    }

    public static void invalidateVisibleOccluder(int blockX, int blockZ) {
        visibleOccluderCache.invalidate(blockX, blockZ);
    }

    public static void resetForDisconnect() {
        first = null;
        second = null;
        selection = null;
        activeRegions = List.of();
        hiddenRegions = List.of();
        visibleOverrides = List.of();
        visibleOccluderCache.clear();
        enabled = false;
        hideEnabled = false;
        resetPlotMode();
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
            int fromX = Math.max(minSectionX, Math.floorDiv(expandMin(region.minX()), 16));
            int toX = Math.min(maxSectionX, Math.floorDiv(expandMax(region.maxX()), 16));
            int fromY = Math.max(minSectionY, Math.floorDiv(expandMin(region.minY()), 16));
            int toY = Math.min(maxSectionY, Math.floorDiv(expandMax(region.maxY()), 16));
            int fromZ = Math.max(minSectionZ, Math.floorDiv(expandMin(region.minZ()), 16));
            int toZ = Math.min(maxSectionZ, Math.floorDiv(expandMax(region.maxZ()), 16));
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
        if (affected.size() > 1024 || affected.size() > loadedEstimate * 0.35) {
            refreshRenderer();
            return;
        }

        affected.forEach(section -> client.worldRenderer.scheduleBlockRender(
                section.x(), section.y(), section.z()));
        client.worldRenderer.scheduleTerrainUpdate();
    }

    private static int expandMin(int value) {
        return value == Integer.MIN_VALUE ? value : value - 1;
    }

    private static int expandMax(int value) {
        return value == Integer.MAX_VALUE ? value : value + 1;
    }

    private static void rebuildDerivedRegions() {
        List<BlockRegion> base = activeRegions();
        activeRegionIndex = RegionIndex.of(base);
        hiddenRegionIndex = RegionIndex.of(hiddenRegions);
        overrideRegionIndex = RegionIndex.of(visibleOverrides);
        if (visibleOverrides.isEmpty()) {
            traversalRegions = base;
            visibleRegions = base;
            advanceVisibilityGeneration();
            return;
        }
        ArrayList<BlockRegion> combined = new ArrayList<>(base.size() + visibleOverrides.size());
        combined.addAll(base);
        combined.addAll(visibleOverrides);
        List<BlockRegion> snapshot = List.copyOf(combined);
        traversalRegions = snapshot;
        visibleRegions = enabled() ? snapshot : base;
        advanceVisibilityGeneration();
    }

    private static void advanceVisibilityGeneration() {
        visibilityGeneration = visibilityGeneration == Integer.MAX_VALUE ? 1 : visibilityGeneration + 1;
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
