package de.selectiverender;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
public final class BoundaryMaterial {
    private static volatile RenderMaterial solid, translucent;
    private BoundaryMaterial() { }
    public static RenderMaterial get(boolean textured) { return get(textured ? SelectiveRenderSettings.BoundaryMode.TEXTURE : SelectiveRenderSettings.BoundaryMode.COLOR); }
    public static RenderMaterial get(SelectiveRenderSettings.BoundaryMode mode) {
        boolean alpha = BoundaryColorTexture.translucent(mode);
        RenderMaterial value = alpha ? translucent : solid;
        if (value != null) return value;
        synchronized (BoundaryMaterial.class) {
            value = alpha ? translucent : solid;
            if (value == null) {
                value = RendererAccess.INSTANCE.getRenderer().materialFinder()
                        .blendMode(alpha ? BlendMode.TRANSLUCENT : BlendMode.SOLID)
                        .disableColorIndex(true).disableDiffuse(true).ambientOcclusion(net.fabricmc.fabric.api.util.TriState.FALSE).emissive(true).find();
                if (alpha) translucent = value; else solid = value;
            }
            return value;
        }
    }
}
