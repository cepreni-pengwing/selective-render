package de.selectiverender;
/** Opaque native GUI texture: no colored-triangle batching or per-strip alpha/depth artifacts. */
final class BoundarySliderTexture implements AutoCloseable {
    private static int next;
    private final com.mojang.blaze3d.platform.NativeImage image = new com.mojang.blaze3d.platform.NativeImage(256, 100, false);
    private final net.minecraft.client.renderer.texture.DynamicTexture texture = new net.minecraft.client.renderer.texture.DynamicTexture(() -> "SR boundary sliders", image);
    private final net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath("selectiverender", "boundary_sliders_" + next++);
    private float lastHue = Float.NaN, lastSaturation, lastBrightness;
    private int lastOpacity = -1; private boolean lastRainbow;
    BoundarySliderTexture() { net.minecraft.client.Minecraft.getInstance().getTextureManager().register(id, texture); }
    void update(float hue, float saturation, float brightness, int opacity, boolean rainbow) {
        if (hue == lastHue && saturation == lastSaturation && brightness == lastBrightness && opacity == lastOpacity && rainbow == lastRainbow) return;
        lastHue = hue; lastSaturation = saturation; lastBrightness = brightness; lastOpacity = opacity; lastRainbow = rainbow;
        for (int kind = 0; kind < 5; kind++) for (int x = 0; x < 256; x++) for (int y = 0; y < 20; y++) {
            int pixel = kind == 4 ? BoundaryStyle.previewPixel(x, y, hue, saturation, brightness, opacity, rainbow)
                    : BoundaryStyle.sliderPixel(kind, x, y, hue, saturation, brightness);
            image.setPixel(x, kind * 20 + y, pixel);
        }
        texture.upload();
    }
    void draw(net.minecraft.client.gui.GuiGraphicsExtractor draw, int kind, int x, int y, int width) {
        // This overload takes x1, y1, x2, y2, rather than width/height.
        draw.blit(id, x, y, x + width, y + 20, 0, 1, kind / 5f, (kind + 1) / 5f);
    }
    @Override public void close() { net.minecraft.client.Minecraft.getInstance().getTextureManager().release(id); }
}
