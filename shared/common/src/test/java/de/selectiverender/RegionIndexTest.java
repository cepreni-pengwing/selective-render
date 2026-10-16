package de.selectiverender;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionIndexTest {
    @Test
    void indexedBlockQueriesMatchLinearRegionLogic() {
        ArrayList<BlockRegion> regions = new ArrayList<>();
        for (int index = -20; index <= 20; index++) {
            int x = index * 37;
            int y = index * 3;
            int z = index * -29;
            regions.add(new BlockRegion(x, x + 19, y, y + 22, z, z + 17));
        }
        RegionIndex index = RegionIndex.of(regions);

        for (int x = -800; x <= 800; x += 13) {
            for (int y = -80; y <= 80; y += 17) {
                for (int z = -650; z <= 650; z += 31) {
                    int queryX = x;
                    int queryY = y;
                    int queryZ = z;
                    boolean expected = regions.stream().anyMatch(
                            region -> region.contains(queryX, queryY, queryZ));
                    assertEquals(expected, index.contains(x, y, z));
                }
            }
        }
    }

    @Test
    void indexedSectionQueriesMatchLinearRegionLogic() {
        List<BlockRegion> regions = List.of(
                new BlockRegion(-33, -2, -17, 4, -48, -1),
                new BlockRegion(0, 15, 0, 15, 0, 15),
                new BlockRegion(100, 170, 30, 90, -20, 70),
                new BlockRegion(500, 900, -64, 320, 500, 900),
                new BlockRegion(-900, -500, -64, 320, -900, -500),
                new BlockRegion(20, 31, 16, 47, 20, 31),
                new BlockRegion(32, 63, 48, 79, 32, 63),
                new BlockRegion(64, 95, 80, 111, 64, 95),
                new BlockRegion(96, 127, 112, 143, 96, 127));
        RegionIndex index = RegionIndex.of(regions);

        for (int sectionX = -60; sectionX <= 60; sectionX++) {
            for (int sectionY = -4; sectionY <= 20; sectionY++) {
                for (int sectionZ = -60; sectionZ <= 60; sectionZ++) {
                    int x = sectionX;
                    int y = sectionY;
                    int z = sectionZ;
                    assertEquals(regions.stream().anyMatch(region -> region.intersectsSection(x, y, z)),
                            index.intersectsSection(x, y, z));
                    assertEquals(regions.stream().anyMatch(region -> region.containsSection(x, y, z)),
                            index.containsSection(x, y, z));
                }
            }
        }
    }
}
