package de.selectiverender;
final class BoundarySliderTexture implements AutoCloseable {
    private static int next;
    private final net.minecraft.client.texture.NativeImage image = new net.minecraft.client.texture.NativeImage(256, 100, false);
    private final net.minecraft.client.texture.NativeImageBackedTexture texture = new net.minecraft.client.texture.NativeImageBackedTexture(image);
    private final net.minecraft.util.Identifier id = new net.minecraft.util.Identifier("selectiverender", "boundary_sliders_" + next++);
    private float lastHue = Float.NaN, lastSaturation, lastBrightness;
    private int lastOpacity = -1; private boolean lastRainbow;
    BoundarySliderTexture() { net.minecraft.client.MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture); }
    void update(float hue, float saturation, float brightness, int opacity, boolean rainbow) {
        if (hue == lastHue && saturation == lastSaturation && brightness == lastBrightness && opacity == lastOpacity && rainbow == lastRainbow) return;
        lastHue = hue; lastSaturation = saturation; lastBrightness = brightness; lastOpacity = opacity; lastRainbow = rainbow;
        for (int kind = 0; kind < 5; kind++) for (int x = 0; x < 256; x++) for (int y = 0; y < 20; y++) {
            int pixel = kind == 4 ? BoundaryStyle.previewPixel(x, y, hue, saturation, brightness, opacity, rainbow)
                    : BoundaryStyle.sliderPixel(kind, x, y, hue, saturation, brightness);
            image.setColor(x, kind * 20 + y, BoundaryStyle.swapRedBlue(pixel));
        }
        texture.upload();
    }
    void draw(net.minecraft.client.gui.DrawContext draw, int kind, int x, int y, int width) {
        if (kind == 4) { com.mojang.blaze3d.systems.RenderSystem.enableBlend(); com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc(); }
        draw.drawTexture(id, x, y, width, 20, 0f, kind * 20f, 256, 20, 256, 100);
        if (kind == 4) com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }
    @Override public void close() { net.minecraft.client.MinecraftClient.getInstance().getTextureManager().destroyTexture(id); }
}
