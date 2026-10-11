package de.selectiverender;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
public final class BoundaryMaterial {
    private static volatile RenderMaterial solid, translucent;
    private BoundaryMaterial() { }
    public static RenderMaterial get(boolean textured) {
        boolean alpha = textured || SelectiveRenderSettings.boundaryOpacity() < 100;
        RenderMaterial value = alpha ? translucent : solid;
        if (value != null) return value;
        synchronized (BoundaryMaterial.class) {
            value = alpha ? translucent : solid;
            if (value == null) {
                value = RendererAccess.INSTANCE.getRenderer().materialFinder()
                        .blendMode(alpha ? BlendMode.TRANSLUCENT : BlendMode.SOLID)
                        .disableColorIndex(true).disableDiffuse(true).find();
                if (alpha) translucent = value; else solid = value;
            }
            return value;
        }
    }
}
