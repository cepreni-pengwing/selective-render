package de.selectiverender;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.IntBinaryOperator;

final class VisibleOccluderCache {
    private final ConcurrentHashMap<Long, ColumnHeights> chunks = new ConcurrentHashMap<>();
    private final int maximumChunks;

    VisibleOccluderCache(int maximumChunks) {
        this.maximumChunks = maximumChunks;
    }

    int get(int generation, int blockX, int blockZ, IntBinaryOperator computer) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        long chunkKey = chunkKey(chunkX, chunkZ);
        ColumnHeights columns = chunks.get(chunkKey);
        if (columns == null) {
            if (chunks.mappingCount() >= maximumChunks) chunks.clear();
            columns = chunks.computeIfAbsent(chunkKey, ignored -> new ColumnHeights());
        }
        return columns.get(generation, blockX & 15, blockZ & 15, blockX, blockZ, computer);
    }

    void invalidate(int blockX, int blockZ) {
        ColumnHeights columns = chunks.get(chunkKey(blockX >> 4, blockZ >> 4));
        if (columns != null) columns.invalidate(blockX & 15, blockZ & 15);
    }

    void clear() {
        chunks.clear();
    }

    void removeChunk(int chunkX, int chunkZ) {
        chunks.remove(chunkKey(chunkX, chunkZ));
    }

    int chunkCount() {
        return chunks.size();
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xffffffffL) | ((chunkZ & 0xffffffffL) << 32);
    }

    private static final class ColumnHeights {
        private final AtomicLongArray heights = new AtomicLongArray(256);

        private int get(int generation, int localX, int localZ,
                        int blockX, int blockZ, IntBinaryOperator computer) {
            int index = (localZ << 4) | localX;
            long cached = heights.get(index);
            if (generation(cached) == generation) return height(cached);
            int computed = computer.applyAsInt(blockX, blockZ);
            long update = pack(generation, computed);
            if (heights.compareAndSet(index, cached, update)) return computed;
            long winner = heights.get(index);
            return generation(winner) == generation ? height(winner) : computed;
        }

        private void invalidate(int localX, int localZ) {
            heights.set((localZ << 4) | localX, 0L);
        }

        private static long pack(int generation, int height) {
            return ((long) generation << 32) | (height & 0xffffffffL);
        }

        private static int generation(long packed) {
            return (int) (packed >>> 32);
        }

        private static int height(long packed) {
            return (int) packed;
        }
    }
}
