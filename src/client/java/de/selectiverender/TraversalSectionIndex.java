package de.selectiverender;

import java.util.LinkedHashSet;
import java.util.List;

public final class TraversalSectionIndex {
    private static final long MAX_CACHED_SECTIONS = 262_144L;
    private static final TraversalSectionIndex EMPTY = new TraversalSectionIndex(new long[0]);
    private final long[] sectionKeys;

    private TraversalSectionIndex(long[] sectionKeys) {
        this.sectionKeys = sectionKeys;
    }

    static TraversalSectionIndex of(List<BlockRegion> regions) {
        if (regions.isEmpty()) return EMPTY;
        long estimate = 0L;
        for (BlockRegion region : regions) {
            long sizeX = (long) Math.floorDiv(region.maxX(), 16)
                    - Math.floorDiv(region.minX(), 16) + 1L;
            long sizeY = (long) Math.floorDiv(region.maxY(), 16)
                    - Math.floorDiv(region.minY(), 16) + 1L;
            long sizeZ = (long) Math.floorDiv(region.maxZ(), 16)
                    - Math.floorDiv(region.minZ(), 16) + 1L;
            if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0
                    || sizeX > MAX_CACHED_SECTIONS / Math.max(1L, sizeY)
                    || sizeX * sizeY > MAX_CACHED_SECTIONS / Math.max(1L, sizeZ)) {
                return EMPTY;
            }
            estimate += sizeX * sizeY * sizeZ;
            if (estimate > MAX_CACHED_SECTIONS) return EMPTY;
        }

        long[] keys = new long[(int) estimate];
        int size = 0;
        LinkedHashSet<Long> seen = regions.size() > 1
                ? new LinkedHashSet<>((int) estimate) : null;
        for (BlockRegion region : regions) {
            int minX = Math.floorDiv(region.minX(), 16);
            int maxX = Math.floorDiv(region.maxX(), 16);
            int minY = Math.floorDiv(region.minY(), 16);
            int maxY = Math.floorDiv(region.maxY(), 16);
            int minZ = Math.floorDiv(region.minZ(), 16);
            int maxZ = Math.floorDiv(region.maxZ(), 16);
            for (int sectionX = minX; sectionX <= maxX; sectionX++) {
                for (int sectionY = minY; sectionY <= maxY; sectionY++) {
                    for (int sectionZ = minZ; sectionZ <= maxZ; sectionZ++) {
                        long key = pack(sectionX, sectionY, sectionZ);
                        if (seen == null || seen.add(key)) keys[size++] = key;
                    }
                }
            }
        }
        return new TraversalSectionIndex(java.util.Arrays.copyOf(keys, size));
    }

    public boolean isEmpty() { return sectionKeys.length == 0; }
    public int size() { return sectionKeys.length; }
    public long keyAt(int index) { return sectionKeys[index]; }

    private static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFL) << 42
                | ((long) z & 0x3FFFFFL) << 20
                | ((long) y & 0xFFFFFL);
    }
}
