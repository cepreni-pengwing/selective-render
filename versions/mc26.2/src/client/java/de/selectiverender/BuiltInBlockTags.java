package de.selectiverender;

import java.util.Set;

/** Client-local block selectors that must work even when a server does not sync their tags. */
public final class BuiltInBlockTags {
    public static final String BEAMS = "selectiverender:beams";
    private static final Set<String> BEAM_MODS = Set.of("conquest", "architects");

    private BuiltInBlockTags() {}

    /** Includes every registered block from the supported mods whose identifier names it as a beam. */
    public static boolean isBeam(String blockId) {
        if (blockId == null) return false;
        int separator = blockId.indexOf(':');
        return separator > 0 && separator < blockId.length() - 1
                && BEAM_MODS.contains(blockId.substring(0, separator))
                && blockId.substring(separator + 1).contains("beam");
    }
}
