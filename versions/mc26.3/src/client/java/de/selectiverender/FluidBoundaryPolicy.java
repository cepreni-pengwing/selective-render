package de.selectiverender;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;

/** Isolated opt-in policy for culling fluid surfaces at render-region boundaries. */
public final class FluidBoundaryPolicy {
    private FluidBoundaryPolicy() { }

    public static boolean shouldCull(BlockGetter world, BlockPos position,
                                     FluidState fluidState, Direction face) {
        if (!SelectiveRenderState.cullFluidsAt(position.getX(), position.getY(), position.getZ())
                || !SelectiveRenderState.filteringActive()
                || fluidState.isEmpty()) return false;
        if (face == Direction.UP && fluidState.getHeight(world, position) < 0.9999f) return false;

        int x = position.getX();
        int y = position.getY();
        int z = position.getZ();
        int neighborX = x + face.getStepX();
        int neighborY = y + face.getStepY();
        int neighborZ = z + face.getStepZ();
        return SelectiveRenderState.shouldRender(x, y, z)
                && !SelectiveRenderState.shouldRender(neighborX, neighborY, neighborZ)
                && !SelectiveRenderState.isActivelyHidden(neighborX, neighborY, neighborZ);
    }
}
