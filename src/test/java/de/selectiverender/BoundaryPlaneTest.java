package de.selectiverender;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoundaryPlaneTest {
    @Test void findsNormalAndTranslatedNegativeCutPlanes() {
        assertEquals(1, BoundaryPlane.negativeStep(0, 0));
        assertEquals(2, BoundaryPlane.negativeStep(-1, -1));
    }

    @Test void findsNormalAndTranslatedPositiveCutPlanes() {
        assertEquals(1, BoundaryPlane.positiveStep(1, 1));
        assertEquals(2, BoundaryPlane.positiveStep(2, 2));
    }

    @Test void ignoresThreeDimensionalAndFractionalGeometry() {
        assertEquals(0, BoundaryPlane.negativeStep(-1, 0));
        assertEquals(0, BoundaryPlane.positiveStep(0, 1));
        assertEquals(0, BoundaryPlane.negativeStep(-0.5f, -0.5f));
    }

    @Test void toleratesSmallVertexNoiseOnAPlane() {
        assertEquals(1, BoundaryPlane.negativeStep(-0.00001f, 0.00001f));
    }

    @Test void ignoresUnreasonablyDistantModelPlanes() {
        assertEquals(0, BoundaryPlane.negativeStep(-31, -31));
        assertEquals(0, BoundaryPlane.positiveStep(32, 32));
    }
}
