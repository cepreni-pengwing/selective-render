package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import net.minecraft.client.world.ClientChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientChunkManager.class)
abstract class ClientChunkManagerMixin {
    @Inject(method = "unload", at = @At("HEAD"))
    private void selectiverender$removeLightCacheChunk(int chunkX, int chunkZ, CallbackInfo ci) {
        SelectiveRenderState.removeVisibleOccluderChunk(chunkX, chunkZ);
    }
}
