package de.selectiverender;

import java.util.Set;

record FilteredRegion(BlockRegion region, Set<BlockFilterRule> rules) {
    boolean excludesEntities(int x, int y, int z) {
        return region.contains(x, y, z) && rules.stream().anyMatch(rule -> rule.mode() == BlockFilterRule.Mode.ONLY);
    }
    FilteredRegion { rules = Set.copyOf(rules); }
}
