package de.selectiverender;

import java.util.List;

/** Conservative proof that no filtered block can affect a terrain light volume. */
public final class VirtualLightBounds {
    private VirtualLightBounds() { }

    public static int visibleCeiling(boolean renderEnabled, List<BlockRegion> visible,
                                     List<BlockRegion> hidden,
                                     int minX, int minY, int minZ, int maxX, int maxZ) {
        // Hidden roofs can cast shadows arbitrarily far down. Do not bound this check in Y.
        for (BlockRegion region : hidden) {
            if (region.maxX() >= minX && region.minX() <= maxX
                    && region.maxZ() >= minZ && region.minZ() <= maxZ
                    && region.maxY() >= minY) return Integer.MIN_VALUE;
        }
        if (!renderEnabled) return Integer.MAX_VALUE;
        int ceiling = Integer.MIN_VALUE;
        for (BlockRegion region : visible) {
            if (region.minX() <= minX && region.maxX() >= maxX
                    && region.minZ() <= minZ && region.maxZ() >= maxZ
                    && region.minY() <= minY) {
                ceiling = Math.max(ceiling, region.maxY());
            }
        }
        // Requiring one enclosing cuboid is conservative for merged/disjoint regions.
        return ceiling;
    }
}
