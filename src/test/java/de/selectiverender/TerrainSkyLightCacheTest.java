package de.selectiverender;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainSkyLightCacheTest {
    private final TerrainSkyLightCache cache = new TerrainSkyLightCache();
    private final Object world = new Object();
    private TerrainSkyLightCache.Key key(int generation, int policy) {
        return new TerrainSkyLightCache.Key(world, generation, policy, -14, -20, -14, 29, 27, 29);
    }
    private TerrainSkyLightCache.Ticket capture(int generation, int policy) {
        Object context = new Object();
        cache.capture(context, generation, policy);
        return cache.ticket(context);
    }

    @Test void identicalOpticsReuseAnImmutableResultAcrossMeshJobs() {
        byte[] light = {15, 8, 0};
        cache.put(key(1, 0), capture(1, 0), light, light.length);
        light[0] = 0; // Worker scratch arrays are reused on the next job.
        assertArrayEquals(new byte[] {15, 8, 0}, cache.get(key(1, 0), capture(1, 0)).light());
    }

    @Test void unrelatedColumnsDoNotInvalidateButHaloAndRoofColumnsDo() {
        var key = key(1, 0);
        cache.put(key, capture(1, 0), new byte[] {15}, 1);
        cache.invalidateColumn(100, 100);
        assertNotNull(cache.get(key, capture(1, 0)));
        cache.invalidateColumn(-1, 0); // Any height, including a roof above this volume.
        assertNull(cache.get(key, capture(1, 0)));
    }

    @Test void oldSnapshotsCannotReuseOrPublishAfterAnOpticalUpdate() {
        var key = key(1, 0);
        var old = capture(1, 0);
        cache.invalidateColumn(0, 0);
        cache.put(key, old, new byte[] {15}, 1);
        assertNull(cache.get(key, capture(1, 0)));
        cache.put(key, capture(1, 0), new byte[] {8}, 1);
        assertNull(cache.get(key, old));
        assertEquals(8, cache.get(key, capture(1, 0)).light()[0]);
    }

    @Test void settingsVisibilityAndWorldsHaveSeparateResults() {
        cache.put(key(1, 0), capture(1, 0), new byte[] {15}, 1);
        assertNull(cache.get(key(2, 0), capture(2, 0)));
        assertNull(cache.get(key(1, 1), capture(1, 1)));
        assertNull(cache.get(new TerrainSkyLightCache.Key(new Object(), 1, 0,
                -14, -20, -14, 29, 27, 29), capture(1, 0)));
        cache.put(key(2, 0), capture(1, 0), new byte[] {15}, 1);
        assertNull(cache.get(key(2, 0), capture(2, 0)));
    }

    @Test void disconnectRejectsOutstandingWorkerTickets() {
        var ticket = capture(1, 0);
        cache.clear();
        cache.put(key(1, 0), ticket, new byte[] {15}, 1);
        assertNull(cache.get(key(1, 0), capture(1, 0)));
    }

    @Test void vanillaProofsAreCachedAndInvalidatedToo() {
        cache.put(key(1, 0), capture(1, 0), null, 0);
        assertTrue(cache.get(key(1, 0), capture(1, 0)).vanilla());
        cache.invalidateColumn(1, 1);
        assertNull(cache.get(key(1, 0), capture(1, 0)));
    }

    @Test void cacheEvictionBoundsLargeLightVolumes() {
        byte[] light = new byte[1024 * 1024];
        for (int generation = 0; generation < 20; generation++) {
            cache.put(key(generation, 0), capture(generation, 0), light, light.length);
        }
        assertNull(cache.get(key(0, 0), capture(0, 0)));
        assertNotNull(cache.get(key(19, 0), capture(19, 0)));
    }

    @Test void opaqueNeighborsNeverNeedAnExpensiveShapeCheck() {
        assertFalse(VirtualLightPropagation.canPass(15, 0, 15));
        assertFalse(VirtualLightPropagation.canPass(14, 10, 4));
        assertTrue(VirtualLightPropagation.canPass(15, 0, 0));
        assertTrue(VirtualLightPropagation.canPass(15, 9, 4));
    }
}
