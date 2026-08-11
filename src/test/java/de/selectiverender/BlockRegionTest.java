package de.selectiverender;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockRegionTest {
    @Test
    void validatesInclusiveBoundsAndBlockCount() {
        BlockRegion region = new BlockRegion(-1, 1, 4, 5, 10, 12);
        assertTrue(region.contains(-1, 4, 10));
        assertTrue(region.contains(1, 5, 12));
        assertFalse(region.contains(2, 5, 12));
        assertEquals(18, region.blockCount());
        assertThrows(IllegalArgumentException.class, () -> new BlockRegion(1, 0, 0, 0, 0, 0));
    }

    @Test
    void handlesNegativeSectionCoordinates() {
        BlockRegion region = new BlockRegion(-16, -1, 0, 15, -32, -17);
        assertTrue(region.containsSection(-1, 0, -2));
        assertTrue(region.intersectsSection(-1, 0, -2));
        assertFalse(region.intersectsSection(0, 0, -2));
    }
}
