package de.selectiverender;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

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

    private Supplier<Object[]> sourceStates(AtomicInteger loads) {
        return () -> {
            loads.incrementAndGet();
            return new Object[4096];
        };
    }

    @Test void interiorReuseRequiresHaloAndCurrentRevisionAndPreservesVoxelLayout() {
        var key=new TerrainSkyLightCache.Key(world,1,0,-16,-16,-16,31,31,31);
        byte[] light=new byte[48*48*48];
        for(int y=0;y<48;y++) for(int z=0;z<48;z++) for(int x=0;x<48;x++)
            light[(y*48+z)*48+x]=(byte)((x+2*y+3*z)%16);
        cache.put(key,capture(1,0),light,light.length);
        byte[] core=cache.copyInterior(world,1,0,0,0,0,-64,319);
        assertNotNull(core);
        for(int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++)
            assertEquals(light[((y+16)*48+z+16)*48+x+16],core[(y*16+z)*16+x]);
        assertNull(cache.copyInterior(world,1,0,4,0,0,-64,319));
        assertNull(cache.copyInterior(new Object(),1,0,0,0,0,-64,319));
        assertNull(cache.copyInterior(world,2,0,0,0,0,-64,319));
        assertNull(cache.copyInterior(world,1,1,0,0,0,-64,319));
        cache.invalidateColumn(0,0);
        assertNull(cache.copyInterior(world,1,0,0,0,0,-64,319));
    }

    @Test void reusedCoreRespectsClippedWorldHeightAndIsAnIndependentCopy() {
        var key=new TerrainSkyLightCache.Key(world,1,0,-16,0,-16,31,31,31);
        byte[] light=new byte[48*32*48]; java.util.Arrays.fill(light,(byte)15);
        cache.put(key,capture(1,0),light,light.length);
        byte[] core=cache.copyInterior(world,1,0,0,0,0,0,31);
        assertNotNull(core); assertEquals(15,core[0]); core[0]=0;
        assertEquals(15,cache.copyInterior(world,1,0,0,0,0,0,31)[0]);
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

    @Test void sourceSectionsAreReusedUntilTheirOwnChunkChanges() {
        AtomicInteger loads = new AtomicInteger();
        var firstTicket = capture(1, 0);
        var first = cache.sourceSection(world, 1, 0, 0, 2, 0, firstTicket, sourceStates(loads));
        assertNotNull(first);

        cache.invalidateColumn(10, 10);
        var unrelatedUpdateTicket = capture(1, 0);
        var reused = cache.sourceSection(world, 1, 0, 0, 2, 0,
                unrelatedUpdateTicket, sourceStates(loads));
        assertSame(first, reused);
        assertEquals(1, loads.get());

        cache.invalidateColumn(0, 0);
        var changedChunkTicket = capture(1, 0);
        var rebuilt = cache.sourceSection(world, 1, 0, 0, 2, 0,
                changedChunkTicket, sourceStates(loads));
        assertNotSame(first, rebuilt);
        assertEquals(2, loads.get());
    }

    @Test void sourceSectionBuiltAcrossAnUpdateIsNotPublished() {
        AtomicInteger loads = new AtomicInteger();
        var staleTicket = capture(1, 0);
        cache.sourceSection(world, 1, 0, 0, 2, 0, staleTicket, () -> {
            cache.invalidateColumn(0, 0);
            return sourceStates(loads).get();
        });

        var currentTicket = capture(1, 0);
        cache.sourceSection(world, 1, 0, 0, 2, 0, currentTicket, sourceStates(loads));
        assertEquals(2, loads.get());
    }
}
