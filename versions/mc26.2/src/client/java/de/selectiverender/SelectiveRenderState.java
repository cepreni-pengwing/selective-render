package de.selectiverender;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

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
        return snapshot.enabled() || snapshot.hideEnabled() || !snapshot.filteredRegions().isEmpty();
    }

    static VisibilitySnapshot snapshot() { return visibility; }

    static void refreshChange(VisibilitySnapshot previous) {
        VisibilityRefreshPlan plan = VisibilityRefreshPlan.between(previous, visibility);
        if (plan.isEmpty()) return;
        refreshRegions(plan.changedRegions(), plan.scanComplement() ? previous : null);
        if (!FlywheelCompat.refresh(Minecraft.getInstance().level)) refreshRenderer();
    }

    public static void setSavedState(Collection<BlockRegion> regions, boolean newEnabled,
                                     Collection<BlockRegion> hidden, boolean newHideEnabled,
                                     Collection<BlockRegion> overrides,
                                     Collection<FilteredRegion> filteredRegions) {
        visibleOccluderCache.clear();
        VisibilitySnapshot current = visibility;
        visibility = current.withSavedState(List.copyOf(regions), newEnabled,
                List.copyOf(hidden), newHideEnabled, List.copyOf(overrides),
                List.copyOf(filteredRegions), nextGeneration(current));
    }

    public static void setSavedState(Collection<BlockRegion> regions, boolean newEnabled,
                                     Collection<BlockRegion> hidden, boolean newHideEnabled,
                                     Collection<BlockRegion> overrides) {
        setSavedState(regions, newEnabled, hidden, newHideEnabled, overrides, List.of());
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
        refreshChange(current);
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
        refreshChange(current);
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
            refreshChange(current);
        }
    }

    public static boolean togglePlotRendering() {
        VisibilitySnapshot current = visibility;
        if (!current.plotModeActive() || current.plotRegions().isEmpty()) return false;
        visibleOccluderCache.clear();
        visibility = current.withPlotState(current.plotRegions(), true,
                !current.plotRenderingEnabled(), nextGeneration(current));
        refreshChange(current);
        return true;
    }

    public static boolean disablePlotMode() {
        VisibilitySnapshot current = visibility;
        if (!current.plotModeActive()) return false;
        visibleOccluderCache.clear();
        visibility = current.withPlotState(List.of(), false, false, nextGeneration(current));
        refreshChange(current);
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
        return shouldRender(Mth.floor(x), Mth.floor(y), Mth.floor(z));
    }

    public static boolean shouldRender(int blockX, int blockY, int blockZ) {
        VisibilitySnapshot snapshot = visibility;
        if (!snapshot.enabled() && !snapshot.hideEnabled()) return true;
        return shouldRender(snapshot, blockX, blockY, blockZ);
    }

    public static boolean shouldRender(BlockState state, int x, int y, int z) {
        VisibilitySnapshot snapshot = visibility;
        if (!snapshot.enabled() && !snapshot.hideEnabled()) {
            if (snapshot.filteredRegions().isEmpty()) return true;
        } else if (!shouldRender(snapshot, x, y, z)) {
            return false;
        }
        return matchesBlockFilters(snapshot, state, x, y, z);
    }

    private static boolean matchesBlockFilters(VisibilitySnapshot snapshot, BlockState state,
                                               int x, int y, int z) {
        if (snapshot.filteredRegions().isEmpty()) return true;
        boolean matchedRegion = false, allowed = false;
        String blockId = null;
        Set<String> tags = null;
        for (FilteredRegion filtered : snapshot.filteredRegions()) {
            if (!filtered.region().contains(x, y, z)) continue;
            matchedRegion = true;
            if (filtered.rules().isEmpty()) return true;
            if (blockId == null) {
                blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                tags = BuiltInRegistries.BLOCK.wrapAsHolder(state.getBlock()).tags()
                        .map(tag -> tag.location().toString())
                        .collect(java.util.stream.Collectors.toSet());
            }
            if (BlockFilterRule.allows(filtered.rules(), blockId, tags)) allowed = true;
        }
        return !matchedRegion || allowed;
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

    public static int unfilteredLightCeiling(int minX, int minY, int minZ, int maxX, int maxZ) {
        VisibilitySnapshot snapshot = visibility;
        return VirtualLightBounds.visibleCeiling(snapshot.enabled(), snapshot.visibleRegions(),
                snapshot.hideEnabled() ? snapshot.hiddenRegions() : List.of(),
                minX, minY, minZ, maxX, maxZ);
    }

    public static boolean mayNeedVirtualSkyLight(int blockX, int blockZ) {
        if (SelectiveRenderSettings.virtualLightMode()
                == SelectiveRenderSettings.VirtualLightMode.NONE) return false;
        VisibilitySnapshot snapshot = visibility;
        return lightInfluenceCache.get().get(snapshot.generation(),
                blockX >> 4, blockZ >> 4,
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
        return isActivelyHidden(position.getX(), position.getY(), position.getZ());
    }

    public static boolean isActivelyHidden(int blockX, int blockY, int blockZ) {
        VisibilitySnapshot snapshot = visibility;
        return snapshot.hideEnabled() && snapshot.hiddenRegionIndex().contains(
                blockX, blockY, blockZ);
    }

    public static boolean isActivelyHidden(Entity entity) {
        VisibilitySnapshot snapshot = visibility;
        return !(entity instanceof Player) && snapshot.hideEnabled()
                && snapshot.hiddenRegionIndex().contains(Mth.floor(entity.getX()),
                Mth.floor(entity.getY()), Mth.floor(entity.getZ()));
    }

    public static boolean shouldRender(Entity entity) {
        if (!(entity instanceof Player) && !filteringActive()) return true;
        SelectiveRenderSettings.PlayerVisibility playerVisibility =
                SelectiveRenderSettings.playerVisibility();
        if (entity instanceof Player) {
            boolean ownPlayer = entity == Minecraft.getInstance().player;
            if (playerVisibility == SelectiveRenderSettings.PlayerVisibility.OWN_ONLY) return ownPlayer;
            if (playerVisibility == SelectiveRenderSettings.PlayerVisibility.EXCEPT_OWN) return !ownPlayer;
            if (playerVisibility == SelectiveRenderSettings.PlayerVisibility.EVERYWHERE) return true;
            if (playerVisibility == SelectiveRenderSettings.PlayerVisibility.NONE) return false;
        }
        boolean inside = shouldRender(entity.getX(), entity.getY(), entity.getZ());
        if (!(entity instanceof Player)) return inside;
        return switch (playerVisibility) {
            case INSIDE -> inside;
            case OUTSIDE -> !inside;
            default -> true;
        };
    }

    public static boolean shouldInteract(BlockPos position) {
        boolean interactWithHidden = SelectiveRenderSettings.interactWithHiddenRegions();
        boolean hidden = isActivelyHidden(position);
        boolean filteredOut = !interactWithHidden && !hidden && isFilteredOut(position);
        if (!InteractionPolicy.allowsHiddenBlock(hidden, filteredOut, interactWithHidden)) return false;
        SelectiveRenderSettings.InteractionMode mode = SelectiveRenderSettings.interactionMode();
        if (!interactionFilteringActive(mode)) return true;
        return InteractionPolicy.allows(mode,
                interactionInside(position.getX(), position.getY(), position.getZ()));
    }

    private static boolean isFilteredOut(BlockPos position) {
        VisibilitySnapshot snapshot = visibility;
        if (snapshot.filteredRegions().isEmpty()) return false;
        int x = position.getX();
        int y = position.getY();
        int z = position.getZ();
        boolean insideFilteredRegion = false;
        for (FilteredRegion filtered : snapshot.filteredRegions()) {
            if (filtered.region().contains(x, y, z)) {
                insideFilteredRegion = true;
                break;
            }
        }
        if (!insideFilteredRegion) return false;
        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world == null) return false;
        return !matchesBlockFilters(snapshot, world.getBlockState(position), x, y, z);
    }

    public static boolean shouldInteract(Entity entity) {
        VisibilitySnapshot snapshot = visibility;
        if (snapshot.hideEnabled() && snapshot.hiddenRegionIndex().contains(
                Mth.floor(entity.getX()), Mth.floor(entity.getY()), Mth.floor(entity.getZ()))
                && !SelectiveRenderSettings.interactWithHiddenRegions()) return false;
        SelectiveRenderSettings.InteractionMode mode = SelectiveRenderSettings.interactionMode();
        if (!interactionFilteringActive(mode)) return true;
        return InteractionPolicy.allows(mode, interactionInside(Mth.floor(entity.getX()),
                Mth.floor(entity.getY()), Mth.floor(entity.getZ())));
    }

    private static boolean interactionFilteringActive(SelectiveRenderSettings.InteractionMode mode) {
        VisibilitySnapshot snapshot = visibility;
        return InteractionPolicy.active(mode, snapshot.enabled() || snapshot.hideEnabled(),
                SelectiveRenderSettings.filterInteractionsWhenInactive(),
                snapshot.plotModeActive() ? !snapshot.plotRegions().isEmpty()
                        : SelectiveRenderConfig.hasSavedRegions());
    }

    private static boolean interactionInside(int blockX, int blockY, int blockZ) {
        VisibilitySnapshot snapshot = visibility;
        if (snapshot.enabled() || snapshot.hideEnabled()) {
            return shouldRender(snapshot, blockX, blockY, blockZ)
                    || (SelectiveRenderSettings.interactWithHiddenRegions()
                    && snapshot.hiddenRegionIndex().contains(blockX, blockY, blockZ));
        }
        if (snapshot.plotModeActive()) {
            return snapshot.activeRegionIndex().contains(blockX, blockY, blockZ);
        }
        return SelectiveRenderConfig.containsSavedRegion(blockX, blockY, blockZ,
                SelectiveRenderSettings.interactWithHiddenRegions());
    }

    public static boolean shouldSeedVirtualSkyColumn(boolean visibleColumn) {
        return SelectiveRenderSettings.virtualLightMode().seedsColumn(visibleColumn);
    }

    public static boolean shouldScanVirtualSkyColumn(boolean visibleColumn) {
        if (SelectiveRenderSettings.virtualLightMode().seedsColumn(visibleColumn)) return true;
        VisibilitySnapshot snapshot = visibility;
        return snapshot.hideEnabled() && !snapshot.hiddenRegions().isEmpty()
                && SelectiveRenderSettings.hiddenVirtualLightMode().seedsColumn(visibleColumn);
    }

    public static boolean shouldSeedVirtualSkyColumn(boolean visibleColumn,
                                                     int blockX, int blockY, int blockZ) {
        return virtualLightModeAt(blockX, blockY, blockZ).seedsColumn(visibleColumn);
    }

    public static boolean shouldPropagateVirtualSkyLight(boolean fromVisible, boolean toVisible) {
        return SelectiveRenderSettings.virtualLightMode()
                .allowsPropagation(fromVisible, toVisible);
    }

    public static boolean shouldPropagateVirtualSkyLight(boolean fromVisible, boolean toVisible,
                                                          int fromX, int fromY, int fromZ,
                                                          int toX, int toY, int toZ) {
        SelectiveRenderSettings.VirtualLightMode renderMode = SelectiveRenderSettings.virtualLightMode();
        SelectiveRenderSettings.VirtualLightMode hiddenMode = SelectiveRenderSettings.hiddenVirtualLightMode();
        // Identical policies need no spatial classification, including the default BOTH/BOTH.
        if (renderMode == hiddenMode) return renderMode.allowsPropagation(fromVisible, toVisible);
        SelectiveRenderSettings.VirtualLightMode mode = isActivelyHidden(fromX, fromY, fromZ)
                || isActivelyHidden(toX, toY, toZ) ? hiddenMode : renderMode;
        return mode.allowsPropagation(fromVisible, toVisible);
    }

    private static SelectiveRenderSettings.VirtualLightMode virtualLightModeAt(
            int blockX, int blockY, int blockZ) {
        SelectiveRenderSettings.VirtualLightMode renderMode = SelectiveRenderSettings.virtualLightMode();
        SelectiveRenderSettings.VirtualLightMode hiddenMode = SelectiveRenderSettings.hiddenVirtualLightMode();
        if (renderMode == hiddenMode) return renderMode;
        return isActivelyHidden(blockX, blockY, blockZ) ? hiddenMode : renderMode;
    }

    public static boolean isBoundaryFace(BlockPos position, Direction direction) {
        return shouldRender(position) && !shouldRender(position.relative(direction));
    }

    public static SelectiveRenderSettings.BoundaryMode boundaryModeForFace(
            BlockPos position, Direction direction) {
        BlockPos neighbor = position.relative(direction);
        if (isActivelyHidden(neighbor)) return SelectiveRenderSettings.BoundaryMode.NORMAL;
        return SelectiveRenderSettings.boundaryMode();
    }

    public static List<BlockRegion> borderRegions() {
        VisibilitySnapshot snapshot = visibility;
        java.util.LinkedHashSet<BlockRegion> regions =
                new java.util.LinkedHashSet<>(SelectiveRenderConfig.allRegions());
        regions.addAll(snapshot.plotRegions());
        return List.copyOf(regions);
    }

    public static int highestVisibleOccluder(ClientLevel world, int blockX, int blockZ) {
        VisibilitySnapshot snapshot = visibility;
        if (!snapshot.enabled() && !snapshot.hideEnabled()) return world.getMaxY();
        return visibleOccluderCache.get(snapshot.generation(), blockX, blockZ, (x, z) -> {
            int worldSurface = world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            int top = visibleColumnTop(snapshot, x, z,
                    Math.min(world.getMaxY(), worldSurface));
            int bottom = visibleColumnBottom(snapshot, x, z, world.getMinY());
            if (top == Integer.MIN_VALUE || bottom == Integer.MAX_VALUE || bottom > top) {
                return Integer.MIN_VALUE;
            }
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, top, z);
            for (int y = top; y >= bottom; y--) {
                cursor.setY(y);
                if (!shouldRender(snapshot, x, y, z)) continue;
                BlockState state = world.getBlockState(cursor);
                if (state.getLightDampening() > 0) return y;
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
                List.of(), List.of(), List.of(), false, false, nextGeneration(current));
    }

    public static void refreshRenderer() {
        Minecraft client = Minecraft.getInstance();
        if (client.levelRenderer != null && client.level != null) {
            client.levelExtractor.allChanged();
        }
    }

    public static void refreshRegions(Collection<BlockRegion> regions) {
        refreshRegions(regions, null);
    }

    private static void refreshRegions(Collection<BlockRegion> regions, VisibilitySnapshot previous) {
        Minecraft client = Minecraft.getInstance();
        if (client.levelRenderer == null || client.level == null || (regions.isEmpty() && previous == null)) return;

        BlockPos camera = client.gameRenderer.mainCamera().blockPosition();
        int viewDistance = client.options.renderDistance().get() + 1;
        int minSectionX = Math.floorDiv(camera.getX(), 16) - viewDistance;
        int maxSectionX = Math.floorDiv(camera.getX(), 16) + viewDistance;
        int minSectionZ = Math.floorDiv(camera.getZ(), 16) - viewDistance;
        int maxSectionZ = Math.floorDiv(camera.getZ(), 16) + viewDistance;
        int minSectionY = Math.floorDiv(client.level.getMinY(), 16);
        int maxSectionY = Math.floorDiv(client.level.getMaxY(), 16);
        Set<SectionCoordinate> affected = new HashSet<>();
        int threshold = SelectiveRenderSettings.fullReloadThreshold();

        // Whitelist transitions also change sections outside the selected region.
        if (previous != null) {
            for (int x = minSectionX; x <= maxSectionX; x++) {
                for (int z = minSectionZ; z <= maxSectionZ; z++) {
                    var chunk = client.level.getChunkSource().getChunkNow(x, z);
                    if (chunk == null) continue;
                    for (int y = minSectionY; y <= maxSectionY; y++) {
                        if (chunk.getSection(y - minSectionY).hasOnlyAir()) continue;
                        SectionVisibility before = sectionVisibility(previous, x, y, z);
                        SectionVisibility after = sectionVisibility(visibility, x, y, z);
                        if (!VisibilityRefreshPlan.sectionChanged(before, after)) continue;
                        affected.add(new SectionCoordinate(x, y, z));
                        if (RenderReloadPolicy.requiresFullReload(affected.size(), threshold)) { refreshRenderer(); return; }
                    }
                }
            }
        }

        for (BlockRegion region : regions) {
            int fromX = Math.max(minSectionX, Math.floorDiv(LightRebuildRange.expandMin(region.minX(), VIRTUAL_LIGHT_RADIUS), 16));
            int toX = Math.min(maxSectionX, Math.floorDiv(LightRebuildRange.expandMax(region.maxX(), VIRTUAL_LIGHT_RADIUS), 16));
            int fromY = Math.max(minSectionY, Math.floorDiv(LightRebuildRange.expandMin(region.minY(), VIRTUAL_LIGHT_RADIUS), 16));
            int toY = Math.min(maxSectionY, Math.floorDiv(LightRebuildRange.expandMax(region.maxY(), VIRTUAL_LIGHT_RADIUS), 16));
            int fromZ = Math.max(minSectionZ, Math.floorDiv(LightRebuildRange.expandMin(region.minZ(), VIRTUAL_LIGHT_RADIUS), 16));
            int toZ = Math.min(maxSectionZ, Math.floorDiv(LightRebuildRange.expandMax(region.maxZ(), VIRTUAL_LIGHT_RADIUS), 16));
            for (int sectionX = fromX; sectionX <= toX; sectionX++) {
                for (int sectionZ = fromZ; sectionZ <= toZ; sectionZ++) {
                    var chunk = client.level.getChunkSource().getChunkNow(sectionX, sectionZ);
                    if (chunk == null) continue;
                    for (int sectionY = fromY; sectionY <= toY; sectionY++) {
                        if (chunk.getSection(sectionY - minSectionY).hasOnlyAir()) continue;
                        affected.add(new SectionCoordinate(sectionX, sectionY, sectionZ));
                        if (RenderReloadPolicy.requiresFullReload(affected.size(), threshold)) { refreshRenderer(); return; }
                    }
                }
            }
        }

        affected.forEach(section -> client.levelExtractor.setSectionDirty(
                section.x(), section.y(), section.z()));
        client.levelRenderer.sectionOcclusionGraph().invalidate();
    }

    public static void refreshOptionalVisuals() {
        Minecraft client = Minecraft.getInstance();
        if (!FlywheelCompat.refresh(client.level)) refreshRenderer();
    }

    public static void refreshVisibilityRegions(Collection<BlockRegion> regions) {
        refreshRegions(regions);
        if (!FlywheelCompat.refresh(Minecraft.getInstance().level)) refreshRenderer();
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
        private static final int SIZE = 4096;
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

        private boolean get(int generation, int sectionX, int sectionZ,
                            List<BlockRegion> includedRegions, List<BlockRegion> hidden) {
            int index = SectionClassificationCache.mix(sectionX, 0, sectionZ) & (SIZE - 1);
            if (generations[index] == generation
                    && sectionXs[index] == sectionX
                    && sectionZs[index] == sectionZ) return values[index];

            boolean value = intersectsExpandedChunk(includedRegions, sectionX, sectionZ)
                    || intersectsExpandedChunk(hidden, sectionX, sectionZ);
            sectionXs[index] = sectionX;
            sectionZs[index] = sectionZ;
            values[index] = value;
            generations[index] = generation;
            return value;
        }

        private static boolean intersectsExpandedChunk(List<BlockRegion> regions,
                                                       int sectionX, int sectionZ) {
            for (BlockRegion region : regions) {
                if (SelectiveRenderState.intersectsExpandedChunk(
                        region, sectionX, sectionZ, VIRTUAL_LIGHT_RADIUS)) return true;
            }
            return false;
        }
    }

    private record SectionCoordinate(int x, int y, int z) { }
}
