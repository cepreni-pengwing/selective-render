package de.selectiverender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Immutable spatial policies, rebuilt only when region data changes. */
final class RegionPolicyIndex {
    private record Policy(RegionIndex index, RegionOptions options) { }
    private final List<Policy> policies;
    final boolean blackOverride, textureOverride, culledOverride;
    private RegionPolicyIndex(List<Policy> policies) {
        this.policies = List.copyOf(policies);
        blackOverride = policies.stream().anyMatch(policy -> policy.options().boundary() == SelectiveRenderSettings.BoundaryMode.COLOR);
        textureOverride = policies.stream().anyMatch(policy -> policy.options().boundary() == SelectiveRenderSettings.BoundaryMode.TEXTURE);
        culledOverride = policies.stream().anyMatch(policy -> policy.options().boundary() == SelectiveRenderSettings.BoundaryMode.CULLED);
    }
    static RegionPolicyIndex of(Map<String, List<BlockRegion>> regions, Map<String, RegionOptions> options) {
        List<Policy> policies = new ArrayList<>();
        regions.forEach((name, boxes) -> {
            RegionOptions value = options.get(name);
            if (value != null && value.hasOverrides()) policies.add(new Policy(RegionIndex.of(boxes), value));
        });
        return new RegionPolicyIndex(policies);
    }
    SelectiveRenderSettings.BoundaryMode boundaryOverrideAt(int x, int y, int z) {
        for (Policy policy : policies) if (policy.options().boundary() != null && policy.index().contains(x, y, z)) return policy.options().boundary();
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
            for (int field = 1; field <= 5; field++) {
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
