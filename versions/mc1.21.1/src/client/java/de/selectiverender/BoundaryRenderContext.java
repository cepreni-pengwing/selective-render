package de.selectiverender;

/** Per-thread immutable regional style; global settings are never temporarily overwritten. */
public final class BoundaryRenderContext {
    private record Entry(RegionOptions options, BlockRegion rainbowBox) { }
    private static final ThreadLocal<Entry> CURRENT = new ThreadLocal<>();
    private BoundaryRenderContext() { }
    public static void bind(net.minecraft.util.math.BlockPos pos) {
        CURRENT.set(new Entry(SelectiveRenderConfig.optionsAt(pos.getX(),pos.getY(),pos.getZ()),
                SelectiveRenderConfig.rainbowBoxAt(pos.getX(),pos.getY(),pos.getZ())));
    }
    public static void clear() { CURRENT.remove(); }
    public static RegionOptions options() { var value=CURRENT.get(); return value==null?RegionOptions.DEFAULT:value.options(); }
    public static boolean regionalColor() { return options().inheritedColor()!=null; }
    public static boolean regionalTexture() { return options().inheritedTexture()!=null; }
    public static String texture() { var value=options().inheritedTexture(); return value==null || value.id()==null ? SelectiveRenderSettings.boundaryTexture():value.id(); }
    public static int rgb() { var value=options().inheritedColor(); return value==null || value.rgb()==null?SelectiveRenderSettings.boundaryColor():value.rgb(); }
    public static int opacity() { return opacity(options()); }
    private static int opacity(RegionOptions options) { var value=options.inheritedColor(); return value==null || value.opacity()==null?SelectiveRenderSettings.boundaryOpacity():value.opacity(); }
    public static boolean normal() { return normal(options()); }
    private static boolean normal(RegionOptions value) { return value.inheritedNormal()==null?SelectiveRenderSettings.boundaryNormal():value.inheritedNormal(); }
    public static boolean normalAt(net.minecraft.util.math.BlockPos pos) { return normal(SelectiveRenderConfig.optionsAt(pos.getX(),pos.getY(),pos.getZ())); }
    public static int opacityAt(net.minecraft.util.math.BlockPos pos) { return opacity(SelectiveRenderConfig.optionsAt(pos.getX(),pos.getY(),pos.getZ())); }
    public static boolean lighting() { var value=options().inheritedColor(); return value==null || value.lighting()==null?SelectiveRenderSettings.boundaryColorLighting():value.lighting(); }
    public static boolean rainbow() { var value=options().inheritedColor(); return value==null || value.rainbow()==null?SelectiveRenderSettings.boundaryRainbow():value.rainbow(); }
    public static int vertexColor(net.minecraft.util.math.BlockPos pos, float x, float z) {
        var entry=CURRENT.get();
        if (!rainbow() || entry==null || entry.rainbowBox()==null) return 0xFF000000|rgb();
        float[] hsv=BoundaryStyle.hsv(rgb());
        return 0xFF000000|BoundaryStyle.hsv(BoundaryRainbow.hue(entry.rainbowBox(),pos.getX()+(double)x,pos.getZ()+(double)z),hsv[1],hsv[2]);
    }
}
