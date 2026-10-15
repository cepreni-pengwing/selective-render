package de.selectiverender;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class RegionIndex {
    private static final int LEAF_SIZE = 8;
    private static final RegionIndex EMPTY = new RegionIndex(List.of(), null);
    private final List<BlockRegion> regions;
    private final Node root;

    private RegionIndex(List<BlockRegion> regions, Node root) {
        this.regions = regions;
        this.root = root;
    }

    static RegionIndex of(List<BlockRegion> regions) {
        if (regions.isEmpty()) return EMPTY;
        List<BlockRegion> snapshot = List.copyOf(regions);
        return new RegionIndex(snapshot, build(new ArrayList<>(snapshot)));
    }

    static RegionIndex empty() {
        return EMPTY;
    }

    List<BlockRegion> regions() {
        return regions;
    }

    boolean contains(int x, int y, int z) {
        return root != null && root.matches(x, x, region -> region.contains(x, y, z));
    }

    boolean intersectsSection(int sectionX, int sectionY, int sectionZ) {
        long minX = (long) sectionX << 4;
        long maxX = minX + 15;
        return root != null && root.matches(minX, maxX,
                region -> region.intersectsSection(sectionX, sectionY, sectionZ));
    }

    boolean containsSection(int sectionX, int sectionY, int sectionZ) {
        long minX = (long) sectionX << 4;
        long maxX = minX + 15;
        return root != null && root.matches(minX, maxX,
                region -> region.containsSection(sectionX, sectionY, sectionZ));
    }

    private static Node build(ArrayList<BlockRegion> regions) {
        if (regions.size() <= LEAF_SIZE) return Node.leaf(List.copyOf(regions));
        regions.sort(Comparator.comparingLong(region ->
                (long) region.minX() + region.maxX()));
        BlockRegion median = regions.get(regions.size() >>> 1);
        int split = (int) (((long) median.minX() + median.maxX()) >> 1);
        ArrayList<BlockRegion> left = new ArrayList<>();
        ArrayList<BlockRegion> crossing = new ArrayList<>();
        ArrayList<BlockRegion> right = new ArrayList<>();
        for (BlockRegion region : regions) {
            if (region.maxX() < split) left.add(region);
            else if (region.minX() > split) right.add(region);
            else crossing.add(region);
        }
        if (crossing.isEmpty()) return Node.leaf(List.copyOf(regions));
        return new Node(split, List.copyOf(crossing),
                left.isEmpty() ? null : build(left),
                right.isEmpty() ? null : build(right));
    }

    private interface RegionPredicate {
        boolean test(BlockRegion region);
    }

    private record Node(int split, List<BlockRegion> crossing, Node left, Node right) {
        private static Node leaf(List<BlockRegion> regions) {
            return new Node(0, regions, null, null);
        }

        private boolean matches(long queryMinX, long queryMaxX, RegionPredicate predicate) {
            for (BlockRegion region : crossing) {
                if (predicate.test(region)) return true;
            }
            if (left == null && right == null) return false;
            return queryMinX < split && left != null && left.matches(queryMinX, queryMaxX, predicate)
                    || queryMaxX > split && right != null && right.matches(queryMinX, queryMaxX, predicate);
        }
    }
}
