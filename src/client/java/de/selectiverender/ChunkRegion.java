package de.selectiverender;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

public record ChunkRegion(int minX, int maxX, int minZ, int maxZ) {
    public ChunkRegion {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("Region bounds are not normalized");
        }
    }

    public static ChunkRegion between(ChunkPos first, ChunkPos second) {
        return new ChunkRegion(
                Math.min(first.x, second.x), Math.max(first.x, second.x),
                Math.min(first.z, second.z), Math.max(first.z, second.z));
    }

    public boolean contains(int chunkX, int chunkZ) {
        return chunkX >= minX && chunkX <= maxX && chunkZ >= minZ && chunkZ <= maxZ;
    }

    public boolean contains(int areaMinX, int areaMaxX, int areaMinZ, int areaMaxZ) {
        return areaMinX >= minX && areaMaxX <= maxX && areaMinZ >= minZ && areaMaxZ <= maxZ;
    }

    public boolean contains(BlockPos position) {
        return contains(position.getX() >> 4, position.getZ() >> 4);
    }

    public long chunkCount() {
        return (long) (maxX - minX + 1) * (long) (maxZ - minZ + 1);
    }
}
