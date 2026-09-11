package de.selectiverender;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

record VisibilityRefreshPlan(List<BlockRegion> changedRegions, boolean scanComplement) {
    static VisibilityRefreshPlan between(VisibilitySnapshot before, VisibilitySnapshot after) {
        List<BlockRegion> changed = new ArrayList<>();
        addDifference(changed, before.enabled() ? before.visibleRegions() : List.of(),
                after.enabled() ? after.visibleRegions() : List.of());
        addDifference(changed, before.hideEnabled() ? before.hiddenRegions() : List.of(),
                after.hideEnabled() ? after.hiddenRegions() : List.of());
        return new VisibilityRefreshPlan(List.copyOf(changed), before.enabled() != after.enabled());
    }

    boolean isEmpty() { return changedRegions.isEmpty() && !scanComplement; }

    static boolean sectionChanged(SectionVisibility before, SectionVisibility after) {
        return before != after || before == SectionVisibility.PARTIAL;
    }

    private static void addDifference(List<BlockRegion> changed,
                                      List<BlockRegion> before, List<BlockRegion> after) {
        Set<BlockRegion> oldRegions = new HashSet<>(before);
        Set<BlockRegion> newRegions = new HashSet<>(after);
        for (BlockRegion region : oldRegions) if (!newRegions.contains(region)) changed.add(region);
        for (BlockRegion region : newRegions) if (!oldRegions.contains(region)) changed.add(region);
    }
}
