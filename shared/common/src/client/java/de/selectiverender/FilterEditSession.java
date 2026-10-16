package de.selectiverender;

import java.util.HashMap;
import java.util.Map;

/** Keeps a row's buttons targeting its newly confirmed selector until widgets rebuild. */
final class FilterEditSession {
    private final Map<BlockFilterRule, BlockFilterRule> aliases = new HashMap<>();
    void clear() { aliases.clear(); }
    BlockFilterRule current(BlockFilterRule original) {
        BlockFilterRule result = aliases.getOrDefault(original, original);
        if (result == null) throw new IllegalArgumentException("This filter was removed");
        return result;
    }
    void replaced(BlockFilterRule previous, BlockFilterRule replacement) {
        aliases.replaceAll((key, value) -> previous.equals(value) ? replacement : value);
        aliases.put(previous, replacement);
    }
}
