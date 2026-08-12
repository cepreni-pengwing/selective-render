package de.selectiverender;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisibleOccluderCacheTest {
    @Test
    void computesEachColumnOnceUntilInvalidated() {
        VisibleOccluderCache cache = new VisibleOccluderCache(16);
        AtomicInteger computations = new AtomicInteger();

        assertEquals(42, cache.get(1, 17, -1, (x, z) -> {
            computations.incrementAndGet();
            return 42;
        }));
        assertEquals(42, cache.get(1, 17, -1, (x, z) -> {
            computations.incrementAndGet();
            return 7;
        }));
        assertEquals(1, computations.get());

        cache.invalidate(17, -1);

        assertEquals(7, cache.get(1, 17, -1, (x, z) -> {
            computations.incrementAndGet();
            return 7;
        }));
        assertEquals(2, computations.get());
    }

    @Test
    void keepsColumnsInTheSameChunkIndependent() {
        VisibleOccluderCache cache = new VisibleOccluderCache(16);

        assertEquals(1, cache.get(1, -16, 31, (x, z) -> 1));
        assertEquals(2, cache.get(1, -1, 16, (x, z) -> 2));
        assertEquals(1, cache.get(1, -16, 31, (x, z) -> 9));
        assertEquals(2, cache.get(1, -1, 16, (x, z) -> 9));
    }

    @Test
    void removesUnloadedChunks() {
        VisibleOccluderCache cache = new VisibleOccluderCache(16);
        cache.get(1, 17, 17, (x, z) -> 4);
        assertEquals(1, cache.chunkCount());
        cache.removeChunk(1, 1);
        assertEquals(0, cache.chunkCount());
    }

    @Test
    void staysWithinTheConfiguredChunkBound() {
        VisibleOccluderCache cache = new VisibleOccluderCache(2);
        cache.get(1, 0, 0, (x, z) -> 1);
        cache.get(1, 16, 0, (x, z) -> 2);
        cache.get(1, 32, 0, (x, z) -> 3);
        assertEquals(1, cache.chunkCount());
    }

    @Test
    void doesNotReuseAHeightFromAnotherSnapshotGeneration() {
        VisibleOccluderCache cache = new VisibleOccluderCache(16);
        assertEquals(12, cache.get(1, 0, 0, (x, z) -> 12));
        assertEquals(48, cache.get(2, 0, 0, (x, z) -> 48));
        assertEquals(48, cache.get(2, 0, 0, (x, z) -> 7));
    }
}
