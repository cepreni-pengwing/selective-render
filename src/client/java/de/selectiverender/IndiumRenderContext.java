package de.selectiverender;

import net.minecraft.util.math.BlockPos;

public final class IndiumRenderContext {
    private static final ThreadLocal<BlockPos> BLOCK_POS = new ThreadLocal<>();

    private IndiumRenderContext() { }

    public static void begin(BlockPos position) {
        BLOCK_POS.set(position);
    }

    public static BlockPos position() {
        return BLOCK_POS.get();
    }

    public static void end() {
        BLOCK_POS.remove();
    }
}
