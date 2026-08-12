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
    private static final VisibleOccluderCache visibleOccluderCache = new VisibleOccluderCache(16384);

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
        if (!enabled() && !hideEnabled()) return true;
        return RegionVisibility.section(enabled(), activeRegions(),
                hideEnabled() ? hiddenRegions : List.of(), enabled() ? visibleOverrides : List.of(),
                sectionX, sectionY, sectionZ);
    }

    public static boolean shouldRender(BlockPos position) {
        return shouldRender(position.getX(), position.getY(), position.getZ());
    }

    public static boolean shouldRender(double x, double y, double z) {
        return shouldRender(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
    }

    public static boolean shouldRender(int blockX, int blockY, int blockZ) {
        if (!enabled() && !hideEnabled()) return true;
        return RegionVisibility.block(enabled(), activeRegions(),
                hideEnabled() ? hiddenRegions : List.of(), enabled() ? visibleOverrides : List.of(),
                blockX, blockY, blockZ);
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
        if (visibleOverrides.isEmpty()) {
            traversalRegions = base;
            visibleRegions = base;
            return;
        }
        ArrayList<BlockRegion> combined = new ArrayList<>(base.size() + visibleOverrides.size());
        combined.addAll(base);
        combined.addAll(visibleOverrides);
        List<BlockRegion> snapshot = List.copyOf(combined);
        traversalRegions = snapshot;
        visibleRegions = enabled() ? snapshot : base;
    }

    private record SectionCoordinate(int x, int y, int z) { }
}
