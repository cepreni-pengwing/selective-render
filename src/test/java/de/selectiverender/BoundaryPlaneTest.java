package de.selectiverender;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoundaryPlaneTest {
    @Test void rejectsNonFiniteCoordinates() {
        assertEquals(BoundaryPlane.NO_PLANE, BoundaryPlane.integralPlane(Float.NaN, Float.NaN));
        assertEquals(BoundaryPlane.NO_PLANE, BoundaryPlane.integralPlane(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY));
    }
    @Test void findsNormalAndTranslatedNegativeCutPlanes() {
        assertEquals(0, BoundaryPlane.integralPlane(0, 0));
        assertEquals(-1, BoundaryPlane.integralPlane(-1, -1));
    }

    @Test void findsNormalAndTranslatedPositiveCutPlanes() {
        assertEquals(1, BoundaryPlane.integralPlane(1, 1));
        assertEquals(2, BoundaryPlane.integralPlane(2, 2));
    }

    @Test void ignoresThreeDimensionalAndFractionalGeometry() {
        assertEquals(BoundaryPlane.NO_PLANE, BoundaryPlane.integralPlane(-1, 0));
        assertEquals(BoundaryPlane.NO_PLANE, BoundaryPlane.integralPlane(0, 1));
        assertEquals(BoundaryPlane.NO_PLANE, BoundaryPlane.integralPlane(-0.5f, -0.5f));
    }

    @Test void toleratesSmallVertexNoiseOnAPlane() {
        assertEquals(0, BoundaryPlane.integralPlane(-0.00001f, 0.00001f));
    }

    @Test void ignoresUnreasonablyDistantModelPlanes() {
        assertEquals(BoundaryPlane.NO_PLANE, BoundaryPlane.integralPlane(-31, -31));
        assertEquals(BoundaryPlane.NO_PLANE, BoundaryPlane.integralPlane(32, 32));
    }
}
