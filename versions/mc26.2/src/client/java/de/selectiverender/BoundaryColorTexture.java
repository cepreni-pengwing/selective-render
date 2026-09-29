package de.selectiverender;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;

public final class BoundaryColorTexture {
    private static final Identifier SOLID_SOURCE = Identifier.fromNamespaceAndPath("minecraft", "block/snow");
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
            TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager()
                .getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(SOLID_SOURCE);
            cached = new Coordinates((sprite.getU0() + sprite.getU1()) * 0.5f,
                    (sprite.getV0() + sprite.getV1()) * 0.5f);
            return cached;
        }
    }
}
