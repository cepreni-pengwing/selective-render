package de.selectiverender;

import org.junit.jupiter.api.Test;

import java.util.List;

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
}
