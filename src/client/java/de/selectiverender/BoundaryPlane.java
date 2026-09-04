package de.selectiverender;

final class BoundaryPlane {
    private static final float EPSILON = 0.0001f;
    private static final int MAX_STEP = 31;

    private BoundaryPlane() { }

    static int negativeStep(float minimum, float maximum) {
        Integer plane = integralPlane(minimum, maximum);
        if (plane == null || plane > 0) return 0;
        int step = 1 - plane;
        return step <= MAX_STEP ? step : 0;
    }

    static int positiveStep(float minimum, float maximum) {
        Integer plane = integralPlane(minimum, maximum);
        if (plane == null || plane < 1) return 0;
        return plane <= MAX_STEP ? plane : 0;
    }

    private static Integer integralPlane(float minimum, float maximum) {
        if (Math.abs(maximum - minimum) > EPSILON) return null;
        float plane = (minimum + maximum) * 0.5f;
        int rounded = Math.round(plane);
        return Math.abs(plane - rounded) <= EPSILON ? rounded : null;
    }
}
