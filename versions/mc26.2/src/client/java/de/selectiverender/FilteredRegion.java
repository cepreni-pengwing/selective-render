package de.selectiverender;

import java.util.Set;

record FilteredRegion(BlockRegion region, Set<BlockFilterRule> rules) {
    FilteredRegion { rules = Set.copyOf(rules); }
}
