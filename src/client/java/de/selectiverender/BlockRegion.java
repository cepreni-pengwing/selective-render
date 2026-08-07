package de.selectiverender;

import net.minecraft.util.math.BlockPos;

public record BlockRegion(int minX, int maxX, int minZ, int maxZ) {
    public BlockRegion {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("Region bounds are not normalized");
        }
    }

    public static BlockRegion between(BlockPos first, BlockPos second) {
        return new BlockRegion(
                Math.min(first.getX(), second.getX()), Math.max(first.getX(), second.getX()),
                Math.min(first.getZ(), second.getZ()), Math.max(first.getZ(), second.getZ()));
    }

    public boolean contains(int blockX, int blockZ) {
        return blockX >= minX && blockX <= maxX && blockZ >= minZ && blockZ <= maxZ;
    }

    public boolean contains(BlockPos position) {
        return contains(position.getX(), position.getZ());
    }

    public boolean intersectsChunk(int chunkX, int chunkZ) {
        int chunkMinX = chunkX << 4;
        int chunkMinZ = chunkZ << 4;
        return maxX >= chunkMinX && minX <= chunkMinX + 15
                && maxZ >= chunkMinZ && minZ <= chunkMinZ + 15;
    }

    public long blockCount() {
        return (long) (maxX - minX + 1) * (long) (maxZ - minZ + 1);
    }
}
