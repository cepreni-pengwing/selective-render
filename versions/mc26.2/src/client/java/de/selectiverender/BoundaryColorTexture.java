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
    private static volatile TextureAtlasSprite colorSprite, textureSprite;
    private BoundaryColorTexture() { }
    public static synchronized void invalidate() { colorSprite = null; textureSprite = null; }
    public static TextureAtlasSprite sprite(boolean textured) {
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
    public static float u() { var s = sprite(false); return (s.getU0() + s.getU1()) * .5f; }
    public static float v() { var s = sprite(false); return (s.getV0() + s.getV1()) * .5f; }
    public static float u(boolean textured, Direction face, float x, float y, float z) {
        if (!textured) return u();
        var s = sprite(true); float value = Math.max(0, Math.min(1, face.getAxis() == Direction.Axis.X ? z : x));
        return s.getU0() + (s.getU1() - s.getU0()) * value;
    }
    public static float v(boolean textured, Direction face, float x, float y, float z) {
        if (!textured) return v();
        var s = sprite(true); float value = Math.max(0, Math.min(1, face.getAxis() == Direction.Axis.Y ? z : 1 - y));
        return s.getV0() + (s.getV1() - s.getV0()) * value;
    }
    public static int argb(boolean textured) {
        return BoundaryStyle.alpha(SelectiveRenderSettings.boundaryOpacity()) << 24
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
        if (SelectiveRenderSettings.boundaryOpacity() == 100
                && !SelectiveRenderConfig.hasBoundaryMode(SelectiveRenderSettings.BoundaryMode.TEXTURE)) return false;
        var mode = SelectiveRenderState.boundaryModeAt(pos.getX(), pos.getY(), pos.getZ());
        if (!custom(mode) || (mode == SelectiveRenderSettings.BoundaryMode.COLOR
                && SelectiveRenderSettings.boundaryOpacity() == 100)) return false;
        for (Direction face : Direction.values()) if (SelectiveRenderState.isBoundaryFace(pos, face)) return true;
        return false;
    }
}
