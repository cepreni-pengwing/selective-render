package de.selectiverender;

import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class BoundaryGeometry {
    private BoundaryGeometry() { }

    public static SelectiveRenderSettings.BoundaryMode boundaryModeForQuad(
            BlockPos position, BakedQuad quad) {
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
        return boundaryModeForQuad(position, minX, maxX, minY, maxY, minZ, maxZ);
    }

    public static SelectiveRenderSettings.BoundaryMode boundaryModeForQuad(
            BlockPos position, float minX, float maxX, float minY, float maxY,
            float minZ, float maxZ) {
        SelectiveRenderSettings.BoundaryMode mode = boundaryModeAtPlane(position,
                Direction.WEST, Direction.EAST, minX, maxX);
        if (mode != SelectiveRenderSettings.BoundaryMode.NORMAL) return mode;
        mode = boundaryModeAtPlane(position, Direction.DOWN, Direction.UP, minY, maxY);
        if (mode != SelectiveRenderSettings.BoundaryMode.NORMAL) return mode;
        return boundaryModeAtPlane(position, Direction.NORTH, Direction.SOUTH, minZ, maxZ);
    }

    private static SelectiveRenderSettings.BoundaryMode boundaryModeAtPlane(
            BlockPos position, Direction negative, Direction positive, float minimum, float maximum) {
        int negativeStep = BoundaryPlane.negativeStep(minimum, maximum);
        if (negativeStep > 0) {
            SelectiveRenderSettings.BoundaryMode mode = boundaryModeAt(
                    position, negative, negativeStep);
            if (mode != SelectiveRenderSettings.BoundaryMode.NORMAL) return mode;
        }
        int positiveStep = BoundaryPlane.positiveStep(minimum, maximum);
        return positiveStep > 0 ? boundaryModeAt(position, positive, positiveStep)
                : SelectiveRenderSettings.BoundaryMode.NORMAL;
    }

    private static SelectiveRenderSettings.BoundaryMode boundaryModeAt(
            BlockPos position, Direction direction, int step) {
        BlockPos inside = position.offset(direction, step - 1);
        BlockPos outside = position.offset(direction, step);
        if (!SelectiveRenderState.shouldRender(inside)
                || SelectiveRenderState.shouldRender(outside)
                || SelectiveRenderState.isActivelyHidden(outside)) {
            return SelectiveRenderSettings.BoundaryMode.NORMAL;
        }
        return SelectiveRenderSettings.boundaryMode();
    }
}
