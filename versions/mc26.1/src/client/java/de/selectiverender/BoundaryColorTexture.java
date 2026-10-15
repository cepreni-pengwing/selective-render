package de.selectiverender;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;

/** Loaded block-model sprites; only the two selected sprites are cached until reload. */
public final class BoundaryColorTexture {
    private static volatile TextureAtlasSprite colorSprite, textureSprite, displayColor, displayTexture, displayCombined;
    private static final TextureAtlasSprite[] alphaSprites = new TextureAtlasSprite[101];
    private static volatile boolean dirty = true, textureHasAlpha;
    private BoundaryColorTexture() { }
    public static synchronized void invalidate() { BoundaryTextureResolver.invalidate(); colorSprite = null; textureSprite = null; displayColor = null; displayTexture = null; displayCombined = null; dirty = true; java.util.Arrays.fill(alphaSprites, null); }
    private static TextureAtlasSprite sourceSprite(boolean textured) {
        TextureAtlasSprite cached = textured ? textureSprite : colorSprite;
        if (cached != null) return cached;
        synchronized (BoundaryColorTexture.class) {
            cached = textured ? textureSprite : colorSprite;
            if (cached == null) {
                if (textured) {
                    var id = Identifier.tryParse(SelectiveRenderSettings.boundaryTexture());
                    if (id == null) id = Identifier.fromNamespaceAndPath("minecraft", "stone");
                    var block = BuiltInRegistries.BLOCK.getValue(id);
                    if (block == null) block = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("minecraft", "stone"));
                    cached = Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(block.defaultBlockState()).sprite();
                    textureSprite = cached;
                } else { cached = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(Identifier.fromNamespaceAndPath("selectiverender", "block/boundary_white")); colorSprite = cached; }
            }
            return cached;
        }
    }

    public static TextureAtlasSprite sprite(boolean textured) { return sprite(textured ? SelectiveRenderSettings.BoundaryMode.TEXTURE : SelectiveRenderSettings.BoundaryMode.COLOR); }
    /** Immutable alpha sprites serve both global and regional colors; RGB is per vertex. */
    public static TextureAtlasSprite sprite(SelectiveRenderSettings.BoundaryMode mode) {
        if (mode == SelectiveRenderSettings.BoundaryMode.COLOR) {
            int opacity=BoundaryRenderContext.opacity();
            var value=alphaSprites[opacity];
            if (value==null) { value=Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(Identifier.fromNamespaceAndPath("selectiverender", "block/boundary_alpha_" + opacity)); alphaSprites[opacity]=value; }
            return value;
        }
        if (mode == SelectiveRenderSettings.BoundaryMode.TEXTURE && BoundaryTextureResolver.bound()==null && BoundaryRenderContext.regionalTexture())
            return Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(BoundaryTextureResolver.selectedState()).sprite();
        if (mode == SelectiveRenderSettings.BoundaryMode.TEXTURE && BoundaryTextureResolver.bound() != null) return BoundaryTextureResolver.bound().sprite();
        var cached = mode == SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE ? displayCombined : BoundaryStyle.texture(mode) ? displayTexture : displayColor;
        if (cached == null) {
            String name = mode == SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE ? "boundary_combined" : BoundaryStyle.texture(mode) ? "boundary_texture" : SelectiveRenderSettings.boundaryRainbow() ? "boundary_rainbow" : "boundary_white";
            cached = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(Identifier.fromNamespaceAndPath("selectiverender", "block/" + name));
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
        var atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
        var source = sourceSprite(true).contents();
        var original = ((de.selectiverender.mixin.BoundarySpriteImagesAccess)(Object)source).selectiverender$images()[0];
        boolean previousAlpha = textureHasAlpha;
        textureHasAlpha = false;
        for (int y = 0; y < source.height(); y++) for (int x = 0; x < source.width(); x++)
            if ((original.getPixel(x, y) >>> 24) != 255) textureHasAlpha = true;

        for (var mode : new SelectiveRenderSettings.BoundaryMode[]{SelectiveRenderSettings.BoundaryMode.TEXTURE, SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE}) {
            boolean textured = BoundaryStyle.texture(mode);
            var target = sprite(mode);
            var images = ((de.selectiverender.mixin.BoundarySpriteImagesAccess)(Object)target.contents()).selectiverender$images();
            for (int level = 0; level < images.length; level++) {
                var image = images[level];
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = textured ? original.getPixel(x * source.width() / image.getWidth(), y * source.height() / image.getHeight()) : 0xFFFFFFFF;
                    int color = SelectiveRenderSettings.boundaryColor();
                    pixel = BoundaryStyle.compose(pixel, color, mode == SelectiveRenderSettings.BoundaryMode.TEXTURE ? 100 : SelectiveRenderSettings.boundaryOpacity(), mode);
                    image.setPixel(x, y, pixel);
                }
                com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().writeToTexture(atlas.getTexture(), image, level, 0, target.getX() >> level, target.getY() >> level, image.getWidth(), image.getHeight(), 0, 0);
            }
        }
        dirty = false;
        return previousAlpha != textureHasAlpha;
    }
    public static boolean translucent(boolean textured) { return translucent(textured ? SelectiveRenderSettings.BoundaryMode.TEXTURE : SelectiveRenderSettings.BoundaryMode.COLOR); }
    public static boolean translucent(SelectiveRenderSettings.BoundaryMode mode) {
        if (mode == SelectiveRenderSettings.BoundaryMode.TEXTURE && BoundaryTextureResolver.bound() != null) return BoundaryTextureResolver.bound().translucent();
        return mode == SelectiveRenderSettings.BoundaryMode.TEXTURE ? textureHasAlpha : BoundaryRenderContext.opacity() < 100;
    }
    public static float u() { var s = sprite(false); return (s.getU0() + s.getU1()) * .5f; }
    public static float v() { var s = sprite(false); return (s.getV0() + s.getV1()) * .5f; }
    public static float u(boolean textured, Direction face, float x, float y, float z) { return u(textured ? SelectiveRenderSettings.BoundaryMode.TEXTURE : SelectiveRenderSettings.BoundaryMode.COLOR, face, x, y, z); }
    public static float u(SelectiveRenderSettings.BoundaryMode mode, Direction face, float x, float y, float z) {
        boolean textured = BoundaryStyle.texture(mode);
        if (!textured) { var s=sprite(mode); return (s.getU0()+s.getU1())*.5f; }
        var resolved = BoundaryTextureResolver.bound();
        if (resolved != null) return resolved.uv().u(Math.max(0,Math.min(1,face.getAxis()==Direction.Axis.X?z:x)), Math.max(0,Math.min(1,face.getAxis()==Direction.Axis.Y?z:y)));
        var s = sprite(mode); float value = Math.max(0, Math.min(1, face.getAxis() == Direction.Axis.X ? z : x));
        return s.getU0() + (s.getU1() - s.getU0()) * value;
    }
    public static float v(boolean textured, Direction face, float x, float y, float z) { return v(textured ? SelectiveRenderSettings.BoundaryMode.TEXTURE : SelectiveRenderSettings.BoundaryMode.COLOR, face, x, y, z); }
    public static float v(SelectiveRenderSettings.BoundaryMode mode, Direction face, float x, float y, float z) {
        boolean textured = BoundaryStyle.texture(mode);
        if (!textured) { var s=sprite(mode); return (s.getV0()+s.getV1())*.5f; }
        var resolved = BoundaryTextureResolver.bound();
        if (resolved != null) return resolved.uv().v(Math.max(0,Math.min(1,face.getAxis()==Direction.Axis.X?z:x)), Math.max(0,Math.min(1,face.getAxis()==Direction.Axis.Y?z:y)));
        var s = sprite(mode); float value = Math.max(0, Math.min(1, face.getAxis() == Direction.Axis.Y ? z : 1 - y));
        return s.getV0() + (s.getV1() - s.getV0()) * value;
    }
    public static float u(SelectiveRenderSettings.BoundaryMode mode, BlockPos pos, Direction face, float x, float y, float z) {
        return u(mode, face, x, y, z);
    }
    public static float v(SelectiveRenderSettings.BoundaryMode mode, BlockPos pos, Direction face, float x, float y, float z) {
        return v(mode, face, x, y, z);
    }
    public static int argb(boolean textured) { return 0xFFFFFFFF; }
    public static int argb(SelectiveRenderSettings.BoundaryMode mode) { if (mode==SelectiveRenderSettings.BoundaryMode.COLOR) return 0xFF000000|BoundaryRenderContext.rgb(); return mode == SelectiveRenderSettings.BoundaryMode.TEXTURE && BoundaryTextureResolver.bound() != null ? BoundaryTextureResolver.bound().color() : 0xFFFFFFFF; }
    public static int argb(SelectiveRenderSettings.BoundaryMode mode, BlockPos pos, Direction face, float x, float y, float z) {
        return mode==SelectiveRenderSettings.BoundaryMode.COLOR
                ? BoundaryRenderContext.vertexColor(pos,x,z):argb(mode);
    }
    public static int abgr(SelectiveRenderSettings.BoundaryMode mode, BlockPos pos, Direction face, float x, float y, float z) {
        return BoundaryStyle.swapRedBlue(argb(mode,pos,face,x,y,z));
    }
    public static int abgr(boolean textured) { return 0xFFFFFFFF; }
    public static int abgr(SelectiveRenderSettings.BoundaryMode mode) { return BoundaryStyle.swapRedBlue(argb(mode)); }
    /** Preserve already-resolved connected/animated UVs when the chosen block is the actual block. */
    public static boolean preserveSourceTexture(net.minecraft.world.level.block.state.BlockState state) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(BoundaryRenderContext.texture());
    }
    public static boolean custom(SelectiveRenderSettings.BoundaryMode mode) {
        return BoundaryStyle.color(mode) || BoundaryStyle.texture(mode);
    }
    public static boolean translucent(BlockPos pos) {
        if (!SelectiveRenderState.filteringActive()) return false;
        var mode = SelectiveRenderState.boundaryModeAt(pos.getX(), pos.getY(), pos.getZ());
        boolean alpha = BoundaryStyle.color(mode) && BoundaryRenderContext.opacityAt(pos)<100 || BoundaryStyle.texture(mode) && textureHasAlpha;
        if (!custom(mode) || !alpha) return false;
        for (Direction face : Direction.values()) if (SelectiveRenderState.isBoundaryFace(pos, face)) return true;
        return false;
    }
}
