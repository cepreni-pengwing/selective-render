package de.selectiverender;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigMigrationTest {
    @Test
    void migratesChunkBasedLegacyRegions() {
        BlockRegion region = ConfigMigration.region(-2, 1, null, null, 3, 4, 1);
        assertEquals(new BlockRegion(-32, 31, Integer.MIN_VALUE, Integer.MAX_VALUE, 48, 79), region);
    }

    @Test
    void preservesUnlimitedHeightForHorizontalBlockRegions() {
        BlockRegion region = ConfigMigration.region(2, 8, null, null, 4, 9, 2);
        assertEquals(new BlockRegion(2, 8, Integer.MIN_VALUE, Integer.MAX_VALUE, 4, 9), region);
    }

    @Test
    void preservesThreeDimensionalRegions() {
        BlockRegion region = ConfigMigration.region(2, 8, -64, 320, 4, 9, 7);
        assertEquals(new BlockRegion(2, 8, -64, 320, 4, 9), region);
    }
}
