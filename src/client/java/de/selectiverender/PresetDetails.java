package de.selectiverender;

import java.util.ArrayList;
import java.util.List;

/** Read-only metadata used by the Presets settings tab. */
final class PresetDetails {
    private PresetDetails() { }

    static List<String> rows(String preset) {
        List<String> result = new ArrayList<>();
        boolean hidden = SelectiveRenderConfig.isPresetHidden(preset);
        boolean selected = hidden ? SelectiveRenderConfig.isHiddenPresetActive(preset)
                : SelectiveRenderConfig.isPresetActive(preset);
        boolean enabled = selected && (hidden ? SelectiveRenderConfig.hideGroupEnabled()
                : SelectiveRenderConfig.groupEnabled());
        result.add("Name: " + preset);
        result.add("Type: " + (hidden ? "Hidden" : "Render"));
        result.add("Selected in group: " + (selected ? "Yes" : "No"));
        result.add("Group enabled: " + ((hidden ? SelectiveRenderConfig.hideGroupEnabled()
                : SelectiveRenderConfig.groupEnabled()) ? "Yes" : "No"));
        result.add("Region effect active: " + (enabled ? "Yes" : "No"));
        result.add("Block filtering: " + (SelectiveRenderSettings.blockFiltersEnabled() ? "On" : "Off"));
        List<BlockRegion> regions = SelectiveRenderConfig.presetRegions(preset);
        result.add("Bounding boxes: " + regions.size());
        for (int i = 0; i < regions.size(); i++) {
            BlockRegion region = regions.get(i);
            result.add("Box " + (i + 1) + " minimum: " + region.minX() + ", " + region.minY() + ", " + region.minZ());
            result.add("Box " + (i + 1) + " maximum: " + region.maxX() + ", " + region.maxY() + ", " + region.maxZ());
            result.add("Box " + (i + 1) + " blocks: " + region.blockCount());
        }
        var filters = SelectiveRenderConfig.blockFilters(preset);
        result.add("Filters: " + filters.size());
        for (BlockFilterRule rule : filters) {
            result.add(rule.mode().name().toLowerCase(java.util.Locale.ROOT) + ": " + rule.selector());
        }
        return result;
    }

}
