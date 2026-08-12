package de.selectiverender;

import java.util.List;

final class PlotRegionTransform {
    private PlotRegionTransform() { }

    static List<BlockRegion> apply(List<BlockRegion> regions, Integer minY, Integer maxY,
                                   int xzMargin) {
        if (minY == null && maxY == null && xzMargin == 0) return regions;
        return regions.stream().map(region -> new BlockRegion(
                subtractClamped(region.minX(), xzMargin),
                addClamped(region.maxX(), xzMargin),
                minY == null ? region.minY() : minY,
                maxY == null ? region.maxY() : maxY,
                subtractClamped(region.minZ(), xzMargin),
                addClamped(region.maxZ(), xzMargin))).toList();
    }

    private static int subtractClamped(int value, int amount) {
        return (int) Math.max(Integer.MIN_VALUE, (long) value - amount);
    }

    private static int addClamped(int value, int amount) {
        return (int) Math.min(Integer.MAX_VALUE, (long) value + amount);
    }
}
