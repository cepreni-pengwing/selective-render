package de.selectiverender;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;

public final class BoundaryColorTexture {
    private static final Identifier SOLID_SOURCE = new Identifier("minecraft", "block/snow");

    private BoundaryColorTexture() { }

    public static float u() {
        Sprite sprite = sprite();
        return (sprite.getMinU() + sprite.getMaxU()) * 0.5f;
    }

    public static float v() {
        Sprite sprite = sprite();
        return (sprite.getMinV() + sprite.getMaxV()) * 0.5f;
    }

    private static Sprite sprite() {
        return MinecraftClient.getInstance().getBakedModelManager()
                .getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getSprite(SOLID_SOURCE);
    }
}
