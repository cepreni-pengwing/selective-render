package de.selectiverender;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;

public final class BoundaryColorTexture {
    private static final Identifier SOLID_SOURCE = new Identifier("minecraft", "block/snow");
    private static volatile float cachedU = Float.NaN;
    private static volatile float cachedV = Float.NaN;

    private BoundaryColorTexture() { }

    public static float u() {
        ensureCached();
        return cachedU;
    }

    public static float v() {
        ensureCached();
        return cachedV;
    }

    public static void invalidate() {
        cachedU = Float.NaN;
        cachedV = Float.NaN;
    }

    private static void ensureCached() {
        if (!Float.isNaN(cachedU)) return;
        synchronized (BoundaryColorTexture.class) {
            if (!Float.isNaN(cachedU)) return;
            Sprite sprite = MinecraftClient.getInstance().getBakedModelManager()
                .getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getSprite(SOLID_SOURCE);
            cachedV = (sprite.getMinV() + sprite.getMaxV()) * 0.5f;
            cachedU = (sprite.getMinU() + sprite.getMaxU()) * 0.5f;
        }
    }
}
