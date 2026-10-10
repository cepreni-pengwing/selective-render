package de.selectiverender;

import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;

/** Isolated opt-in policy for culling water surfaces at render-region boundaries. */
public final class FluidBoundaryPolicy {
    private FluidBoundaryPolicy() { }

    public static boolean shouldCull(BlockView world, BlockPos position,
                                     FluidState fluidState, Direction face) {
        if (!SelectiveRenderSettings.cullWaterBoundaryFaces()
                || !SelectiveRenderState.filteringActive()
                || SelectiveRenderSettings.boundaryMode() == SelectiveRenderSettings.BoundaryMode.NORMAL
                || !fluidState.isIn(FluidTags.WATER)) return false;
        if (face == Direction.UP && fluidState.getHeight(world, position) < 0.9999f) return false;

        int x = position.getX();
        int y = position.getY();
        int z = position.getZ();
        int neighborX = x + face.getOffsetX();
        int neighborY = y + face.getOffsetY();
        int neighborZ = z + face.getOffsetZ();
        return SelectiveRenderState.shouldRender(x, y, z)
                && !SelectiveRenderState.shouldRender(neighborX, neighborY, neighborZ)
                && !SelectiveRenderState.isActivelyHidden(neighborX, neighborY, neighborZ);
    }
}
