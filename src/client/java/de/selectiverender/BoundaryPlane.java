package de.selectiverender;

final class BoundaryPlane {
    private static final float EPSILON = 0.0001f;
    private static final int MAX_STEP = 31;
    static final int NO_PLANE = Integer.MIN_VALUE;

    private BoundaryPlane() { }

    static int integralPlane(float minimum, float maximum) {
        if (Math.abs(maximum - minimum) > EPSILON) return NO_PLANE;
        float plane = (minimum + maximum) * 0.5f;
        int rounded = Math.round(plane);
        if (Math.abs(plane - rounded) > EPSILON) return NO_PLANE;
        return rounded >= 1 - MAX_STEP && rounded <= MAX_STEP ? rounded : NO_PLANE;
    }
}
