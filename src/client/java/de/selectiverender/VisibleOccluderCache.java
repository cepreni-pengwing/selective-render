package de.selectiverender;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.IntBinaryOperator;

final class VisibleOccluderCache {
    private static final int UNKNOWN = Integer.MAX_VALUE;
    private final ConcurrentHashMap<Long, ColumnHeights> chunks = new ConcurrentHashMap<>();
    private final int maximumChunks;

    VisibleOccluderCache(int maximumChunks) {
        this.maximumChunks = maximumChunks;
    }

    int get(int blockX, int blockZ, IntBinaryOperator computer) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        long chunkKey = chunkKey(chunkX, chunkZ);
        ColumnHeights columns = chunks.get(chunkKey);
        if (columns == null) {
            if (chunks.mappingCount() >= maximumChunks) chunks.clear();
            columns = chunks.computeIfAbsent(chunkKey, ignored -> new ColumnHeights());
        }
        return columns.get(blockX & 15, blockZ & 15, blockX, blockZ, computer);
    }

    void invalidate(int blockX, int blockZ) {
        ColumnHeights columns = chunks.get(chunkKey(blockX >> 4, blockZ >> 4));
        if (columns != null) columns.invalidate(blockX & 15, blockZ & 15);
    }

    void clear() {
        chunks.clear();
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xffffffffL) | ((chunkZ & 0xffffffffL) << 32);
    }

    private static final class ColumnHeights {
        private final AtomicIntegerArray heights = new AtomicIntegerArray(256);

        private ColumnHeights() {
            for (int index = 0; index < heights.length(); index++) heights.set(index, UNKNOWN);
        }

        private int get(int localX, int localZ, int blockX, int blockZ, IntBinaryOperator computer) {
            int index = (localZ << 4) | localX;
            int cached = heights.get(index);
            if (cached != UNKNOWN) return cached;
            int computed = computer.applyAsInt(blockX, blockZ);
            if (heights.compareAndSet(index, UNKNOWN, computed)) return computed;
            return heights.get(index);
        }

        private void invalidate(int localX, int localZ) {
            heights.set((localZ << 4) | localX, UNKNOWN);
        }
    }
}
