package de.selectiverender;

final class RenderReloadPolicy {
    private RenderReloadPolicy() { }

    static boolean requiresFullReload(int affectedSections, int loadedSections) {
        return affectedSections > 4096 || affectedSections > loadedSections * 0.60;
    }
}
