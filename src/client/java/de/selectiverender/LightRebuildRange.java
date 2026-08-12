package de.selectiverender;

final class LightRebuildRange {
    private LightRebuildRange() { }

    static int expandMin(int value, int radius) {
        return (int) Math.max(Integer.MIN_VALUE, (long) value - radius);
    }

    static int expandMax(int value, int radius) {
        return (int) Math.min(Integer.MAX_VALUE, (long) value + radius);
    }
}
