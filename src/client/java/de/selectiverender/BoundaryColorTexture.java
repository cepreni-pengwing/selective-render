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
    private static volatile Sprite colorSprite, textureSprite;
    private BoundaryColorTexture() { }
    public static synchronized void invalidate() { colorSprite = null; textureSprite = null; }
    public static Sprite sprite(boolean textured) {
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
