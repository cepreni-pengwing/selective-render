package de.selectiverender;

import java.util.List;

final class RegionVisibility {
    private RegionVisibility() { }

    static boolean block(boolean whitelistEnabled, List<BlockRegion> includedRegions,
                         List<BlockRegion> hiddenRegions, List<BlockRegion> visibleOverrides,
                         int blockX, int blockY, int blockZ) {
        boolean includedByOverride = contains(visibleOverrides, blockX, blockY, blockZ);
        if (!includedByOverride && whitelistEnabled
                && !contains(includedRegions, blockX, blockY, blockZ)) return false;
        return !contains(hiddenRegions, blockX, blockY, blockZ);
    }

    static boolean section(boolean whitelistEnabled, List<BlockRegion> includedRegions,
                           List<BlockRegion> hiddenRegions, List<BlockRegion> visibleOverrides,
                           int sectionX, int sectionY, int sectionZ) {
        return classifySection(whitelistEnabled, includedRegions, hiddenRegions, visibleOverrides,
                sectionX, sectionY, sectionZ) != SectionVisibility.HIDDEN;
    }

    static SectionVisibility classifySection(boolean whitelistEnabled, List<BlockRegion> includedRegions,
                                               List<BlockRegion> hiddenRegions,
                                               List<BlockRegion> visibleOverrides,
                                               int sectionX, int sectionY, int sectionZ) {
        boolean overrideIntersects = intersects(visibleOverrides, sectionX, sectionY, sectionZ);
        boolean includedIntersects = intersects(includedRegions, sectionX, sectionY, sectionZ);
        if (whitelistEnabled && !overrideIntersects && !includedIntersects) {
            return SectionVisibility.HIDDEN;
        }
        if (containsSection(hiddenRegions, sectionX, sectionY, sectionZ)) {
            return SectionVisibility.HIDDEN;
        }

        boolean baseFullyVisible = !whitelistEnabled
                || containsSection(visibleOverrides, sectionX, sectionY, sectionZ)
                || containsSection(includedRegions, sectionX, sectionY, sectionZ);
        boolean hiddenIntersects = intersects(hiddenRegions, sectionX, sectionY, sectionZ);
        return baseFullyVisible && !hiddenIntersects
                ? SectionVisibility.UNCHANGED
                : SectionVisibility.PARTIAL;
    }

    private static boolean contains(List<BlockRegion> regions, int x, int y, int z) {
        return regions.stream().anyMatch(region -> region.contains(x, y, z));
    }

    private static boolean intersects(List<BlockRegion> regions, int sectionX, int sectionY, int sectionZ) {
        for (BlockRegion region : regions) {
            if (region.intersectsSection(sectionX, sectionY, sectionZ)) return true;
        }
        return false;
    }

    private static boolean containsSection(List<BlockRegion> regions,
                                           int sectionX, int sectionY, int sectionZ) {
        for (BlockRegion region : regions) {
            if (region.containsSection(sectionX, sectionY, sectionZ)) return true;
        }
        return false;
    }
}
