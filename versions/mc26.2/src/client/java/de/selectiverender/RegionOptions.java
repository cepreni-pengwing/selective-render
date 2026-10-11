package de.selectiverender;

/** Nullable values inherit the global policy; filter rules remain stored when disabled. */
public record RegionOptions(Boolean filtersEnabled, SelectiveRenderSettings.BoundaryMode boundary,
                            Boolean cullFluids, SelectiveRenderSettings.InteractionMode interactions,
                            Boolean inactiveInteractions, Boolean hiddenInteractions) {
    public static final RegionOptions DEFAULT = new RegionOptions(null, null, null, null, null, null);
    public boolean hasOverrides() {
        return boundary != null || cullFluids != null || interactions != null
                || inactiveInteractions != null || hiddenInteractions != null;
    }
    public boolean filtersActive() { return !Boolean.FALSE.equals(filtersEnabled); }
    public RegionOptions with(int field, Object value) {
        return new RegionOptions(field == 0 ? (Boolean) value : filtersEnabled,
                field == 1 ? (SelectiveRenderSettings.BoundaryMode) value : boundary,
                field == 2 ? (Boolean) value : cullFluids,
                field == 3 ? (SelectiveRenderSettings.InteractionMode) value : interactions,
                field == 4 ? (Boolean) value : inactiveInteractions,
                field == 5 ? (Boolean) value : hiddenInteractions);
    }
}
