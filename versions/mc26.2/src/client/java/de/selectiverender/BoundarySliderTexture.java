package de.selectiverender;
/** Opaque native GUI texture: no colored-triangle batching or per-strip alpha/depth artifacts. */
final class BoundarySliderTexture implements AutoCloseable {
    private static int next;
    private final com.mojang.blaze3d.platform.NativeImage image = new com.mojang.blaze3d.platform.NativeImage(256, 80, false);
    private final net.minecraft.client.renderer.texture.DynamicTexture texture = new net.minecraft.client.renderer.texture.DynamicTexture(() -> "SR boundary sliders", image);
    private final net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath("selectiverender", "boundary_sliders_" + next++);
    private float lastHue = Float.NaN, lastSaturation, lastBrightness;
    BoundarySliderTexture() { net.minecraft.client.Minecraft.getInstance().getTextureManager().register(id, texture); }
    void update(float hue, float saturation, float brightness) {
        if (hue == lastHue && saturation == lastSaturation && brightness == lastBrightness) return;
        lastHue = hue; lastSaturation = saturation; lastBrightness = brightness;
        for (int kind = 0; kind < 4; kind++) for (int x = 0; x < 256; x++) for (int y = 0; y < 20; y++) {
            int pixel = BoundaryStyle.sliderPixel(kind, x, y, hue, saturation, brightness);
            image.setPixel(x, kind * 20 + y, pixel);
        }
        texture.upload();
    }
    void draw(net.minecraft.client.gui.GuiGraphicsExtractor draw, int kind, int x, int y, int width) {
        draw.blit(id, x, y, width, 20, 0, 1, kind / 4f, (kind + 1) / 4f);
    }
    @Override public void close() { net.minecraft.client.Minecraft.getInstance().getTextureManager().release(id); }
}
