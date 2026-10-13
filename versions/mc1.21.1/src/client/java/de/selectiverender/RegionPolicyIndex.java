package de.selectiverender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Immutable spatial policies, rebuilt only when region data changes. */
final class RegionPolicyIndex {
    private record Policy(RegionIndex index, List<BlockRegion> boxes, RegionOptions options) { }
    private final List<Policy> policies;
    final boolean blackOverride, textureOverride, combinedOverride, culledOverride;
    private RegionPolicyIndex(List<Policy> policies) {
        this.policies = List.copyOf(policies);
        blackOverride = policies.stream().anyMatch(policy -> BoundaryStyle.color(policy.options().boundary()) || policy.options().colorLayer()!=null && policy.options().colorLayer().enabled());
        textureOverride = policies.stream().anyMatch(policy -> BoundaryStyle.texture(policy.options().boundary()) || policy.options().textureLayer()!=null && policy.options().textureLayer().enabled());
        combinedOverride = policies.stream().anyMatch(policy -> policy.options().boundary() == SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE);
        culledOverride = policies.stream().anyMatch(policy -> policy.options().boundary() == SelectiveRenderSettings.BoundaryMode.CULLED || Boolean.FALSE.equals(policy.options().normalFaces()));
    }
    static RegionPolicyIndex of(Map<String, List<BlockRegion>> regions, Map<String, RegionOptions> options) {
        List<Policy> policies = new ArrayList<>();
        regions.forEach((name, boxes) -> {
            RegionOptions value = options.get(name);
            if (value != null && value.hasOverrides()) policies.add(new Policy(RegionIndex.of(boxes), List.copyOf(boxes), value));
        });
        return new RegionPolicyIndex(policies);
    }
    SelectiveRenderSettings.BoundaryMode boundaryOverrideAt(int x, int y, int z) {
        var options=optionsAt(x,y,z);
        if(options.boundary()!=null && options.textureLayer()==null && options.colorLayer()==null && options.normalFaces()==null) return options.boundary();
        return options.boundary()==null && options.textureLayer()==null && options.colorLayer()==null && options.normalFaces()==null ? null
                : options.effectiveBoundary(SelectiveRenderSettings.boundaryMode());
    }
    BlockRegion rainbowBoxAt(int x,int y,int z) {
        for (Policy policy:policies) if (policy.options().inheritedColor()!=null && policy.index().contains(x,y,z))
            for (BlockRegion box:policy.boxes()) if (box.contains(x,y,z)) return box;
        return null;
    }
    Boolean fluidOverrideAt(int x, int y, int z) {
        for (Policy policy : policies) if (policy.options().cullFluids() != null && policy.index().contains(x, y, z)) return policy.options().cullFluids();
        return null;
    }
    RegionOptions optionsAt(int x, int y, int z) {
        RegionOptions result = RegionOptions.DEFAULT;
        for (Policy policy : policies) if (policy.index().contains(x, y, z)) {
            RegionOptions value = policy.options();
            if (result == RegionOptions.DEFAULT) { result = value; continue; }
            if (result.inheritedNormal()==null && value.inheritedNormal()!=null) result=result.withNormal(value.inheritedNormal());
            if (result.inheritedTexture()==null && value.inheritedTexture()!=null) result=result.withTexture(value.inheritedTexture());
            if (result.inheritedColor()==null && value.inheritedColor()!=null) result=result.withColor(value.inheritedColor());
            for (int field = 2; field <= 5; field++) {
                Object setting = switch (field) {
                    case 1 -> value.boundary(); case 2 -> value.cullFluids(); case 3 -> value.interactions();
                    case 4 -> value.inactiveInteractions(); default -> value.hiddenInteractions();
                };
                Object previous = switch (field) {
                    case 1 -> result.boundary(); case 2 -> result.cullFluids(); case 3 -> result.interactions();
                    case 4 -> result.inactiveInteractions(); default -> result.hiddenInteractions();
                };
                if (previous == null && setting != null) result = result.with(field, setting);
            }
        }
        return result;
    }
}
