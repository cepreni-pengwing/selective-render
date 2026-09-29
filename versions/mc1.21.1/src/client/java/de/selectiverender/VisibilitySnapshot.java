package de.selectiverender;

import java.util.ArrayList;
import java.util.List;

final class VisibilitySnapshot {
    static final VisibilitySnapshot EMPTY = create(
            List.of(), false, List.of(), false, List.of(), List.of(), false, false, 1);

    private final List<BlockRegion> configuredRegions;
    private final List<BlockRegion> hiddenRegions;
    private final List<BlockRegion> visibleOverrides;
    private final List<BlockRegion> plotRegions;
    private final List<BlockRegion> activeRegions;
    private final List<BlockRegion> traversalRegions;
    private final List<BlockRegion> visibleRegions;
    private final RegionIndex activeRegionIndex;
    private final RegionIndex hiddenRegionIndex;
    private final RegionIndex overrideRegionIndex;
    private final TraversalSectionIndex traversalSectionIndex;
    private final boolean configuredEnabled;
    private final boolean hideConfiguredEnabled;
    private final boolean plotModeActive;
    private final boolean plotRenderingConfigured;
    private final boolean enabled;
    private final boolean hideEnabled;
    private final int generation;

    private VisibilitySnapshot(List<BlockRegion> configuredRegions,
                               List<BlockRegion> hiddenRegions,
                               List<BlockRegion> visibleOverrides,
                               List<BlockRegion> plotRegions,
                               List<BlockRegion> activeRegions,
                               List<BlockRegion> traversalRegions,
                               List<BlockRegion> visibleRegions,
                               RegionIndex activeRegionIndex,
                               RegionIndex hiddenRegionIndex,
                               RegionIndex overrideRegionIndex,
                               TraversalSectionIndex traversalSectionIndex,
                               boolean configuredEnabled,
                               boolean hideConfiguredEnabled,
                               boolean plotModeActive,
                               boolean plotRenderingConfigured,
                               boolean enabled,
                               boolean hideEnabled,
                               int generation) {
        this.configuredRegions = configuredRegions;
        this.hiddenRegions = hiddenRegions;
        this.visibleOverrides = visibleOverrides;
        this.plotRegions = plotRegions;
        this.activeRegions = activeRegions;
        this.traversalRegions = traversalRegions;
        this.visibleRegions = visibleRegions;
        this.activeRegionIndex = activeRegionIndex;
        this.hiddenRegionIndex = hiddenRegionIndex;
        this.overrideRegionIndex = overrideRegionIndex;
        this.traversalSectionIndex = traversalSectionIndex;
        this.configuredEnabled = configuredEnabled;
        this.hideConfiguredEnabled = hideConfiguredEnabled;
        this.plotModeActive = plotModeActive;
        this.plotRenderingConfigured = plotRenderingConfigured;
        this.enabled = enabled;
        this.hideEnabled = hideEnabled;
        this.generation = generation;
    }

    static VisibilitySnapshot create(List<BlockRegion> configuredRegions,
                                     boolean configuredEnabled,
                                     List<BlockRegion> hiddenRegions,
                                     boolean hideConfiguredEnabled,
                                     List<BlockRegion> visibleOverrides,
                                     List<BlockRegion> plotRegions,
                                     boolean plotModeActive,
                                     boolean plotRenderingConfigured,
                                     int generation) {
        List<BlockRegion> configured = List.copyOf(configuredRegions);
        List<BlockRegion> hidden = List.copyOf(hiddenRegions);
        List<BlockRegion> overrides = List.copyOf(visibleOverrides);
        List<BlockRegion> plots = List.copyOf(plotRegions);
        List<BlockRegion> active = plotModeActive ? plots : configured;
        boolean enabled = plotModeActive
                ? plotRenderingConfigured && !plots.isEmpty()
                : configuredEnabled && !configured.isEmpty();
        boolean hideEnabled = hideConfiguredEnabled && !hidden.isEmpty();

        List<BlockRegion> traversal = active;
        List<BlockRegion> visible = active;
        if (!overrides.isEmpty()) {
            ArrayList<BlockRegion> combined = new ArrayList<>(active.size() + overrides.size());
            combined.addAll(active);
            combined.addAll(overrides);
            traversal = List.copyOf(combined);
            visible = enabled ? traversal : active;
        }

        return new VisibilitySnapshot(configured, hidden, overrides, plots, active,
                traversal, visible, RegionIndex.of(active), RegionIndex.of(hidden),
                RegionIndex.of(overrides), TraversalSectionIndex.of(traversal),
                configuredEnabled, hideConfiguredEnabled,
                plotModeActive, plotRenderingConfigured, enabled, hideEnabled, generation);
    }

    VisibilitySnapshot withSavedState(List<BlockRegion> configuredRegions,
                                      boolean configuredEnabled,
                                      List<BlockRegion> hiddenRegions,
                                      boolean hideConfiguredEnabled,
                                      List<BlockRegion> visibleOverrides,
                                      int generation) {
        return create(configuredRegions, configuredEnabled, hiddenRegions, hideConfiguredEnabled,
                visibleOverrides, plotRegions, plotModeActive, plotRenderingConfigured, generation);
    }

    VisibilitySnapshot withPlotState(List<BlockRegion> plotRegions,
                                     boolean plotModeActive,
                                     boolean plotRenderingConfigured,
                                     int generation) {
        return create(configuredRegions, configuredEnabled, hiddenRegions, hideConfiguredEnabled,
                visibleOverrides, plotRegions, plotModeActive, plotRenderingConfigured, generation);
    }

    VisibilitySnapshot toggleConfiguredState(int generation) {
        return create(configuredRegions, !configuredEnabled, hiddenRegions, hideConfiguredEnabled,
                visibleOverrides, plotRegions, plotModeActive, plotRenderingConfigured, generation);
    }

    List<BlockRegion> configuredRegions() { return configuredRegions; }
    List<BlockRegion> hiddenRegions() { return hiddenRegions; }
    List<BlockRegion> visibleOverrides() { return visibleOverrides; }
    List<BlockRegion> plotRegions() { return plotRegions; }
    List<BlockRegion> activeRegions() { return activeRegions; }
    List<BlockRegion> traversalRegions() { return traversalRegions; }
    List<BlockRegion> visibleRegions() { return visibleRegions; }
    RegionIndex activeRegionIndex() { return activeRegionIndex; }
    RegionIndex hiddenRegionIndex() { return hiddenRegionIndex; }
    RegionIndex overrideRegionIndex() { return overrideRegionIndex; }
    TraversalSectionIndex traversalSectionIndex() { return traversalSectionIndex; }
    boolean enabled() { return enabled; }
    boolean hideEnabled() { return hideEnabled; }
    boolean plotModeActive() { return plotModeActive; }
    boolean plotRenderingEnabled() { return plotModeActive && plotRenderingConfigured; }
    int generation() { return generation; }
}
