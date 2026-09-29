package de.selectiverender;

final class RenderReloadPolicy {
    private RenderReloadPolicy() { }

    static boolean requiresFullReload(int affectedSections, int sectionThreshold) {
        return affectedSections > sectionThreshold;
    }
}
