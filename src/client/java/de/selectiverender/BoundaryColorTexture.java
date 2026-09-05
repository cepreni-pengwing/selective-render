package de.selectiverender;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;

public final class BoundaryColorTexture {
    private static final Identifier SOLID_SOURCE = new Identifier("minecraft", "block/snow");
    private static volatile Coordinates cached;
    private record Coordinates(float u, float v) { }

    private BoundaryColorTexture() { }

    public static float u() {
        return coordinates().u();
    }

    public static float v() {
        return coordinates().v();
    }

    public static synchronized void invalidate() {
        cached = null;
    }

    private static Coordinates coordinates() {
        Coordinates value = cached;
        if (value != null) return value;
        synchronized (BoundaryColorTexture.class) {
            if (cached != null) return cached;
            Sprite sprite = MinecraftClient.getInstance().getBakedModelManager()
                .getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getSprite(SOLID_SOURCE);
            cached = new Coordinates((sprite.getMinU() + sprite.getMaxU()) * 0.5f,
                    (sprite.getMinV() + sprite.getMaxV()) * 0.5f);
            return cached;
        }
    }
}
