package de.selectiverender;

/** Keeps filtered-region lighting opt-in while preserving the fresh-install no-op path. */
public final class VirtualSkyActivation {
    private VirtualSkyActivation() { }

    public static boolean isActive(boolean renderEnabled, boolean hideEnabled,
                                   boolean filteredMode, int filteredRegionCount) {
        return renderEnabled || hideEnabled || (filteredMode && filteredRegionCount > 0);
    }
}
