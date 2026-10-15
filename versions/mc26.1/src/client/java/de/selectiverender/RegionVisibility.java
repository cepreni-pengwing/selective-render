package de.selectiverender;

import java.util.List;

final class RegionVisibility {
    private RegionVisibility() { }

    static boolean block(boolean whitelistEnabled, List<BlockRegion> includedRegions,
                         List<BlockRegion> hiddenRegions, List<BlockRegion> visibleOverrides,
                         int blockX, int blockY, int blockZ) {
        return block(whitelistEnabled, RegionIndex.of(includedRegions), RegionIndex.of(hiddenRegions),
                RegionIndex.of(visibleOverrides), blockX, blockY, blockZ);
    }

    static boolean block(boolean whitelistEnabled, RegionIndex includedRegions,
                         RegionIndex hiddenRegions, RegionIndex visibleOverrides,
                         int blockX, int blockY, int blockZ) {
        boolean includedByOverride = visibleOverrides.contains(blockX, blockY, blockZ);
        if (!includedByOverride && whitelistEnabled
                && !includedRegions.contains(blockX, blockY, blockZ)) return false;
        return !hiddenRegions.contains(blockX, blockY, blockZ);
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
        return classifySection(whitelistEnabled, RegionIndex.of(includedRegions),
                RegionIndex.of(hiddenRegions), RegionIndex.of(visibleOverrides),
                sectionX, sectionY, sectionZ);
    }

    static SectionVisibility classifySection(boolean whitelistEnabled, RegionIndex includedRegions,
                                               RegionIndex hiddenRegions,
                                               RegionIndex visibleOverrides,
                                               int sectionX, int sectionY, int sectionZ) {
        boolean overrideIntersects = visibleOverrides.intersectsSection(sectionX, sectionY, sectionZ);
        boolean includedIntersects = includedRegions.intersectsSection(sectionX, sectionY, sectionZ);
        if (whitelistEnabled && !overrideIntersects && !includedIntersects) {
            return SectionVisibility.HIDDEN;
        }
        if (hiddenRegions.containsSection(sectionX, sectionY, sectionZ)) {
            return SectionVisibility.HIDDEN;
        }

        boolean baseFullyVisible = !whitelistEnabled
                || visibleOverrides.containsSection(sectionX, sectionY, sectionZ)
                || includedRegions.containsSection(sectionX, sectionY, sectionZ);
        boolean hiddenIntersects = hiddenRegions.intersectsSection(sectionX, sectionY, sectionZ);
        return baseFullyVisible && !hiddenIntersects
                ? SectionVisibility.FULL_VISIBLE
                : SectionVisibility.PARTIAL;
    }

}
