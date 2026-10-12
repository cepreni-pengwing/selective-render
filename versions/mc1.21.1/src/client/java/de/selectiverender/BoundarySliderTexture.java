package de.selectiverender;
/** Opaque native GUI texture: no colored-triangle batching or per-strip alpha/depth artifacts. */
final class BoundarySliderTexture implements AutoCloseable {
    private static int next;
    private final net.minecraft.client.texture.NativeImage image = new net.minecraft.client.texture.NativeImage(256, 80, false);
    private final net.minecraft.client.texture.NativeImageBackedTexture texture = new net.minecraft.client.texture.NativeImageBackedTexture(image);
    private final net.minecraft.util.Identifier id = net.minecraft.util.Identifier.of("selectiverender", "boundary_sliders_" + next++);
    private float lastHue = Float.NaN, lastSaturation, lastBrightness;
    BoundarySliderTexture() { net.minecraft.client.MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture); }
    void update(float hue, float saturation, float brightness) {
        if (hue == lastHue && saturation == lastSaturation && brightness == lastBrightness) return;
        lastHue = hue; lastSaturation = saturation; lastBrightness = brightness;
        for (int kind = 0; kind < 4; kind++) for (int x = 0; x < 256; x++) for (int y = 0; y < 20; y++) {
            int pixel = BoundaryStyle.sliderPixel(kind, x, y, hue, saturation, brightness);
            image.setColor(x, kind * 20 + y, BoundaryStyle.swapRedBlue(pixel));
        }
        texture.upload();
    }
    void draw(net.minecraft.client.gui.DrawContext draw, int kind, int x, int y, int width) {
        draw.drawTexture(id, x, y, width, 20, 0f, kind * 20f, 256, 20, 256, 80);
    }
    @Override public void close() { net.minecraft.client.MinecraftClient.getInstance().getTextureManager().destroyTexture(id); }
}
