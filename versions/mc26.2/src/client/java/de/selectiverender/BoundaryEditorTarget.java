package de.selectiverender;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One editor implementation writes either global defaults or one region's independent layers. */
final class BoundaryEditorTarget {
    private final Supplier<RegionOptions> read;
    private final Consumer<RegionOptions> write;
    BoundaryEditorTarget() { read=null; write=null; }
    BoundaryEditorTarget(Supplier<RegionOptions> read, Consumer<RegionOptions> write) { this.read=read; this.write=write; }
    boolean regional() { return read!=null; }
    private RegionOptions options() { return regional()?read.get():RegionOptions.DEFAULT; }
    boolean normalGlobal() { return options().inheritedNormal()==null; }
    boolean normal() { return options().inheritedNormal()==null?SelectiveRenderSettings.boundaryNormal():options().inheritedNormal(); }
    void inheritNormal() { if(regional()) write.accept(options().withNormal(null)); }
    void setNormal(boolean value) { if(regional()) write.accept(options().withNormal(value)); else SelectiveRenderSettings.setBoundaryNormal(value); }
    boolean textureGlobal() { return options().inheritedTexture()==null; }
    boolean colorGlobal() { return options().inheritedColor()==null; }
    boolean textureEnabled() { var layer=options().inheritedTexture(); return layer==null?BoundaryStyle.texture(SelectiveRenderSettings.boundaryMode()):layer.enabled(); }
    boolean colorEnabled() { var layer=options().inheritedColor(); return layer==null?BoundaryStyle.color(SelectiveRenderSettings.boundaryMode()):layer.enabled(); }
    SelectiveRenderSettings.BoundaryMode mode() { return options().effectiveBoundary(SelectiveRenderSettings.boundaryMode()); }
    String texture() { var layer=options().inheritedTexture(); return layer==null || layer.id()==null?SelectiveRenderSettings.boundaryTexture():layer.id(); }
    int color() { var layer=options().inheritedColor(); return layer==null || layer.rgb()==null?SelectiveRenderSettings.boundaryColor():layer.rgb(); }
    int opacity() { var layer=options().inheritedColor(); return layer==null || layer.opacity()==null?SelectiveRenderSettings.boundaryOpacity():layer.opacity(); }
    boolean lighting() { var layer=options().inheritedColor(); return layer==null || layer.lighting()==null?SelectiveRenderSettings.boundaryColorLighting():layer.lighting(); }
    boolean rainbow() { var layer=options().inheritedColor(); return layer==null || layer.rainbow()==null?SelectiveRenderSettings.boundaryRainbow():layer.rainbow(); }
    boolean cullFluids() { var value=options().cullFluids(); return value==null?SelectiveRenderSettings.cullFluidBoundaryFaces():value; }
    void inheritTexture() { if(regional()) write.accept(options().withTexture(null)); }
    void inheritColor() { if(regional()) write.accept(options().withColor(null)); }
    void setTextureEnabled(boolean enabled) {
        if(regional()) write.accept(options().withTexture(new RegionOptions.TextureLayer(enabled,texture())));
        else SelectiveRenderSettings.setBoundaryMode(BoundaryStyle.layers(colorEnabled(),enabled));
    }
    void setColorEnabled(boolean enabled) {
        if(regional()) write.accept(options().withColor(new RegionOptions.ColorLayer(enabled,options().inheritedColor()==null?null:options().inheritedColor().rgb(),options().inheritedColor()==null?null:options().inheritedColor().opacity(),options().inheritedColor()==null?null:options().inheritedColor().lighting(),options().inheritedColor()==null?null:options().inheritedColor().rainbow())));
        else SelectiveRenderSettings.setBoundaryMode(BoundaryStyle.layers(enabled,textureEnabled()));
    }
    void inheritLighting() {
        if(regional()) { var layer=options().inheritedColor();
            if(layer!=null) write.accept(options().withColor(new RegionOptions.ColorLayer(layer.enabled(),layer.rgb(),layer.opacity(),null,layer.rainbow())));
        }
    }
    void setLighting(boolean value) {
        if(regional()) write.accept(options().withColor(new RegionOptions.ColorLayer(colorEnabled(),options().inheritedColor()==null?null:options().inheritedColor().rgb(),options().inheritedColor()==null?null:options().inheritedColor().opacity(),value,options().inheritedColor()==null?null:options().inheritedColor().rainbow())));
        else SelectiveRenderSettings.setBoundaryColorLighting(value);
    }
    void setCullFluids(boolean value) {
        if(regional()) write.accept(options().with(2,value)); else SelectiveRenderSettings.setCullFluidBoundaryFaces(value);
    }
    void commit(int rgb, String id, int opacity, boolean rainbow, boolean colorChanged, boolean textureChanged) {
        if(!regional()) { SelectiveRenderSettings.setBoundaryAppearance(rgb,id,opacity); SelectiveRenderSettings.setBoundaryRainbow(rainbow); return; }
        RegionOptions next=options();
        if(textureChanged) next=next.withTexture(new RegionOptions.TextureLayer(textureEnabled(),id));
        if(colorChanged) next=next.withColor(new RegionOptions.ColorLayer(colorEnabled(),rgb,opacity,options().inheritedColor()==null?null:options().inheritedColor().lighting(),rainbow));
        if(!next.equals(options())) write.accept(next);
    }
}
