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
    private static volatile Sprite colorSprite, textureSprite, displayColor, displayTexture, displayCombined;
    private static volatile boolean dirty = true, textureHasAlpha;
    private BoundaryColorTexture() { }
    public static synchronized void invalidate() { BoundaryTextureResolver.invalidate(); colorSprite = null; textureSprite = null; displayColor = null; displayTexture = null; displayCombined = null; dirty = true; }
    private static Sprite sourceSprite(boolean textured) {
        Sprite cached = textured ? textureSprite : colorSprite;
        if (cached != null) return cached;
        synchronized (BoundaryColorTexture.class) {
            cached = textured ? textureSprite : colorSprite;
            if (cached == null) {
                if (textured) {
                    var id = Identifier.tryParse(SelectiveRenderSettings.boundaryTexture());
                    if (id == null) id = new Identifier("minecraft", "stone");
                    var block = Registries.BLOCK.get(id);
                    if (block == null) block = Registries.BLOCK.get(new Identifier("minecraft", "stone"));
                    cached = MinecraftClient.getInstance().getBakedModelManager().getBlockModels().getModel(block.getDefaultState()).getParticleSprite();
                    textureSprite = cached;
                } else { cached = MinecraftClient.getInstance().getBakedModelManager().getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getSprite(new Identifier("selectiverender", "block/boundary_white")); colorSprite = cached; }
            }
            return cached;
        }
    }

    public static Sprite sprite(boolean textured) { return sprite(textured ? SelectiveRenderSettings.BoundaryMode.TEXTURE : SelectiveRenderSettings.BoundaryMode.COLOR); }
    public static Sprite sprite(SelectiveRenderSettings.BoundaryMode mode) {
        if (mode == SelectiveRenderSettings.BoundaryMode.TEXTURE && BoundaryTextureResolver.bound() != null) return BoundaryTextureResolver.bound().sprite();
        var cached = mode == SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE ? displayCombined : BoundaryStyle.texture(mode) ? displayTexture : displayColor;
        if (cached == null) {
            String name = mode == SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE ? "boundary_combined" : BoundaryStyle.texture(mode) ? "boundary_texture" : SelectiveRenderSettings.boundaryRainbow() ? "boundary_rainbow" : "boundary_white";
            cached = MinecraftClient.getInstance().getBakedModelManager().getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getSprite(new Identifier("selectiverender", "block/" + name));
            if (mode == SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE) displayCombined = cached;
            else if (BoundaryStyle.texture(mode)) displayTexture = cached; else displayColor = cached;
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
        float[] rainbowHsv = BoundaryStyle.hsv(SelectiveRenderSettings.boundaryColor());
        for (var mode : new SelectiveRenderSettings.BoundaryMode[]{SelectiveRenderSettings.BoundaryMode.COLOR, SelectiveRenderSettings.BoundaryMode.TEXTURE, SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE}) {
            boolean textured = BoundaryStyle.texture(mode);
            var target = sprite(mode);
            var images = ((de.selectiverender.mixin.BoundarySpriteImagesAccess)(Object)target.getContents()).selectiverender$images();
            for (int level = 0; level < images.length; level++) {
                var image = images[level];
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = textured ? original.getColor(x * source.getWidth() / image.getWidth(), y * source.getHeight() / image.getHeight()) : 0xFFFFFFFF;
                    pixel = BoundaryStyle.swapRedBlue(pixel);
                    int color = SelectiveRenderSettings.boundaryColor();
                    if (mode == SelectiveRenderSettings.BoundaryMode.COLOR && SelectiveRenderSettings.boundaryRainbow()) {
                        color = BoundaryStyle.hsv(x / (float)Math.max(1, image.getWidth() - 1), rainbowHsv[1], rainbowHsv[2]);
                    }
                    pixel = BoundaryStyle.compose(pixel, color, mode == SelectiveRenderSettings.BoundaryMode.TEXTURE ? 100 : SelectiveRenderSettings.boundaryOpacity(), mode);
                    image.setColor(x, y, BoundaryStyle.swapRedBlue(pixel));
                }
                image.upload(level, target.getX() >> level, target.getY() >> level, false);
            }
        }
        dirty = false;
        return previousAlpha != textureHasAlpha;
    }
    public static boolean translucent(boolean textured) { return translucent(textured ? SelectiveRenderSettings.BoundaryMode.TEXTURE : SelectiveRenderSettings.BoundaryMode.COLOR); }
    public static boolean translucent(SelectiveRenderSettings.BoundaryMode mode) {
        if (mode == SelectiveRenderSettings.BoundaryMode.TEXTURE && BoundaryTextureResolver.bound() != null) return BoundaryTextureResolver.bound().translucent();
        return mode == SelectiveRenderSettings.BoundaryMode.TEXTURE ? textureHasAlpha : SelectiveRenderSettings.boundaryOpacity() < 100;
    }
    public static float u() { var s = sprite(false); return (s.getMinU() + s.getMaxU()) * .5f; }
    public static float v() { var s = sprite(false); return (s.getMinV() + s.getMaxV()) * .5f; }
    public static float u(boolean textured, Direction face, float x, float y, float z) { return u(textured ? SelectiveRenderSettings.BoundaryMode.TEXTURE : SelectiveRenderSettings.BoundaryMode.COLOR, face, x, y, z); }
    public static float u(SelectiveRenderSettings.BoundaryMode mode, Direction face, float x, float y, float z) {
        boolean textured = BoundaryStyle.texture(mode);
        if (!textured) return u();
        var resolved = BoundaryTextureResolver.bound();
        if (resolved != null) { float f = Math.max(0,Math.min(1,face.getAxis() == Direction.Axis.X ? z : x)); return resolved.u0() + (resolved.u1() - resolved.u0()) * f; }
        var s = sprite(mode); float value = Math.max(0, Math.min(1, face.getAxis() == Direction.Axis.X ? z : x));
        return s.getMinU() + (s.getMaxU() - s.getMinU()) * value;
    }
    public static float v(boolean textured, Direction face, float x, float y, float z) { return v(textured ? SelectiveRenderSettings.BoundaryMode.TEXTURE : SelectiveRenderSettings.BoundaryMode.COLOR, face, x, y, z); }
    public static float v(SelectiveRenderSettings.BoundaryMode mode, Direction face, float x, float y, float z) {
        boolean textured = BoundaryStyle.texture(mode);
        if (!textured) return v();
        var resolved = BoundaryTextureResolver.bound();
        if (resolved != null) { float f = Math.max(0,Math.min(1,face.getAxis() == Direction.Axis.Y ? z : 1-y)); return resolved.v0() + (resolved.v1() - resolved.v0()) * f; }
        var s = sprite(mode); float value = Math.max(0, Math.min(1, face.getAxis() == Direction.Axis.Y ? z : 1 - y));
        return s.getMinV() + (s.getMaxV() - s.getMinV()) * value;
    }
    public static float u(SelectiveRenderSettings.BoundaryMode mode, BlockPos pos, Direction face, float x, float y, float z) {
        if (mode == SelectiveRenderSettings.BoundaryMode.COLOR && SelectiveRenderSettings.boundaryRainbow() && pos != null) {
            var sprite = sprite(mode); float h = BoundaryRainbow.hueAt(pos, x, z);
            if (face == Direction.WEST && h == 0) h = 1;
            return sprite.getMinU() + (sprite.getMaxU() - sprite.getMinU()) * h;
        }
        return u(mode, face, x, y, z);
    }
    public static float v(SelectiveRenderSettings.BoundaryMode mode, BlockPos pos, Direction face, float x, float y, float z) {
        return v(mode, face, x, y, z);
    }
    public static int argb(boolean textured) { return 0xFFFFFFFF; }
    public static int argb(SelectiveRenderSettings.BoundaryMode mode) { return mode == SelectiveRenderSettings.BoundaryMode.TEXTURE && BoundaryTextureResolver.bound() != null ? BoundaryTextureResolver.bound().color() : 0xFFFFFFFF; }
    public static int abgr(boolean textured) { return 0xFFFFFFFF; }
    public static int abgr(SelectiveRenderSettings.BoundaryMode mode) { return BoundaryStyle.swapRedBlue(argb(mode)); }
    /** Preserve already-resolved connected/animated UVs when the chosen block is the actual block. */
    public static boolean preserveSourceTexture(net.minecraft.block.BlockState state) {
        return net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString().equals(SelectiveRenderSettings.boundaryTexture());
    }
    public static boolean custom(SelectiveRenderSettings.BoundaryMode mode) {
        return BoundaryStyle.color(mode) || BoundaryStyle.texture(mode);
    }
    public static boolean translucent(BlockPos pos) {
        if (!SelectiveRenderState.filteringActive()) return false;
        if (SelectiveRenderSettings.boundaryOpacity() == 100 && !textureHasAlpha) return false;
        var mode = SelectiveRenderState.boundaryModeAt(pos.getX(), pos.getY(), pos.getZ());
        if (!custom(mode) || !translucent(mode)) return false;
        for (Direction face : Direction.values()) if (SelectiveRenderState.isBoundaryFace(pos, face)) return true;
        return false;
    }
}
