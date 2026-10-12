package de.selectiverender;
/** One hue cycle clockwise around each cuboid's X/Z perimeter, continuous at corners. */
public final class BoundaryRainbow {
    private BoundaryRainbow() { }
    public static float hue(BlockRegion box, double worldX, double worldZ) {
        double width = (double)box.maxX() + 1 - box.minX(), depth = (double)box.maxZ() + 1 - box.minZ();
        double x = Math.max(0, Math.min(width, worldX - box.minX()));
        double z = Math.max(0, Math.min(depth, worldZ - box.minZ()));
        double north = z, east = width - x, south = depth - z, west = x;
        double distance;
        if (north <= east && north <= south && north <= west) distance = x;
        else if (east <= south && east <= west) distance = width + z;
        else if (south <= west) distance = width + depth + width - x;
        else distance = 2 * width + depth + depth - z;
        return (float)(distance / (2 * (width + depth)) % 1);
    }
    private static final ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(Cache::new);
    private static final class Cache { long position = Long.MIN_VALUE; int generation = -1; BlockRegion box; }
    public static float hueAt(net.minecraft.core.BlockPos pos, float localX, float localZ) {
        var cache = CACHE.get(); int generation = SelectiveRenderState.visibilityGeneration();
        if (cache.position != pos.asLong() || cache.generation != generation) {
            cache.position = pos.asLong(); cache.generation = generation; cache.box = null;
            double best = Double.POSITIVE_INFINITY;
            for (var box : SelectiveRenderState.traversalRegions()) {
                if (!box.contains(pos.getX(), pos.getY(), pos.getZ())) continue;
                double distance = Math.min(Math.min(pos.getX() - box.minX(), box.maxX() - pos.getX()),
                        Math.min(pos.getZ() - box.minZ(), box.maxZ() - pos.getZ()));
                if (distance < best) { best = distance; cache.box = box; }
            }
        }
        return cache.box == null ? 0 : hue(cache.box, pos.getX() + (double)localX, pos.getZ() + (double)localZ);
    }
}
