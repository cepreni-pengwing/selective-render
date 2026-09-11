package de.selectiverender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

final class PlotRegionTransform {
    private PlotRegionTransform() { }

    static List<BlockRegion> apply(List<BlockRegion> regions, Integer minY, Integer maxY,
                                   int xzMargin) {
        if (regions.isEmpty()) return regions;
        if (minY == null && maxY == null && xzMargin == 0) return regions;
        if (xzMargin < 0) return erodeUnion(regions, minY, maxY, -(long) xzMargin);
        return regions.stream().map(region -> new BlockRegion(
                subtractClamped(region.minX(), xzMargin),
                addClamped(region.maxX(), xzMargin),
                minY == null ? region.minY() : minY,
                maxY == null ? region.maxY() : maxY,
                subtractClamped(region.minZ(), xzMargin),
                addClamped(region.maxZ(), xzMargin))).toList();
    }

    /** Erodes the complete X/Z union, so adjacent PlotSquared parts do not create inner seams. */
    private static List<BlockRegion> erodeUnion(List<BlockRegion> regions, Integer requestedMinY,
                                                 Integer requestedMaxY, long radius) {
        long[] sourceX = sourceEdges(regions, true);
        long[] sourceZ = sourceEdges(regions, false);
        boolean[][] covered = coverage(regions, sourceX, sourceZ);
        int[][] uncoveredPrefix = uncoveredPrefix(covered);

        long domainMinX = sourceX[0] + radius;
        long domainMaxX = sourceX[sourceX.length - 1] - radius;
        long domainMinZ = sourceZ[0] + radius;
        long domainMaxZ = sourceZ[sourceZ.length - 1] - radius;
        if (domainMinX >= domainMaxX || domainMinZ >= domainMaxZ) return List.of();

        long[] outputX = outputEdges(sourceX, domainMinX, domainMaxX, radius);
        long[] outputZ = outputEdges(sourceZ, domainMinZ, domainMaxZ, radius);
        boolean[][] retained = new boolean[outputX.length - 1][outputZ.length - 1];
        for (int x = 0; x < retained.length; x++) {
            long blockX = outputX[x];
            for (int z = 0; z < retained[x].length; z++) {
                long blockZ = outputZ[z];
                retained[x][z] = fullyCovered(blockX - radius, blockX + radius + 1,
                        blockZ - radius, blockZ + radius + 1,
                        sourceX, sourceZ, uncoveredPrefix);
            }
        }

        int minY = requestedMinY == null
                ? regions.stream().mapToInt(BlockRegion::minY).min().orElseThrow() : requestedMinY;
        int maxY = requestedMaxY == null
                ? regions.stream().mapToInt(BlockRegion::maxY).max().orElseThrow() : requestedMaxY;
        return mergeCells(retained, outputX, outputZ, minY, maxY);
    }

    private static long[] sourceEdges(List<BlockRegion> regions, boolean xAxis) {
        TreeSet<Long> edges = new TreeSet<>();
        for (BlockRegion region : regions) {
            edges.add((long) (xAxis ? region.minX() : region.minZ()));
            edges.add((long) (xAxis ? region.maxX() : region.maxZ()) + 1L);
        }
        return edges.stream().mapToLong(Long::longValue).toArray();
    }

    private static boolean[][] coverage(List<BlockRegion> regions, long[] xs, long[] zs) {
        boolean[][] result = new boolean[xs.length - 1][zs.length - 1];
        for (BlockRegion region : regions) {
            int fromX = Arrays.binarySearch(xs, (long) region.minX());
            int toX = Arrays.binarySearch(xs, (long) region.maxX() + 1L);
            int fromZ = Arrays.binarySearch(zs, (long) region.minZ());
            int toZ = Arrays.binarySearch(zs, (long) region.maxZ() + 1L);
            for (int x = fromX; x < toX; x++) Arrays.fill(result[x], fromZ, toZ, true);
        }
        return result;
    }

    private static int[][] uncoveredPrefix(boolean[][] covered) {
        int[][] prefix = new int[covered.length + 1][covered[0].length + 1];
        for (int x = 0; x < covered.length; x++) {
            for (int z = 0; z < covered[x].length; z++) {
                prefix[x + 1][z + 1] = prefix[x][z + 1] + prefix[x + 1][z]
                        - prefix[x][z] + (covered[x][z] ? 0 : 1);
            }
        }
        return prefix;
    }

    private static long[] outputEdges(long[] source, long domainMin, long domainMax, long radius) {
        TreeSet<Long> edges = new TreeSet<>();
        edges.add(domainMin);
        edges.add(domainMax);
        for (long edge : source) {
            addWithin(edges, edge - radius, domainMin, domainMax);
            addWithin(edges, edge + radius, domainMin, domainMax);
        }
        return edges.stream().mapToLong(Long::longValue).toArray();
    }

    private static void addWithin(TreeSet<Long> edges, long value, long min, long max) {
        if (value > min && value < max) edges.add(value);
    }

    private static boolean fullyCovered(long minX, long maxX, long minZ, long maxZ,
                                         long[] xs, long[] zs, int[][] prefix) {
        int fromX = containingCell(xs, minX);
        int toX = containingCell(xs, maxX - 1) + 1;
        int fromZ = containingCell(zs, minZ);
        int toZ = containingCell(zs, maxZ - 1) + 1;
        int uncovered = prefix[toX][toZ] - prefix[fromX][toZ]
                - prefix[toX][fromZ] + prefix[fromX][fromZ];
        return uncovered == 0;
    }

    private static int containingCell(long[] edges, long value) {
        int index = Arrays.binarySearch(edges, value);
        if (index >= 0) return Math.min(index, edges.length - 2);
        return -index - 2;
    }

    private static List<BlockRegion> mergeCells(boolean[][] retained, long[] xs, long[] zs,
                                                 int minY, int maxY) {
        Map<Run, MutableRectangle> active = new LinkedHashMap<>();
        List<BlockRegion> result = new ArrayList<>();
        for (int z = 0; z < zs.length - 1; z++) {
            Map<Run, MutableRectangle> next = new LinkedHashMap<>();
            int x = 0;
            while (x < xs.length - 1) {
                if (!retained[x][z]) { x++; continue; }
                int start = x++;
                while (x < xs.length - 1 && retained[x][z]) x++;
                Run run = new Run(xs[start], xs[x]);
                MutableRectangle rectangle = active.remove(run);
                if (rectangle == null) rectangle = new MutableRectangle(run.minX, run.maxX, zs[z], zs[z + 1]);
                else rectangle.maxZ = zs[z + 1];
                next.put(run, rectangle);
            }
            active.values().forEach(rectangle -> result.add(rectangle.toRegion(minY, maxY)));
            active = next;
        }
        active.values().forEach(rectangle -> result.add(rectangle.toRegion(minY, maxY)));
        return List.copyOf(result);
    }

    private static int subtractClamped(int value, int amount) {
        return (int) Math.max(Integer.MIN_VALUE, (long) value - amount);
    }

    private static int addClamped(int value, int amount) {
        return (int) Math.min(Integer.MAX_VALUE, (long) value + amount);
    }

    private record Run(long minX, long maxX) { }

    private static final class MutableRectangle {
        private final long minX;
        private final long maxX;
        private final long minZ;
        private long maxZ;

        private MutableRectangle(long minX, long maxX, long minZ, long maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        private BlockRegion toRegion(int minY, int maxY) {
            return new BlockRegion((int) minX, (int) (maxX - 1), minY, maxY,
                    (int) minZ, (int) (maxZ - 1));
        }
    }
}
