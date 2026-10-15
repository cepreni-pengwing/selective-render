package de.selectiverender;

/** Client-local, model-audited structural selectors, independent of server tag synchronization. */
public final class BuiltInBlockTags {
    public static final String BEAMS = "selectiverender:beams";
    private BuiltInBlockTags() { }
    public static boolean isBeam(String blockId) {
        return blockId != null && BeamBlockCatalog.contains(blockId);
    }
}
