package de.selectiverender;

import java.util.Set;

record FilteredRegion(BlockRegion region, Set<BlockFilterRule> rules) {
    FilteredRegion {
        rules = Set.copyOf(rules);
    }

    boolean allows(int x, int y, int z, String blockId, Set<String> tags) {
        return region.contains(x, y, z) && BlockFilterRule.allows(rules, blockId, tags);
    }
}
