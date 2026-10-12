package de.selectiverender;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;

/** Loaded block-model sprites; only the two selected sprites are cached until reload. */
public final class BoundaryColorTexture {
    private static volatile Sprite colorSprite, textureSprite, displayColor, displayTexture;
    private static volatile boolean dirty = true, textureHasAlpha;
    private BoundaryColorTexture() { }
    public static synchronized void invalidate() { colorSprite = null; textureSprite = null; displayColor = null; displayTexture = null; dirty = true; }
    private static Sprite sourceSprite(boolean textured) {
        Sprite cached = textured ? textureSprite : colorSprite;
        if (cached != null) return cached;
        synchronized (BoundaryColorTexture.class) {
            cached = textured ? textureSprite : colorSprite;
            if (cached == null) {
                if (textured) {
                    var id = Identifier.tryParse(SelectiveRenderSettings.boundaryTexture());
                    if (id == null) id = Identifier.of("minecraft", "stone");
                    var block = Registries.BLOCK.get(id);
                    if (block == null) block = Registries.BLOCK.get(Identifier.of("minecraft", "stone"));
                    cached = MinecraftClient.getInstance().getBakedModelManager().getBlockModels().getModel(block.getDefaultState()).getParticleSprite();
                    textureSprite = cached;
                } else { cached = MinecraftClient.getInstance().getBakedModelManager().getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getSprite(Identifier.of("selectiverender", "block/boundary_white")); colorSprite = cached; }
            }
            return cached;
        }
    }

    public static Sprite sprite(boolean textured) {
        var cached = textured ? displayTexture : displayColor;
        if (cached == null) {
            cached = MinecraftClient.getInstance().getBakedModelManager().getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getSprite(textured ? Identifier.of("selectiverender", "block/boundary_texture") : Identifier.of("selectiverender", "block/boundary_white"));
            if (textured) displayTexture = cached; else displayColor = cached;
        }
        return cached;
    }
    /** Run on the render thread. Opacity lives in texture alpha, never Sodium's AO channel. */
    public static boolean prepare() {
        if (!dirty || !SelectiveRenderState.filteringActive()
                || !(SelectiveRenderConfig.hasBoundaryMode(SelectiveRenderSettings.BoundaryMode.COLOR)
                || SelectiveRenderConfig.hasBoundaryMode(SelectiveRenderSettings.BoundaryMode.TEXTURE))) return false;
        var atlas = MinecraftClient.getInstance().getBakedModelManager().getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        var source = sourceSprite(true).getContents();
        var original = ((de.selectiverender.mixin.BoundarySpriteImagesAccess)(Object)source).selectiverender$images()[0];
        boolean previousAlpha = textureHasAlpha;
        textureHasAlpha = false;
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++)
            if ((original.getColor(x, y) >>> 24) != 255) textureHasAlpha = true;
        atlas.bindTexture();
        for (boolean textured : new boolean[]{false, true}) {
            var target = sprite(textured);
            var images = ((de.selectiverender.mixin.BoundarySpriteImagesAccess)(Object)target.getContents()).selectiverender$images();
            for (int level = 0; level < images.length; level++) {
                var image = images[level];
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = textured ? original.getColor(x * source.getWidth() / image.getWidth(), y * source.getHeight() / image.getHeight()) : 0xFFFFFFFF;
                    int alpha = ((pixel >>> 24) * SelectiveRenderSettings.boundaryOpacity() + 50) / 100;
                    image.setColor(x, y, pixel & 0xFFFFFF | alpha << 24);
                }
                image.upload(level, target.getX() >> level, target.getY() >> level, false);
            }
        }
        dirty = false;
        return previousAlpha != textureHasAlpha;
    }
    public static boolean translucent(boolean textured) {
        return SelectiveRenderSettings.boundaryOpacity() < 100 || (textured && textureHasAlpha);
    }
    public static float u() { var s = sprite(false); return (s.getMinU() + s.getMaxU()) * .5f; }
    public static float v() { var s = sprite(false); return (s.getMinV() + s.getMaxV()) * .5f; }
    public static float u(boolean textured, Direction face, float x, float y, float z) {
        if (!textured) return u();
        var s = sprite(true); float value = Math.max(0, Math.min(1, face.getAxis() == Direction.Axis.X ? z : x));
        return s.getMinU() + (s.getMaxU() - s.getMinU()) * value;
    }
    public static float v(boolean textured, Direction face, float x, float y, float z) {
        if (!textured) return v();
        var s = sprite(true); float value = Math.max(0, Math.min(1, face.getAxis() == Direction.Axis.Y ? z : 1 - y));
        return s.getMinV() + (s.getMaxV() - s.getMinV()) * value;
    }
    public static int argb(boolean textured) {
        return 0xFF000000
                | (textured ? 0xFFFFFF : SelectiveRenderSettings.boundaryColor());
    }
    public static int abgr(boolean textured) {
        int value = argb(textured);
        return value & 0xFF00FF00 | (value >> 16 & 255) | (value & 255) << 16;
    }
    public static boolean custom(SelectiveRenderSettings.BoundaryMode mode) {
        return mode == SelectiveRenderSettings.BoundaryMode.COLOR || mode == SelectiveRenderSettings.BoundaryMode.TEXTURE;
    }
    public static boolean translucent(BlockPos pos) {
        if (!SelectiveRenderState.filteringActive()) return false;
        if (SelectiveRenderSettings.boundaryOpacity() == 100 && !textureHasAlpha) return false;
        var mode = SelectiveRenderState.boundaryModeAt(pos.getX(), pos.getY(), pos.getZ());
        if (!custom(mode) || !translucent(mode == SelectiveRenderSettings.BoundaryMode.TEXTURE)) return false;
        for (Direction face : Direction.values()) if (SelectiveRenderState.isBoundaryFace(pos, face)) return true;
        return false;
    }
}
