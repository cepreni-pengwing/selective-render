package de.selectiverender;

import net.minecraft.util.math.BlockPos;

public final class IndiumRenderContext {
    private static final ThreadLocal<BlockPos> BLOCK_POS = new ThreadLocal<>();

    private static final ThreadLocal<BoundaryQuadTracker> QUADS = ThreadLocal.withInitial(BoundaryQuadTracker::new);
    public static BoundaryQuadTracker boundaryQuads() { return QUADS.get(); }

    private IndiumRenderContext() { }

    public static void begin(BlockPos position) {
        BLOCK_POS.set(position);
        QUADS.get().reset();
    }

    public static BlockPos position() {
        return BLOCK_POS.get();
    }

    public static void end() {
        BLOCK_POS.remove();
    }
}
