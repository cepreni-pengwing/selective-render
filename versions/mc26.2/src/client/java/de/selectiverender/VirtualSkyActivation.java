package de.selectiverender;

/** Active filters have lighting independently of interaction restrictions; empty fresh installs stay no-op. */
public final class VirtualSkyActivation {
    private VirtualSkyActivation() { }

    public static boolean isActive(boolean renderEnabled, boolean hideEnabled,
                                   boolean filteredMode, int filteredRegionCount) {
        return renderEnabled || hideEnabled || filteredRegionCount > 0;
    }
}
