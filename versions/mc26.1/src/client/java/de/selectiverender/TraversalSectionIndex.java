package de.selectiverender;

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
        PrimitiveLongSet seen = regions.size() > 1
                ? new PrimitiveLongSet((int) estimate) : null;
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

    private static final class PrimitiveLongSet {
        private final long[] table;
        private final int mask;
        private boolean containsZero;

        private PrimitiveLongSet(int expectedSize) {
            int capacity = 2;
            while (capacity < expectedSize * 2) capacity <<= 1;
            table = new long[capacity];
            mask = capacity - 1;
        }

        private boolean add(long value) {
            if (value == 0L) {
                if (containsZero) return false;
                containsZero = true;
                return true;
            }
            int index = mix(value) & mask;
            while (table[index] != 0L) {
                if (table[index] == value) return false;
                index = (index + 1) & mask;
            }
            table[index] = value;
            return true;
        }

        private static int mix(long value) {
            value ^= value >>> 33;
            value *= 0xff51afd7ed558ccdL;
            value ^= value >>> 33;
            value *= 0xc4ceb9fe1a85ec53L;
            return (int) (value ^ value >>> 32);
        }
    }
}
