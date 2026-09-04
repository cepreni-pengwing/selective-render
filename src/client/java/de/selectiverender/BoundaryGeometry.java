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
        int plane = BoundaryPlane.integralPlane(minimum, maximum);
        if (plane == BoundaryPlane.NO_PLANE) return SelectiveRenderSettings.BoundaryMode.NORMAL;
        return plane <= 0 ? boundaryModeAt(position, negative, 1 - plane)
                : boundaryModeAt(position, positive, plane);
    }

    private static SelectiveRenderSettings.BoundaryMode boundaryModeAt(
            BlockPos position, Direction direction, int step) {
        int deltaX = direction.getOffsetX();
        int deltaY = direction.getOffsetY();
        int deltaZ = direction.getOffsetZ();
        int insideStep = step - 1;
        int insideX = position.getX() + deltaX * insideStep;
        int insideY = position.getY() + deltaY * insideStep;
        int insideZ = position.getZ() + deltaZ * insideStep;
        int outsideX = insideX + deltaX;
        int outsideY = insideY + deltaY;
        int outsideZ = insideZ + deltaZ;
        return BoundaryPolicy.mode(SelectiveRenderSettings.boundaryMode(),
                SelectiveRenderState.shouldRender(insideX, insideY, insideZ),
                SelectiveRenderState.shouldRender(outsideX, outsideY, outsideZ),
                SelectiveRenderState.isActivelyHidden(outsideX, outsideY, outsideZ));
    }
}
