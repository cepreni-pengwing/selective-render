package de.selectiverender;

import java.util.Set;

record FilteredRegion(BlockRegion region, Set<BlockFilterRule> rules) {
    static final BlockRegion EVERYWHERE = new BlockRegion(-30_000_000, 30_000_000, -1_048_576, 1_048_576, -30_000_000, 30_000_000);
    static FilteredRegion everywhere(Set<BlockFilterRule> rules) { return new FilteredRegion(EVERYWHERE, rules); }
    boolean global() { return region.equals(EVERYWHERE); }
    FilteredRegion {
        rules = Set.copyOf(rules);
    }

    boolean excludesEntities(int x, int y, int z) {
        return region.contains(x, y, z) && rules.stream().anyMatch(rule -> rule.mode() == BlockFilterRule.Mode.ONLY);
    }

    boolean allows(int x, int y, int z, String blockId, Set<String> tags) {
        return region.contains(x, y, z) && BlockFilterRule.allows(rules, blockId, tags);
    }
}
