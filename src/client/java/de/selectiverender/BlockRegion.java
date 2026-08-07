package de.selectiverender;

import net.minecraft.util.math.BlockPos;

public record BlockRegion(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    public BlockRegion {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Region bounds are not normalized");
        }
    }

    public static BlockRegion between(BlockPos first, BlockPos second) {
        return new BlockRegion(
                Math.min(first.getX(), second.getX()), Math.max(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()), Math.max(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()), Math.max(first.getZ(), second.getZ()));
    }

    public boolean contains(int blockX, int blockY, int blockZ) {
        return blockX >= minX && blockX <= maxX
                && blockY >= minY && blockY <= maxY
                && blockZ >= minZ && blockZ <= maxZ;
    }

    public boolean contains(BlockPos position) {
        return contains(position.getX(), position.getY(), position.getZ());
    }

    public boolean intersectsSection(int sectionX, int sectionY, int sectionZ) {
        long sectionMinX = (long) sectionX << 4;
        long sectionMinY = (long) sectionY << 4;
        long sectionMinZ = (long) sectionZ << 4;
        return maxX >= sectionMinX && minX <= sectionMinX + 15
                && maxY >= sectionMinY && minY <= sectionMinY + 15
                && maxZ >= sectionMinZ && minZ <= sectionMinZ + 15;
    }

    public long blockCount() {
        return (long) (maxX - minX + 1) * (long) (maxY - minY + 1) * (long) (maxZ - minZ + 1);
    }
}
