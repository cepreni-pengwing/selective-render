package de.selectiverender;

/** Active filters have lighting independently of interaction restrictions; empty fresh installs stay no-op. */
public final class VirtualSkyActivation {
    private VirtualSkyActivation() { }

    /** A negative sample is the no-skylight/no-influence sentinel, never a direct-sky proof. */
    public static boolean canUseDirectSky(int sample, boolean topLighting) {
        return sample >= 0 && topLighting;
    }

    public static boolean isActive(boolean renderEnabled, boolean hideEnabled,
                                   boolean filteredMode, int filteredRegionCount) {
        return renderEnabled || hideEnabled || filteredRegionCount > 0;
    }
}
