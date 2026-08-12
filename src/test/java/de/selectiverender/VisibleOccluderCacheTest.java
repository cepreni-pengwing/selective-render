package de.selectiverender;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisibleOccluderCacheTest {
    @Test
    void computesEachColumnOnceUntilInvalidated() {
        VisibleOccluderCache cache = new VisibleOccluderCache(16);
        AtomicInteger computations = new AtomicInteger();

        assertEquals(42, cache.get(17, -1, (x, z) -> {
            computations.incrementAndGet();
            return 42;
        }));
        assertEquals(42, cache.get(17, -1, (x, z) -> {
            computations.incrementAndGet();
            return 7;
        }));
        assertEquals(1, computations.get());

        cache.invalidate(17, -1);

        assertEquals(7, cache.get(17, -1, (x, z) -> {
            computations.incrementAndGet();
            return 7;
        }));
        assertEquals(2, computations.get());
    }

    @Test
    void keepsColumnsInTheSameChunkIndependent() {
        VisibleOccluderCache cache = new VisibleOccluderCache(16);

        assertEquals(1, cache.get(-16, 31, (x, z) -> 1));
        assertEquals(2, cache.get(-1, 16, (x, z) -> 2));
        assertEquals(1, cache.get(-16, 31, (x, z) -> 9));
        assertEquals(2, cache.get(-1, 16, (x, z) -> 9));
    }
}
