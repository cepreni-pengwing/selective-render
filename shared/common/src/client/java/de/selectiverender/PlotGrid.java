package de.selectiverender;

/** Inclusive block corners and a uniform road width; independent of Minecraft mappings. */
public record PlotGrid(int x1, int z1, int x2, int z2, Integer roadWidth, boolean enabled) {
    private static final int LIMIT = 30_000_000;
    public PlotGrid {
        if (Math.abs((long)x1) > LIMIT || Math.abs((long)x2) > LIMIT
                || Math.abs((long)z1) > LIMIT || Math.abs((long)z2) > LIMIT)
            throw new IllegalArgumentException("Plot corners exceed world limits.");
        if (roadWidth != null && (roadWidth < 0 || roadWidth > LIMIT))
            throw new IllegalArgumentException("Road width must be between 0 and 30000000.");
    }
    public int minX() { return Math.min(x1, x2); }
    public int maxX() { return Math.max(x1, x2); }
    public int minZ() { return Math.min(z1, z2); }
    public int maxZ() { return Math.max(z1, z2); }
    public long width() { return (long)maxX() - minX() + 1; }
    public long depth() { return (long)maxZ() - minZ() + 1; }
    /** Equivalent grid phase, represented by the smallest nonnegative plot origin. */
    public PlotGrid normalized() {
        if(roadWidth==null) return this;
        long x=Math.floorMod((long)minX(),width()+roadWidth), z=Math.floorMod((long)minZ(),depth()+roadWidth);
        if(x+width()-1>LIMIT || z+depth()-1>LIMIT) return this;
        return new PlotGrid((int)x,(int)z,(int)(x+width()-1),(int)(z+depth()-1),roadWidth,enabled);
    }
    public PlotGrid withRoad(int width) { return new PlotGrid(x1,z1,x2,z2,width,enabled).normalized(); }
    public PlotGrid withEnabled(boolean value) { return new PlotGrid(x1,z1,x2,z2,roadWidth,value); }
    public PlotGrid identifyNeighbor(PlotGrid next) {
        if (width() != next.width() || depth() != next.depth())
            throw new IllegalArgumentException("Select a neighboring plot of the same size.");
        long gap;
        if (minZ() == next.minZ() && maxZ() == next.maxZ())
            gap = Math.max(minX(),next.minX()) - (long)Math.min(maxX(),next.maxX()) - 1;
        else if (minX() == next.minX() && maxX() == next.maxX())
            gap = Math.max(minZ(),next.minZ()) - (long)Math.min(maxZ(),next.maxZ()) - 1;
        else throw new IllegalArgumentException("Select a directly neighboring plot along X or Z.");
        if (gap < 0 || gap > LIMIT) throw new IllegalArgumentException("Select a different neighboring plot.");
        return withRoad((int)gap);
    }
    /** Null means road; floor division also handles plots west/north of the calibration. */
    public int[] plotAt(int x, int z) {
        if (roadWidth == null) return null;
        long periodX = width() + roadWidth, periodZ = depth() + roadWidth;
        long dx = (long)x - minX(), dz = (long)z - minZ();
        if (Math.floorMod(dx, periodX) >= width() || Math.floorMod(dz, periodZ) >= depth()) return null;
        long startX = minX() + Math.floorDiv(dx,periodX)*periodX;
        long startZ = minZ() + Math.floorDiv(dz,periodZ)*periodZ;
        if (startX < -LIMIT || startZ < -LIMIT || startX+width()-1 > LIMIT || startZ+depth()-1 > LIMIT) return null;
        return new int[]{(int)startX,(int)(startX+width()-1),(int)startZ,(int)(startZ+depth()-1)};
    }
}
