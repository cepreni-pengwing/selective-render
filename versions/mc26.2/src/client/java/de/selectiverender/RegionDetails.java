package de.selectiverender;

import java.util.ArrayList;
import java.util.List;

/** Read-only metadata used by the Regions settings tab. */
final class RegionDetails {
    private RegionDetails() { }

    static List<String> rows(String regionName) {
        List<String> result = new ArrayList<>();
        boolean hidden = SelectiveRenderConfig.isRegionHidden(regionName);
        boolean selected = hidden ? SelectiveRenderConfig.isHiddenRegionActive(regionName)
                : SelectiveRenderConfig.isRegionActive(regionName);
        boolean enabled = selected && (hidden ? SelectiveRenderConfig.hideGroupEnabled()
                : SelectiveRenderConfig.groupEnabled());
        result.add("Name: " + regionName);
        result.add("Type: " + (hidden ? "Hidden" : "Render"));
        result.add("Selected in group: " + (selected ? "Yes" : "No"));
        result.add("Group enabled: " + ((hidden ? SelectiveRenderConfig.hideGroupEnabled()
                : SelectiveRenderConfig.groupEnabled()) ? "Yes" : "No"));
        result.add("Region effect active: " + (enabled ? "Yes" : "No"));
        result.add("Block filtering: " + (SelectiveRenderSettings.blockFiltersEnabled() ? "On" : "Off"));
        List<BlockRegion> regions = SelectiveRenderConfig.regionBoxes(regionName);
        result.add("Bounding boxes: " + regions.size());
        for (int i = 0; i < regions.size(); i++) {
            BlockRegion region = regions.get(i);
            result.add("Box " + (i + 1) + " minimum: " + region.minX() + ", " + region.minY() + ", " + region.minZ());
            result.add("Box " + (i + 1) + " maximum: " + region.maxX() + ", " + region.maxY() + ", " + region.maxZ());
            result.add("Box " + (i + 1) + " blocks: " + region.blockCount());
        }
        var filters = SelectiveRenderConfig.blockFilters(regionName);
        result.add("Filters: " + filters.size());
        for (BlockFilterRule rule : filters) {
            result.add(rule.mode().name().toLowerCase(java.util.Locale.ROOT) + ": " + rule.selector());
        }
        return result;
    }

}
