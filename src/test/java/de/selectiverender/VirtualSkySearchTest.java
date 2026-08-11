package de.selectiverender;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VirtualSkySearchTest {
    @Test
    void findsSkyAroundAnOverhang() {
        int light = VirtualSkySearch.find(0, 0, 0, 14,
                (x, y, z) -> x >= 2,
                (x, y, z) -> !(x == 1 && y == 0 && z == 0));
        assertEquals(12, light);
    }

    @Test
    void treatsFilteredBlocksAsPassable() {
        int light = VirtualSkySearch.find(0, 0, 0, 14,
                (x, y, z) -> y >= 2,
                (x, y, z) -> true);
        assertEquals(13, light);
    }

    @Test
    void preservesCompleteVisibleEnclosure() {
        int light = VirtualSkySearch.find(0, 0, 0, 14,
                (x, y, z) -> Math.abs(x) + Math.abs(y) + Math.abs(z) > 1,
                (x, y, z) -> false);
        assertEquals(0, light);
    }
}
