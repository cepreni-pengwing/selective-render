package de.selectiverender;

final class RenderReloadPolicy {
    private RenderReloadPolicy() { }

    static boolean requiresFullReload(int affectedSections, int loadedSections, int sectionThreshold) {
        return affectedSections > sectionThreshold || affectedSections > loadedSections * 0.85;
    }
}
