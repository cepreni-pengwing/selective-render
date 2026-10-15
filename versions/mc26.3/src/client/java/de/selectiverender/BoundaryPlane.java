package de.selectiverender;

final class BoundaryPlane {
    private static final float EPSILON = 0.0001f;
    private static final int MAX_STEP = 31;
    static final int NO_PLANE = Integer.MIN_VALUE;

    private BoundaryPlane() { }

    /** Decorative geometry extending along a face is not the block's boundary surface. */
    static boolean faceExtent(float minA, float maxA, float minB, float maxB) {
        return Float.isFinite(minA) && Float.isFinite(maxA) && Float.isFinite(minB) && Float.isFinite(maxB)
                && minA >= -EPSILON && maxA <= 1 + EPSILON
                && minB >= -EPSILON && maxB <= 1 + EPSILON
                && maxA - minA > EPSILON && maxB - minB > EPSILON;
    }

    static int integralPlane(float minimum, float maximum) {
        if (!Float.isFinite(minimum) || !Float.isFinite(maximum)) return NO_PLANE;
        if (Math.abs(maximum - minimum) > EPSILON) return NO_PLANE;
        float plane = (minimum + maximum) * 0.5f;
        int rounded = Math.round(plane);
        if (Math.abs(plane - rounded) > EPSILON) return NO_PLANE;
        return rounded >= 1 - MAX_STEP && rounded <= MAX_STEP ? rounded : NO_PLANE;
    }
}
