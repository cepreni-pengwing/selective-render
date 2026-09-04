package de.selectiverender;

final class ExtensionBounds {
    static final int DOWN = 1;
    static final int UP = 1 << 1;
    static final int NORTH = 1 << 2;
    static final int SOUTH = 1 << 3;
    static final int WEST = 1 << 4;
    static final int EAST = 1 << 5;
    private static final float EPSILON = 0.0001f;

    private ExtensionBounds() { }

    static int mask(float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
        int mask = 0;
        if (minY < -EPSILON) mask |= DOWN;
        if (maxY > 1.0f + EPSILON) mask |= UP;
        if (minZ < -EPSILON) mask |= NORTH;
        if (maxZ > 1.0f + EPSILON) mask |= SOUTH;
        if (minX < -EPSILON) mask |= WEST;
        if (maxX > 1.0f + EPSILON) mask |= EAST;
        return mask;
    }
}
