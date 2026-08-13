package de.selectiverender.mixin.indium;

import net.minecraft.util.math.BlockPos;

final class IndiumRenderContext {
    private static final ThreadLocal<BlockPos> BLOCK_POS = new ThreadLocal<>();

    private IndiumRenderContext() { }

    static void begin(BlockPos position) {
        BLOCK_POS.set(position);
    }

    static BlockPos position() {
        return BLOCK_POS.get();
    }

    static void end() {
        BLOCK_POS.remove();
    }
}
