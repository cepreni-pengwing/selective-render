package de.selectiverender;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldEditSelectionTest {
    private WorldEditSelection complete() {
        WorldEditSelection selection = new WorldEditSelection();
        selection.accept("s|cuboid");
        selection.accept("p|0|10|20|-5|100");
        selection.accept("p|1|-2|-64|7|100");
        return selection;
    }

    @Test void normalizesAllAxesAndIncludesBothCorners() {
        assertEquals(new BlockRegion(-2, 10, -64, 20, -5, 7), complete().region());
    }

    @Test void changingOneCornerKeepsTheOther() {
        var selection = complete();
        selection.accept("p|1|11|21|8|100");
        assertEquals(new BlockRegion(10, 11, 20, 21, -5, 8), selection.region());
    }

    @Test void shapeUpdateClearsOldCornersAndRequiresBothNewOnes() {
        var selection = complete();
        selection.accept("s|cuboid");
        selection.accept("p|0|0|0|0|-1");
        assertNull(selection.region());
        selection.accept("p|1|0|0|0|1");
        assertEquals(1L, selection.region().blockCount());
    }

    @Test void otherShapesAreNotApproximatedByTheirBoundingBoxes() {
        var selection = complete();
        for (String shape : new String[]{"polygon2d", "ellipsoid", "cylinder", "polyhedron"}) {
            selection.accept("s|" + shape);
            selection.accept("p|0|0|0|0|8");
            selection.accept("p|1|1|1|1|8");
            assertNull(selection.region());
            assertTrue(selection.problem().contains("cuboid"));
        }
    }

    @Test void multiRegionOverlaysDoNotReplacePrimarySelection() {
        var selection = complete();
        BlockRegion before = selection.region();
        selection.accept("+s|cuboid");
        selection.accept("+p|0|900|900|900|1");
        selection.accept("col|0|255|255|255");
        assertEquals(before, selection.region());
    }

    @Test void malformedPointsCannotLeaveAStaleImportableSelection() {
        for (String packet : new String[]{"p|0|NaN|0|0|1", "p|0|Infinity|0|0|1",
                "p|0|0.5|0|0|1", "p|0|2147483648|0|0|1", "p|-1|0|0|0|1",
                "p|2|0|0|0|1", "p|0|0|0", "p|0|0|0|0|oops", null, "x".repeat(4097)}) {
            var selection = complete();
            selection.accept(packet);
            assertNull(selection.region(), packet);
        }
    }

    @Test void disconnectOrWorldSwitchClearsSelection() {
        var selection = complete();
        selection.clear();
        assertNull(selection.region());
        selection.accept("p|0|1|2|3|1");
        selection.accept("p|1|1|2|3|1");
        assertNull(selection.region());
    }

    @Test void incompleteUpdateInvalidatesThePreviousOtherCorner() {
        var selection = complete();
        selection.accept("p|0|1|2|3|-1");
        assertNull(selection.region());
    }

    @Test void acceptsIntegralDecimalCoordinates() {
        var selection = new WorldEditSelection();
        selection.accept("s|cuboid");
        selection.accept("p|0|-1.0|-64.0|0.0|-1");
        selection.accept("p|1|-1.0|-64.0|0.0|1");
        assertEquals(new BlockRegion(-1,-1,-64,-64,0,0), selection.region());
    }
}
