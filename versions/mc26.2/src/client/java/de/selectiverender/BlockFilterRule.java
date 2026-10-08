package de.selectiverender;

import java.util.Set;
import java.util.regex.Pattern;

/** One hide or only predicate attached to one saved region. */
public record BlockFilterRule(Mode mode, Kind kind, String value) {
    private static final Pattern NAMESPACED_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    public enum Mode { HIDE, ONLY }
    public enum Kind { ID, TAG }
    public BlockFilterRule {
        if (mode == null || kind == null || value == null || !NAMESPACED_ID.matcher(value).matches())
            throw new IllegalArgumentException("Expected a namespaced block id or tag");
    }
    public boolean matches(String blockId, Set<String> blockTags) {
        if (kind == Kind.ID) return value.equals(blockId);
        if (BuiltInBlockTags.BEAMS.equals(value)) return BuiltInBlockTags.isBeam(blockId);
        return blockTags.contains(value);
    }
    public static boolean allows(Set<BlockFilterRule> rules, String blockId, Set<String> blockTags) {
        if (rules == null || rules.isEmpty()) return true;
        boolean hasOnly = false, matchesOnly = false;
        for (BlockFilterRule rule : rules) {
            boolean matches = rule.matches(blockId, blockTags);
            if (rule.mode == Mode.HIDE && matches) return false;
            if (rule.mode == Mode.ONLY) { hasOnly = true; matchesOnly |= matches; }
        }
        return !hasOnly || matchesOnly;
    }
}
