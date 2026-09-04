package de.selectiverender;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundaryGeometryTest {
    @Test void ordinaryBlockGeometryHasNoExtension() {
        assertEquals(0, ExtensionBounds.mask(0, 1, 0, 1, 0, 1));
    }

    @Test void detectsExtensionsOnEveryAxis() {
        int mask = ExtensionBounds.mask(-0.5f, 1.5f, -1, 2, -0.25f, 1.25f);
        assertEquals(ExtensionBounds.DOWN | ExtensionBounds.UP | ExtensionBounds.NORTH
                | ExtensionBounds.SOUTH | ExtensionBounds.WEST | ExtensionBounds.EAST, mask);
    }

    @Test void ignoresFloatingPointNoiseAtTheBlockBoundary() {
        assertEquals(0, ExtensionBounds.mask(-0.00001f, 1.00001f,
                0, 1, 0, 1));
    }
}
