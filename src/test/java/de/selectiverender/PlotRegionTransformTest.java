package de.selectiverender;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PlotRegionTransformTest {
    @Test
    void appliesHeightAndHorizontalMarginToEveryPlotPart() {
        List<BlockRegion> transformed = PlotRegionTransform.apply(List.of(
                new BlockRegion(10, 20, 0, 255, -30, -10),
                new BlockRegion(40, 50, 5, 80, 60, 70)), -64, 400, 3);

        assertEquals(List.of(
                new BlockRegion(7, 23, -64, 400, -33, -7),
                new BlockRegion(37, 53, -64, 400, 57, 73)), transformed);
    }

    @Test
    void returnsOriginalSnapshotWithoutRequestedChanges() {
        List<BlockRegion> regions = List.of(new BlockRegion(0, 1, 2, 3, 4, 5));
        assertSame(regions, PlotRegionTransform.apply(regions, null, null, 0));
    }

    @Test
    void clampsMarginsAtCoordinateLimits() {
        List<BlockRegion> transformed = PlotRegionTransform.apply(List.of(
                new BlockRegion(Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1,
                        0, 1, Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1)), null, null, 10);

        assertEquals(Integer.MIN_VALUE, transformed.get(0).minX());
        assertEquals(Integer.MAX_VALUE, transformed.get(0).maxX());
        assertEquals(Integer.MIN_VALUE, transformed.get(0).minZ());
        assertEquals(Integer.MAX_VALUE, transformed.get(0).maxZ());
    }

    @Test
    void negativeMarginShrinksAPlot() {
        assertEquals(List.of(new BlockRegion(2, 7, -100, 400, 2, 7)),
                PlotRegionTransform.apply(List.of(
                        new BlockRegion(0, 9, 0, 255, 0, 9)), -100, 400, -2));
    }

    @Test
    void negativeMarginTreatsAdjacentPartsAsOneUnion() {
        assertEquals(List.of(new BlockRegion(2, 17, 0, 10, 2, 7)),
                PlotRegionTransform.apply(List.of(
                        new BlockRegion(0, 9, 0, 10, 0, 9),
                        new BlockRegion(10, 19, 0, 10, 0, 9)), null, null, -2));
    }

    @Test
    void negativeMarginPreservesTheErodedShapeOfAnIrregularUnion() {
        List<BlockRegion> transformed = PlotRegionTransform.apply(List.of(
                new BlockRegion(0, 9, 0, 10, 0, 9),
                new BlockRegion(10, 19, 0, 10, 0, 4)), null, null, -1);

        assertEquals(List.of(
                new BlockRegion(1, 18, 0, 10, 1, 3),
                new BlockRegion(1, 8, 0, 10, 4, 8)), transformed);
    }

    @Test
    void negativeMarginRejectsACompletelyErasedPlot() {
        assertEquals(List.of(), PlotRegionTransform.apply(List.of(
                new BlockRegion(0, 2, 0, 10, 0, 2)), null, null, -2));
    }

    @Test
    void compressedErosionMatchesABlockAccurateReference() {
        Random random = new Random(0x5352);
        for (int sample = 0; sample < 100; sample++) {
            List<BlockRegion> source = java.util.stream.IntStream.range(0, 1 + random.nextInt(5))
                    .mapToObj(ignored -> {
                        int minX = random.nextInt(12) - 6;
                        int minZ = random.nextInt(12) - 6;
                        return new BlockRegion(minX, minX + random.nextInt(6), 0, 10,
                                minZ, minZ + random.nextInt(6));
                    }).toList();
            int radius = 1 + random.nextInt(3);
            List<BlockRegion> transformed = PlotRegionTransform.apply(source, null, null, -radius);
            for (int x = -10; x <= 12; x++) {
                for (int z = -10; z <= 12; z++) {
                    boolean expected = true;
                    for (int dx = -radius; dx <= radius && expected; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            int checkX = x + dx;
                            int checkZ = z + dz;
                            if (source.stream().noneMatch(region -> region.contains(checkX, 0, checkZ))) {
                                expected = false;
                                break;
                            }
                        }
                    }
                    int checkX = x;
                    int checkZ = z;
                    boolean actual = transformed.stream().anyMatch(region -> region.contains(checkX, 0, checkZ));
                    assertEquals(expected, actual, "sample=" + sample + ", x=" + x + ", z=" + z);
                }
            }
        }
    }
}
