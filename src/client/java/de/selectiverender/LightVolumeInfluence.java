package de.selectiverender;

final class LightVolumeInfluence {
    private LightVolumeInfluence() { }

    static boolean blockAffectsSection(int sectionX, int sectionY, int sectionZ,
                                       int blockX, int blockY, int blockZ, int radius) {
        return inside(blockX, sectionX, radius)
                && inside(blockY, sectionY, radius)
                && inside(blockZ, sectionZ, radius);
    }

    static boolean chunkAffectsSection(int sectionX, int sectionZ,
                                       int chunkX, int chunkZ, int radius) {
        long chunkMinX = (long) chunkX << 4;
        long chunkMinZ = (long) chunkZ << 4;
        return intersects(chunkMinX, chunkMinX + 15, sectionX, radius)
                && intersects(chunkMinZ, chunkMinZ + 15, sectionZ, radius);
    }

    private static boolean inside(int block, int section, int radius) {
        long sectionMin = (long) section << 4;
        return block >= sectionMin - radius && block <= sectionMin + 15 + radius;
    }

    private static boolean intersects(long min, long max, int section, int radius) {
        long sectionMin = (long) section << 4;
        return max >= sectionMin - radius && min <= sectionMin + 15 + radius;
    }
}
