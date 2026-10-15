package de.selectiverender;

final class ConfigMigration {
    private ConfigMigration() {
    }

    static BlockRegion region(int minX, int maxX, Integer minY, Integer maxY,
                              int minZ, int maxZ, int formatVersion) {
        if (formatVersion >= 3 && minY != null && maxY != null) {
            return new BlockRegion(minX, maxX, minY, maxY, minZ, maxZ);
        }
        return formatVersion >= 2
                ? new BlockRegion(minX, maxX, Integer.MIN_VALUE, Integer.MAX_VALUE, minZ, maxZ)
                : legacyChunks(minX, maxX, minZ, maxZ);
    }

    static BlockRegion legacyChunks(int minX, int maxX, int minZ, int maxZ) {
        return new BlockRegion(minX << 4, (maxX << 4) + 15,
                Integer.MIN_VALUE, Integer.MAX_VALUE,
                minZ << 4, (maxZ << 4) + 15);
    }
}
