package de.selectiverender;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
public final class BoundaryMaterial {
    private static final RenderMaterial[] materials = new RenderMaterial[4];
    private BoundaryMaterial() { }
    public static RenderMaterial get(boolean textured) { return get(textured ? SelectiveRenderSettings.BoundaryMode.TEXTURE : SelectiveRenderSettings.BoundaryMode.COLOR); }
    public static RenderMaterial get(SelectiveRenderSettings.BoundaryMode mode) {
        boolean alpha = BoundaryColorTexture.translucent(mode);
        boolean fullbright = BoundaryStyle.fullbright(mode);
        int index = (alpha ? 1 : 0) + (fullbright ? 2 : 0);
        RenderMaterial value = materials[index];
        if (value != null) return value;
        synchronized (BoundaryMaterial.class) {
            value = materials[index];
            if (value == null) {
                value = RendererAccess.INSTANCE.getRenderer().materialFinder()
                        .blendMode(alpha ? BlendMode.TRANSLUCENT : BlendMode.SOLID)
                        .disableColorIndex(true).disableDiffuse(fullbright).ambientOcclusion(fullbright ? net.fabricmc.fabric.api.util.TriState.FALSE : net.fabricmc.fabric.api.util.TriState.DEFAULT).emissive(fullbright).find();
                materials[index] = value;
            }
            return value;
        }
    }
}
