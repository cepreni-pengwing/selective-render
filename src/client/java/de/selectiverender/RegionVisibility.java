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
        boolean included = visibleOverrides.stream().anyMatch(region ->
                region.intersectsSection(sectionX, sectionY, sectionZ))
                || !whitelistEnabled || includedRegions.stream().anyMatch(region ->
                region.intersectsSection(sectionX, sectionY, sectionZ));
        boolean fullyHidden = hiddenRegions.stream().anyMatch(region ->
                region.containsSection(sectionX, sectionY, sectionZ));
        return included && !fullyHidden;
    }

    private static boolean contains(List<BlockRegion> regions, int x, int y, int z) {
        return regions.stream().anyMatch(region -> region.contains(x, y, z));
    }
}
