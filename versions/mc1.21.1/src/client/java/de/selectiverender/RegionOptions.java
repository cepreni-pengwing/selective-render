package de.selectiverender;

/** Nullable values inherit the global policy; filter rules remain stored when disabled. */
public record RegionOptions(Boolean filtersEnabled, SelectiveRenderSettings.BoundaryMode boundary,
                            Boolean cullFluids, SelectiveRenderSettings.InteractionMode interactions,
                            Boolean inactiveInteractions, Boolean hiddenInteractions,
                            TextureLayer textureLayer, ColorLayer colorLayer, Boolean normalFaces) {
    public record TextureLayer(boolean enabled, String id) { }
    public record ColorLayer(boolean enabled, Integer rgb, Integer opacity, Boolean lighting, Boolean rainbow) {
        public ColorLayer { if (rgb != null) rgb &= 0xFFFFFF; if (opacity != null) opacity = BoundaryStyle.opacity(opacity); }
    }
    public RegionOptions(Boolean filtersEnabled, SelectiveRenderSettings.BoundaryMode boundary, Boolean cullFluids,
                         SelectiveRenderSettings.InteractionMode interactions, Boolean inactiveInteractions, Boolean hiddenInteractions) {
        this(filtersEnabled, boundary, cullFluids, interactions, inactiveInteractions, hiddenInteractions, null, null);
    }
    public RegionOptions(Boolean filtersEnabled, SelectiveRenderSettings.BoundaryMode boundary, Boolean cullFluids,
                         SelectiveRenderSettings.InteractionMode interactions, Boolean inactiveInteractions, Boolean hiddenInteractions,
                         TextureLayer textureLayer, ColorLayer colorLayer) {
        this(filtersEnabled,boundary,cullFluids,interactions,inactiveInteractions,hiddenInteractions,textureLayer,colorLayer,null);
    }
    public RegionOptions {
        // Earlier regional choices were spatial policies. Local overrides now use a binary choice.
        if (interactions == SelectiveRenderSettings.InteractionMode.INSIDE) interactions = SelectiveRenderSettings.InteractionMode.EVERYWHERE;
        if (interactions == SelectiveRenderSettings.InteractionMode.OUTSIDE) interactions = SelectiveRenderSettings.InteractionMode.NONE;
    }
    public static final RegionOptions DEFAULT = new RegionOptions(null, null, null, null, null, null);
    public boolean hasOverrides() {
        return boundary != null || cullFluids != null || interactions != null
                || inactiveInteractions != null || hiddenInteractions != null || textureLayer != null || colorLayer != null || normalFaces != null;
    }
    public boolean filtersActive() { return !Boolean.FALSE.equals(filtersEnabled); }
    public RegionOptions with(int field, Object value) {
        return new RegionOptions(field == 0 ? (Boolean) value : filtersEnabled,
                field == 1 ? (SelectiveRenderSettings.BoundaryMode) value : boundary,
                field == 2 ? (Boolean) value : cullFluids,
                field == 3 ? (SelectiveRenderSettings.InteractionMode) value : interactions,
                field == 4 ? (Boolean) value : inactiveInteractions,
                field == 5 ? (Boolean) value : hiddenInteractions,
                field == 1 ? null : textureLayer, field == 1 ? null : colorLayer, field == 1 ? null : normalFaces);
    }
    public TextureLayer inheritedTexture() {
        return textureLayer != null ? textureLayer : boundary == null ? null : new TextureLayer(BoundaryStyle.texture(boundary), null);
    }
    public ColorLayer inheritedColor() {
        return colorLayer != null ? colorLayer : boundary == null ? null : new ColorLayer(BoundaryStyle.color(boundary) || boundary == SelectiveRenderSettings.BoundaryMode.CULLED,
                null, boundary == SelectiveRenderSettings.BoundaryMode.CULLED ? 0 : null, null, null);
    }
    public RegionOptions withTexture(TextureLayer value) {
        return new RegionOptions(filtersEnabled, null, cullFluids, interactions, inactiveInteractions, hiddenInteractions, value, inheritedColor(), inheritedNormal());
    }
    public RegionOptions withColor(ColorLayer value) {
        return new RegionOptions(filtersEnabled, null, cullFluids, interactions, inactiveInteractions, hiddenInteractions, inheritedTexture(), value, inheritedNormal());
    }
    public Boolean inheritedNormal() {
        return normalFaces!=null?normalFaces:boundary==SelectiveRenderSettings.BoundaryMode.NORMAL?Boolean.TRUE:
                boundary==SelectiveRenderSettings.BoundaryMode.CULLED?Boolean.FALSE:null;
    }
    public RegionOptions withNormal(Boolean value) {
        return new RegionOptions(filtersEnabled,null,cullFluids,interactions,inactiveInteractions,hiddenInteractions,inheritedTexture(),inheritedColor(),value);
    }
    public SelectiveRenderSettings.BoundaryMode effectiveBoundary(SelectiveRenderSettings.BoundaryMode global) {
        var texture = inheritedTexture(); var color = inheritedColor();
        if (texture == null && color == null && inheritedNormal() == null) return global;
        var mode = BoundaryStyle.layers(color == null ? BoundaryStyle.color(global) : color.enabled(), texture == null ? BoundaryStyle.texture(global) : texture.enabled());
        return mode == SelectiveRenderSettings.BoundaryMode.NORMAL && !(inheritedNormal()==null?SelectiveRenderSettings.boundaryNormal():inheritedNormal()) ? SelectiveRenderSettings.BoundaryMode.CULLED : mode;
    }
}
