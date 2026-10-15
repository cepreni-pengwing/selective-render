package de.selectiverender.mixin;

import de.selectiverender.SelectiveRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.LevelChunk;

@Mixin(ClientChunkCache.class)
abstract class ClientChunkManagerMixin {
    @Inject(method = "drop", at = @At("HEAD"))
    private void selectiverender$removeLightCacheChunk(net.minecraft.world.level.ChunkPos pos, CallbackInfo ci) {
        SelectiveRenderState.invalidateLightCacheChunk(pos.x(), pos.z());
    }

    @Inject(method = "replaceWithPacketData", at = @At("RETURN"))
    private void selectiverender$invalidateLoadedLightChunk(
            int chunkX, int chunkZ, FriendlyByteBuf buffer, java.util.Map<net.minecraft.world.level.levelgen.Heightmap.Types, long[]> heightmaps,
            Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer,
            CallbackInfoReturnable<LevelChunk> cir) {
        SelectiveRenderState.invalidateLightCacheChunk(chunkX, chunkZ);
    }
}
