package de.selectiverender;

import java.util.Set;
import java.util.regex.Pattern;

/** One hide or only predicate attached to one saved region. */
public record BlockFilterRule(Mode mode, Kind kind, String value, boolean enabled) {
    private static final Pattern NAMESPACED_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public enum Mode { HIDE, ONLY }
    public enum Kind { ID, TAG }

    public BlockFilterRule {
        if (mode == null || kind == null || value == null || !NAMESPACED_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Expected a namespaced block id or tag");
        }
    }

    public BlockFilterRule(Mode mode, Kind kind, String value) { this(mode, kind, value, true); }

    public BlockFilterRule withMode(Mode next) { return new BlockFilterRule(next, kind, value, enabled); }
    public BlockFilterRule withEnabled(boolean next) { return new BlockFilterRule(mode, kind, value, next); }
    public BlockFilterRule toggledMode() { return withMode(mode == Mode.HIDE ? Mode.ONLY : Mode.HIDE); }

    public static java.util.List<BlockFilterRule> active(java.util.Collection<BlockFilterRule> rules) {
        return rules.stream().filter(BlockFilterRule::enabled).toList();
    }

    /** Parse the complete batch before modifying any region. */
    public static java.util.List<BlockFilterRule> parse(Mode mode, String input) {
        java.util.LinkedHashSet<BlockFilterRule> result = new java.util.LinkedHashSet<>();
        for (String part : input.split(",", -1)) {
            String value = part.trim().toLowerCase(java.util.Locale.ROOT);
            Kind kind = Kind.ID;
            if (value.startsWith("tag:")) { kind = Kind.TAG; value = value.substring(4); }
            else if (value.startsWith("#")) { kind = Kind.TAG; value = value.substring(1); }
            else if (value.startsWith("id:")) value = value.substring(3);
            result.add(new BlockFilterRule(mode, kind, value));
        }
        return java.util.List.copyOf(result);
    }

    public String selector() { return (kind == Kind.TAG ? "#" : "") + value; }

    public boolean matches(String blockId, Set<String> blockTags) {
        if (kind == Kind.ID) return value.equals(blockId);
        if (BuiltInBlockTags.BEAMS.equals(value)) return BuiltInBlockTags.isBeam(blockId);
        return blockTags.contains(value);
    }

    public static boolean allows(Set<BlockFilterRule> rules, String blockId, Set<String> blockTags) {
        if (rules == null || rules.isEmpty()) return true;
        boolean hasOnly = false;
        boolean matchesOnly = false;
        for (BlockFilterRule rule : rules) {
            if (!rule.enabled) continue;
            boolean matches = rule.matches(blockId, blockTags);
            if (rule.mode == Mode.HIDE && matches) return false;
            if (rule.mode == Mode.ONLY) {
                hasOnly = true;
                matchesOnly |= matches;
            }
        }
        return !hasOnly || matchesOnly;
    }
}
