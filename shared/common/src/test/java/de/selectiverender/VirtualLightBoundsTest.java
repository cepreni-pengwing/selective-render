package de.selectiverender;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VirtualLightBoundsTest {
    private static final BlockRegion LARGE = new BlockRegion(0, 999, 0, 99, 0, 999);

    @Test void interiorUsesRegionCeilingButEdgesRequireSolver() {
        assertEquals(99, ceiling(List.of(LARGE), List.of(), 200, 10, 200, 245, 245));
        assertEquals(Integer.MIN_VALUE, ceiling(List.of(LARGE), List.of(), -1, 10, 200, 44, 245));
        assertEquals(Integer.MIN_VALUE, ceiling(List.of(LARGE), List.of(), 200, -1, 200, 245, 245));
    }

    @Test void hiddenRoofsMustRemainRelevantEvenFarAboveTheSection() {
        BlockRegion roof = new BlockRegion(220, 221, 300, 301, 220, 221);
        assertEquals(Integer.MIN_VALUE, ceiling(List.of(LARGE), List.of(roof), 200, 10, 200, 245, 245));
    }

    @Test void hiddenOutsideLightHaloDoesNotPreventFastPath() {
        BlockRegion distant = new BlockRegion(500, 510, 10, 500, 500, 510);
        assertEquals(99, ceiling(List.of(LARGE), List.of(distant), 200, 10, 200, 245, 245));
    }

    @Test void hiddenBelowHaloCannotSendLightBackIntoCore() {
        BlockRegion below = new BlockRegion(220, 221, -10, 9, 220, 221);
        assertEquals(99, ceiling(List.of(LARGE), List.of(below), 200, 10, 200, 245, 245));
    }

    @Test void separateCuboidsCannotProveAnEnclosingVolume() {
        BlockRegion first = new BlockRegion(0, 220, 0, 99, 0, 999);
        BlockRegion second = new BlockRegion(230, 999, 0, 99, 0, 999);
        assertEquals(Integer.MIN_VALUE, ceiling(List.of(first, second), List.of(), 200, 10, 200, 245, 245));
    }

    @Test void hideOnlyModeOutsideHiddenColumnsUsesVanilla() {
        assertEquals(Integer.MAX_VALUE, VirtualLightBounds.visibleCeiling(
                false, List.of(), List.of(), 200, 10, 200, 245, 245));
    }

    private static int ceiling(List<BlockRegion> regions, List<BlockRegion> hidden,
                               int x, int y, int z, int maxX, int maxZ) {
        return VirtualLightBounds.visibleCeiling(true, regions, hidden, x, y, z, maxX, maxZ);
    }
}
