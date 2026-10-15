package de.selectiverender.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.renderer.texture.SpriteContents;
import com.mojang.blaze3d.platform.NativeImage;
/** Access SR-owned slots only; never modify other mods' images. */
@Mixin(SpriteContents.class)
public interface BoundarySpriteImagesAccess {
    @Accessor("byMipLevel") NativeImage[] selectiverender$images();
}
