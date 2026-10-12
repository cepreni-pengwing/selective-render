package de.selectiverender.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.texture.NativeImage;
/** Access SR-owned slots only; never modify other mods' images. */
@Mixin(SpriteContents.class)
public interface BoundarySpriteImagesAccess {
    @Accessor("mipmapLevelsImages") NativeImage[] selectiverender$images();
}
