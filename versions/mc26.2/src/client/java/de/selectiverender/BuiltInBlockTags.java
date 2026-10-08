package de.selectiverender;

import java.util.Set;

/** Client-local block selectors that must work even when a server does not sync their tags. */
public final class BuiltInBlockTags {
    public static final String BEAMS = "selectiverender:beams";
    private static final Set<String> BEAM_MODS = Set.of("conquest", "architects");

    private BuiltInBlockTags() {}

    /** Includes beams, lintels, poles, and stripped-log beam variants from the supported building mods. */
    public static boolean isBeam(String blockId) {
        if (blockId == null) return false;
        int separator = blockId.indexOf(':');
        if (separator <= 0 || separator >= blockId.length() - 1) return false;
        String namespace = blockId.substring(0, separator);
        String path = blockId.substring(separator + 1);
        if (!BEAM_MODS.contains(namespace)) return false;
        if (path.contains("beam") || path.contains("lintel") || path.contains("pole")) return true;

        // Architects names some diagonal/thin stripped-log beam shapes without "beam" in the ID.
        return namespace.equals("architects")
                && (path.startsWith("diagonal_stripped_")
                || path.startsWith("half_diagonal_stripped_")
                || path.startsWith("steeper_diagonal_stripped_")
                || path.startsWith("thin_stripped_"));
    }
}
