package de.selectiverender;

import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class BoundaryGeometry {
    private BoundaryGeometry() { }

    public static int extensionData(BakedQuad quad) {
        int[] data = quad.getVertexData();
        int stride = data.length / 4;
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride;
            float x = Float.intBitsToFloat(data[offset]);
            float y = Float.intBitsToFloat(data[offset + 1]);
            float z = Float.intBitsToFloat(data[offset + 2]);
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }
        return extensionData(minX, maxX, minY, maxY, minZ, maxZ);
    }

    public static int extensionMask(float minX, float maxX, float minY, float maxY,
                                    float minZ, float maxZ) {
        return ExtensionBounds.mask(minX, maxX, minY, maxY, minZ, maxZ);
    }

    public static int extensionData(float minX, float maxX, float minY, float maxY,
                                    float minZ, float maxZ) {
        int data = 0;
        data = withReach(data, Direction.DOWN, negativeReach(minY));
        data = withReach(data, Direction.UP, positiveReach(maxY));
        data = withReach(data, Direction.NORTH, negativeReach(minZ));
        data = withReach(data, Direction.SOUTH, positiveReach(maxZ));
        data = withReach(data, Direction.WEST, negativeReach(minX));
        return withReach(data, Direction.EAST, positiveReach(maxX));
    }

    public static SelectiveRenderSettings.BoundaryMode extensionBoundaryMode(
            BlockPos position, int extensionData) {
        for (Direction direction : Direction.values()) {
            int reach = reach(extensionData, direction);
            for (int step = 1; step <= reach; step++) {
                BlockPos sample = position.offset(direction, step);
                if (!SelectiveRenderState.shouldRender(sample)) {
                    if (!SelectiveRenderState.isActivelyHidden(sample)) {
                        return SelectiveRenderSettings.boundaryMode();
                    }
                    break;
                }
            }
        }
        return SelectiveRenderSettings.BoundaryMode.NORMAL;
    }

    private static int positiveReach(float maximum) {
        return maximum > 1.0001f ? Math.min(31, (int) Math.ceil(maximum - 1.0f)) : 0;
    }

    private static int negativeReach(float minimum) {
        return minimum < -0.0001f ? Math.min(31, (int) Math.ceil(-minimum)) : 0;
    }

    private static int withReach(int data, Direction direction, int reach) {
        return data | reach << (direction.ordinal() * 5);
    }

    private static int reach(int data, Direction direction) {
        return data >>> (direction.ordinal() * 5) & 31;
    }

}
